/**
 * Shared update logic extracted from [UpdateCommand] so both the CLI and TUI
 * can check for updates, download, and install without duplicating code.
 *
 * Network access uses [java.net.URI]; checksum verification uses [java.security.MessageDigest].
 * Filesystem operations use [kotlinx.io.files.SystemFileSystem] for directory creation and
 * [java.io.File] for temp files and process execution.
 */
package zone.clanker.opsx.cli.update

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.security.MessageDigest

/**
 * Result of checking whether an update is available.
 *
 * Sealed so callers can exhaustively match on the three states:
 * [Available], [UpToDate], and [Failed].
 */
sealed interface UpdateCheck {
    /** A newer version exists and can be downloaded. */
    data class Available(
        val current: String,
        val latest: String,
        val release: ReleaseInfo,
    ) : UpdateCheck

    /** The installed version matches the latest release. */
    data class UpToDate(
        val version: String,
    ) : UpdateCheck

    /** The update check itself failed (network error, parse error, etc.). */
    data class Failed(
        val error: String,
    ) : UpdateCheck
}

/**
 * Result of downloading and installing an update.
 *
 * Sealed so callers can distinguish [Success] from [Failed].
 */
sealed interface UpdateResult {
    /** The binary was replaced successfully. */
    data class Success(
        val version: String,
    ) : UpdateResult

    /** The download or installation failed. */
    data class Failed(
        val error: String,
    ) : UpdateResult
}

/**
 * Discrete progress steps emitted during the update process.
 *
 * The TUI renders these as a vertical step list; the CLI can ignore them
 * and just use the final [Done] or [Error].
 */
sealed interface UpdateStep {
    /** Fetching the latest release metadata from GitHub. */
    data class Checking(
        val repo: String,
    ) : UpdateStep

    /** Downloading the platform-specific archive. */
    data class Downloading(
        val assetName: String,
    ) : UpdateStep

    /** Verifying the SHA-256 checksum against published sums. */
    data object VerifyingChecksum : UpdateStep

    /** Extracting the archive into the opsx home directory. */
    data object Extracting : UpdateStep

    /** Update completed successfully. */
    data class Done(
        val version: String,
    ) : UpdateStep

    /** Update failed at some step. */
    data class Error(
        val message: String,
    ) : UpdateStep
}

/**
 * Metadata for a single GitHub release, decoded from the releases API.
 *
 * @property tagName the git tag (e.g. `v0.1.0`)
 * @property assets the downloadable files attached to the release
 */
@Serializable
data class ReleaseInfo(
    @SerialName("tag_name") val tagName: String,
    @SerialName("assets") val assets: List<Asset> = emptyList(),
)

/**
 * A single downloadable asset attached to a GitHub release.
 *
 * @property name the filename (e.g. `opsx-macos-arm64.tar.gz`)
 * @property downloadUrl the browser-accessible download URL
 */
@Serializable
data class Asset(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
)

/**
 * Shared update logic used by both the CLI [UpdateCommand] and the TUI `UpdateView`.
 *
 * All functions are pure or side-effect-contained: network I/O is isolated in
 * [checkLatest] and [downloadAndInstall], and filesystem writes go through
 * temp files with cleanup.
 */
object UpdateRunner {
    /** Buffer size for streaming file reads (checksum calculation). */
    private const val BUFFER_SIZE = 8192

    /** JSON decoder configured to ignore unknown keys from the GitHub API. */
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Return the default GitHub repository for update checks.
     *
     * Reads `OPSX_REPO` from the environment, falling back to `ClankerGuru/opsx`.
     */
    internal fun defaultRepo(): String = System.getenv("OPSX_REPO") ?: "ClankerGuru/opsx"

    /**
     * Detect the current platform as a `{os}-{arch}` string, or `null` if unsupported.
     *
     * Supported combinations: `macos-arm64`, `macos-x64`, `linux-arm64`, `linux-x64`.
     */
    internal fun detectPlatform(): String? {
        val osProperty = System.getProperty("os.name").lowercase()
        val archProperty = System.getProperty("os.arch").lowercase()
        val osName =
            when {
                "mac" in osProperty -> "macos"
                "linux" in osProperty -> "linux"
                else -> return null
            }
        val archName =
            when {
                "aarch64" in archProperty || "arm64" in archProperty -> "arm64"
                "amd64" in archProperty || "x86_64" in archProperty -> "x64"
                else -> return null
            }
        return "$osName-$archName"
    }

    /**
     * Check GitHub for the latest release and compare against [currentVersion].
     *
     * @param currentVersion the version string of the running binary
     * @param repo the GitHub `owner/repo` slug (defaults to [defaultRepo])
     * @return [UpdateCheck.Available], [UpdateCheck.UpToDate], or [UpdateCheck.Failed]
     */
    fun checkLatest(
        currentVersion: String,
        repo: String = defaultRepo(),
    ): UpdateCheck =
        runCatching {
            val url = "https://api.github.com/repos/$repo/releases/latest"
            val body = URI(url).toURL().readText()
            json.decodeFromString(ReleaseInfo.serializer(), body)
        }.fold(
            onSuccess = { release ->
                val latestVersion = release.tagName.removePrefix("v")
                if (currentVersion == latestVersion) {
                    UpdateCheck.UpToDate(currentVersion)
                } else {
                    UpdateCheck.Available(
                        current = currentVersion,
                        latest = latestVersion,
                        release = release,
                    )
                }
            },
            onFailure = { throwable ->
                UpdateCheck.Failed(throwable.message ?: "unknown error")
            },
        )

    /**
     * Download and install the platform binary from the given [release].
     *
     * Downloads the platform-specific `.tar.gz`, verifies its SHA-256 checksum
     * (if a `SHA256SUMS` asset is present), and extracts into `$OPSX_HOME`.
     *
     * @param release the release metadata containing asset URLs
     * @param onStep optional callback invoked for each [UpdateStep] (for TUI progress)
     * @return [UpdateResult.Success] or [UpdateResult.Failed]
     */
    @Suppress("ReturnCount")
    fun downloadAndInstall(
        release: ReleaseInfo,
        onStep: (UpdateStep) -> Unit = {},
    ): UpdateResult {
        val opsxHome =
            Path(
                System.getenv("OPSX_HOME")
                    ?: "${System.getProperty("user.home")}/.opsx",
            )
        val platform =
            detectPlatform()
                ?: return UpdateResult.Failed("unsupported platform")

        val assetName = "opsx-$platform.tar.gz"
        val assetUrl =
            findAssetUrl(release, assetName)
                ?: return UpdateResult.Failed("release asset not found: $assetName")

        val latestVersion = release.tagName.removePrefix("v")

        return runCatching {
            val tempFile = java.io.File.createTempFile("opsx-update-", ".tar.gz")
            runCatching {
                onStep(UpdateStep.Downloading(assetName))
                URI(assetUrl).toURL().openStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val checksumUrl = findAssetUrl(release, "SHA256SUMS")
                if (checksumUrl != null) {
                    onStep(UpdateStep.VerifyingChecksum)
                    verifyChecksum(tempFile, assetName, checksumUrl)
                }

                onStep(UpdateStep.Extracting)
                SystemFileSystem.createDirectories(opsxHome)
                val tempPath = Path(tempFile.absolutePath)
                val exitCode =
                    ProcessBuilder(
                        "tar",
                        "xzf",
                        tempPath.toString(),
                        "-C",
                        opsxHome.toString(),
                    ).redirectErrorStream(true)
                        .start()
                        .waitFor()

                if (exitCode != 0) {
                    error("extraction failed (exit $exitCode)")
                }

                onStep(UpdateStep.Done(latestVersion))
                UpdateResult.Success(latestVersion)
            }.also { tempFile.delete() }.getOrThrow()
        }.getOrElse { throwable ->
            onStep(UpdateStep.Error(throwable.message ?: "unknown error"))
            UpdateResult.Failed(throwable.message ?: "unknown error")
        }
    }

    /**
     * Find the download URL for an asset by [name] within the [release].
     *
     * @return the URL string, or `null` if no matching asset exists
     */
    internal fun findAssetUrl(
        release: ReleaseInfo,
        name: String,
    ): String? = release.assets.firstOrNull { it.name == name }?.downloadUrl

    /**
     * Verify the SHA-256 checksum of [archiveFile] against the published sums.
     *
     * Parses a checksum file where each line has the format `<hex>  <filename>`,
     * extracts the expected hash for [assetName], and compares it to the computed
     * SHA-256 digest of [archiveFile]. Throws on mismatch.
     *
     * @param archiveFile the local file to checksum
     * @param assetName the filename to look up in the checksum list
     * @param checksumUrl URL or inline content of the checksum file
     * @throws IllegalStateException if the checksum does not match
     */
    internal fun verifyChecksum(
        archiveFile: java.io.File,
        assetName: String,
        checksumUrl: String,
    ) {
        val sums = URI(checksumUrl).toURL().readText()
        val expected =
            sums
                .lines()
                .firstOrNull { it.endsWith(assetName) }
                ?.substringBefore(" ")
                ?.trim()
                ?: return

        val digest = MessageDigest.getInstance("SHA-256")
        archiveFile.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead = input.read(buffer)
            while (bytesRead > 0) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }

        check(actual == expected) {
            "checksum mismatch -- expected $expected, got $actual"
        }
    }
}
