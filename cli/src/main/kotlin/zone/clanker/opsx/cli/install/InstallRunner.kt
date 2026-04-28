/**
 * Executes the install operation -- copies the opsx distribution to `~/.opsx`
 * (or `$OPSX_HOME`), configures PATH in shell rc files, and installs zsh
 * completions.
 *
 * Each step is reported as an [InstallEntry] so callers (CLI and TUI)
 * can render progress as items are processed.
 */
package zone.clanker.opsx.cli.install

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

/**
 * Result of one install operation (file copy, PATH config, completions).
 *
 * @property path display path of the item (tilde-relative for readability)
 * @property description human-readable summary of what happened
 * @property success whether the operation completed without error
 * @property error error message if [success] is false
 */
data class InstallEntry(
    val path: String,
    val description: String,
    val success: Boolean,
    val error: String? = null,
)

/**
 * Executes the install operation -- copies bin/ and lib/ from the source
 * distribution to the opsx home directory, configures PATH, and installs
 * shell completions.
 */
object InstallRunner {
    /** Sentinel line marking the start of the PATH block in shell rc files. */
    private const val SENTINEL = "# >>> opsx >>>"

    /**
     * Install the distribution to `~/.opsx` (or `$OPSX_HOME`).
     *
     * Returns one [InstallEntry] per step so callers can render progress.
     * Uses the real user home and environment for path resolution.
     */
    fun install(sourceDir: Path): List<InstallEntry> {
        val home = System.getProperty("user.home")
        val opsxHome = Path(System.getenv("OPSX_HOME") ?: "$home/.opsx")
        return install(sourceDir = sourceDir, homeDir = home, opsxHome = opsxHome)
    }

    /**
     * Testable overload that accepts explicit paths instead of reading
     * system properties and environment variables.
     */
    internal fun install(
        sourceDir: Path,
        homeDir: String,
        opsxHome: Path,
    ): List<InstallEntry> =
        buildList {
            addAll(copyBinFiles(sourceDir, opsxHome, homeDir))
            addAll(copyLibFiles(sourceDir, opsxHome, homeDir))
            add(ensurePath(homeDir, opsxHome))
            add(installCompletions(homeDir))
        }

    /**
     * Resolve the source distribution directory from the running JAR's location.
     *
     * The distribution layout is `<root>/lib/cli.jar`, so the root is
     * two levels up from the JAR. Returns `null` if the layout is not found.
     */
    fun resolveSourceDir(): Path? =
        runCatching {
            val jarUri =
                InstallRunner::class.java.protectionDomain
                    ?.codeSource
                    ?.location
                    ?.toURI()
                    ?: return null
            val jarFile = Path(jarUri.path)
            val distRoot = jarFile.parent?.parent ?: return null
            val hasBin = SystemFileSystem.exists(Path(distRoot, "bin"))
            val hasLib = SystemFileSystem.exists(Path(distRoot, "lib"))
            if (hasBin && hasLib) distRoot else null
        }.getOrNull()

    /**
     * Wire PATH in the first existing shell rc file if not already present.
     *
     * Checks for the sentinel `# >>> opsx >>>` in `.zshrc`, `.bashrc`, and
     * `.profile` (in that order). If found, reports "already configured".
     * If not, appends the PATH block to the first existing rc file.
     */
    internal fun ensurePath(
        homeDir: String,
        opsxHome: Path,
    ): InstallEntry {
        val rcFile =
            listOf(".zshrc", ".bashrc", ".profile")
                .map { Path(homeDir, it) }
                .firstOrNull { SystemFileSystem.exists(it) }
                ?: return InstallEntry(
                    path = "~/.zshrc",
                    description = "no rc file found",
                    success = false,
                    error = "no shell rc file found",
                )

        val displayPath = "~/${rcFile.name}"
        return runCatching {
            val content = SystemFileSystem.source(rcFile).buffered().use { it.readString() }
            if (content.contains(SENTINEL)) {
                return InstallEntry(
                    path = displayPath,
                    description = "already configured",
                    success = true,
                )
            }
            java.io.File(rcFile.toString()).appendText(
                "\n$SENTINEL\n" +
                    "export OPSX_HOME=\"$opsxHome\"\n" +
                    "export PATH=\"\$OPSX_HOME/bin:\$PATH\"\n" +
                    "# <<< opsx <<<\n",
            )
        }.fold(
            onSuccess = {
                InstallEntry(
                    path = displayPath,
                    description = "PATH configured",
                    success = true,
                )
            },
            onFailure = { throwable ->
                InstallEntry(
                    path = displayPath,
                    description = "PATH configuration",
                    success = false,
                    error = throwable.message,
                )
            },
        )
    }

    /**
     * Install zsh completions to `~/.zsh/completions/_opsx`.
     *
     * Creates the completions directory if needed and writes a placeholder
     * completion stub. Only installs if `.zshrc` exists (indicating zsh usage).
     */
    internal fun installCompletions(homeDir: String): InstallEntry {
        val displayPath = "~/.zsh/completions/_opsx"
        val zshrcPath = Path(homeDir, ".zshrc")
        if (!SystemFileSystem.exists(zshrcPath)) {
            return InstallEntry(
                path = displayPath,
                description = "skipped (no .zshrc)",
                success = true,
            )
        }

        val completionDir = Path(homeDir, ".zsh/completions")
        val completionFile = Path(completionDir, "_opsx")
        return runCatching {
            SystemFileSystem.createDirectories(completionDir)
            SystemFileSystem.sink(completionFile).buffered().use {
                it.writeString(
                    "# opsx zsh completion -- placeholder\n" +
                        "# regenerate with: opsx completion zsh > ~/.zsh/completions/_opsx\n",
                )
            }
        }.fold(
            onSuccess = {
                InstallEntry(
                    path = displayPath,
                    description = "installed",
                    success = true,
                )
            },
            onFailure = { throwable ->
                InstallEntry(
                    path = displayPath,
                    description = "completions",
                    success = false,
                    error = throwable.message,
                )
            },
        )
    }

    /** Copy each file from source/bin/ to opsxHome/bin/, chmod +x. */
    private fun copyBinFiles(
        sourceDir: Path,
        opsxHome: Path,
        homeDir: String,
    ): List<InstallEntry> {
        val sourceBinDir = Path(sourceDir, "bin")
        if (!SystemFileSystem.exists(sourceBinDir)) {
            return listOf(
                InstallEntry(
                    path = "bin/",
                    description = "source bin/ not found",
                    success = false,
                    error = "source bin/ directory missing",
                ),
            )
        }

        val destBinDir = Path(opsxHome, "bin")
        return SystemFileSystem
            .list(sourceBinDir)
            .map { sourcePath ->
                val destPath = Path(destBinDir, sourcePath.name)
                val displayPath = destPath.toString().replace(homeDir, "~")
                copyFileEntry(sourcePath, destPath, displayPath, executable = true)
            }.toList()
    }

    /** Copy each file from source/lib/ to opsxHome/lib/. */
    private fun copyLibFiles(
        sourceDir: Path,
        opsxHome: Path,
        homeDir: String,
    ): List<InstallEntry> {
        val sourceLibDir = Path(sourceDir, "lib")
        if (!SystemFileSystem.exists(sourceLibDir)) {
            return listOf(
                InstallEntry(
                    path = "lib/",
                    description = "source lib/ not found",
                    success = false,
                    error = "source lib/ directory missing",
                ),
            )
        }

        val destLibDir = Path(opsxHome, "lib")
        return SystemFileSystem
            .list(sourceLibDir)
            .map { sourcePath ->
                val destPath = Path(destLibDir, sourcePath.name)
                val displayPath = destPath.toString().replace(homeDir, "~")
                copyFileEntry(sourcePath, destPath, displayPath, executable = false)
            }.toList()
    }

    /** Copy a single file and return an [InstallEntry] for the result. */
    private fun copyFileEntry(
        sourcePath: Path,
        destPath: Path,
        displayPath: String,
        executable: Boolean,
    ): InstallEntry =
        runCatching {
            destPath.parent?.let { SystemFileSystem.createDirectories(it) }
            java.io.File(sourcePath.toString()).copyTo(
                java.io.File(destPath.toString()),
                overwrite = true,
            )
            if (executable) {
                java.io.File(destPath.toString()).setExecutable(true)
            }
        }.fold(
            onSuccess = {
                InstallEntry(
                    path = displayPath,
                    description = "copied",
                    success = true,
                )
            },
            onFailure = { throwable ->
                InstallEntry(
                    path = displayPath,
                    description = "copy failed",
                    success = false,
                    error = throwable.message,
                )
            },
        )
}
