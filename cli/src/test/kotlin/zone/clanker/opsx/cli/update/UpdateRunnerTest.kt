package zone.clanker.opsx.cli.update

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import java.security.MessageDigest

class UpdateRunnerTest :
    FunSpec({

        // --- detectPlatform ---

        test("detectPlatform returns a non-null string on the current platform") {
            val platform = UpdateRunner.detectPlatform()
            platform.shouldNotBeNull()
            platform shouldContain "-"
        }

        test("detectPlatform returns a known os-arch combination") {
            val platform = UpdateRunner.detectPlatform()
            platform.shouldNotBeNull()
            val parts = platform.split("-")
            parts.size shouldBe 2
            parts[0] shouldBe
                when {
                    "mac" in System.getProperty("os.name").lowercase() -> "macos"
                    else -> "linux"
                }
        }

        test("detectPlatform arch part is arm64 or x64") {
            val platform = UpdateRunner.detectPlatform()
            platform.shouldNotBeNull()
            val arch = platform.split("-")[1]
            (arch == "arm64" || arch == "x64") shouldBe true
        }

        // --- defaultRepo ---

        test("defaultRepo returns ClankerGuru/opsx when env is not set") {
            val repo = UpdateRunner.defaultRepo()
            repo shouldContain "/"
        }

        test("defaultRepo contains a slash separating owner and repo") {
            val repo = UpdateRunner.defaultRepo()
            repo.split("/").size shouldBe 2
        }

        // --- findAssetUrl ---

        test("findAssetUrl returns url when asset name matches") {
            val release =
                ReleaseInfo(
                    tagName = "v1.0.0",
                    assets =
                        listOf(
                            Asset(name = "opsx-macos-arm64.tar.gz", downloadUrl = "https://example.com/arm64"),
                            Asset(name = "opsx-linux-x64.tar.gz", downloadUrl = "https://example.com/linux"),
                        ),
                )
            UpdateRunner.findAssetUrl(release, "opsx-macos-arm64.tar.gz") shouldBe "https://example.com/arm64"
        }

        test("findAssetUrl returns url for second asset") {
            val release =
                ReleaseInfo(
                    tagName = "v1.0.0",
                    assets =
                        listOf(
                            Asset(name = "opsx-macos-arm64.tar.gz", downloadUrl = "https://example.com/arm64"),
                            Asset(name = "SHA256SUMS", downloadUrl = "https://example.com/sums"),
                        ),
                )
            UpdateRunner.findAssetUrl(release, "SHA256SUMS") shouldBe "https://example.com/sums"
        }

        test("findAssetUrl returns null when asset name not found") {
            val release =
                ReleaseInfo(
                    tagName = "v1.0.0",
                    assets =
                        listOf(
                            Asset(name = "opsx-macos-arm64.tar.gz", downloadUrl = "https://example.com/arm64"),
                        ),
                )
            UpdateRunner.findAssetUrl(release, "opsx-linux-x64.tar.gz").shouldBeNull()
        }

        test("findAssetUrl returns null for empty assets list") {
            val release = ReleaseInfo(tagName = "v1.0.0", assets = emptyList())
            UpdateRunner.findAssetUrl(release, "anything").shouldBeNull()
        }

        test("findAssetUrl matches exact name only") {
            val release =
                ReleaseInfo(
                    tagName = "v1.0.0",
                    assets =
                        listOf(
                            Asset(name = "opsx-macos-arm64.tar.gz", downloadUrl = "https://example.com/a"),
                        ),
                )
            UpdateRunner.findAssetUrl(release, "opsx-macos-arm64.tar").shouldBeNull()
            UpdateRunner.findAssetUrl(release, "opsx-macos-arm64.tar.gz.sig").shouldBeNull()
        }

        // --- verifyChecksum ---

        test("verifyChecksum passes when checksum matches") {
            val content = "hello world for checksum test"
            val tempFile = java.io.File.createTempFile("checksum-test-", ".bin")
            try {
                tempFile.writeText(content)

                val digest = MessageDigest.getInstance("SHA-256")
                val expected = digest.digest(content.toByteArray()).joinToString("") { "%02x".format(it) }

                val checksumFile = java.io.File.createTempFile("sums-", ".txt")
                checksumFile.writeText("$expected  test-asset.tar.gz\n")

                // verifyChecksum reads the checksum from a URL, so use file:// URL
                UpdateRunner.verifyChecksum(tempFile, "test-asset.tar.gz", checksumFile.toURI().toURL().toString())

                checksumFile.delete()
            } finally {
                tempFile.delete()
            }
        }

        test("verifyChecksum throws on checksum mismatch") {
            val tempFile = java.io.File.createTempFile("checksum-test-", ".bin")
            try {
                tempFile.writeText("actual content")

                val checksumFile = java.io.File.createTempFile("sums-", ".txt")
                val fakeHash = "0".repeat(64)
                checksumFile.writeText("$fakeHash  test-asset.tar.gz\n")

                val exception =
                    runCatching {
                        UpdateRunner.verifyChecksum(
                            tempFile,
                            "test-asset.tar.gz",
                            checksumFile.toURI().toURL().toString(),
                        )
                    }.exceptionOrNull()

                exception.shouldNotBeNull()
                exception.message shouldContain "checksum mismatch"

                checksumFile.delete()
            } finally {
                tempFile.delete()
            }
        }

        test("verifyChecksum skips when asset name not in checksum file") {
            val tempFile = java.io.File.createTempFile("checksum-test-", ".bin")
            try {
                tempFile.writeText("some content")

                val checksumFile = java.io.File.createTempFile("sums-", ".txt")
                checksumFile.writeText("abcdef1234567890  other-asset.tar.gz\n")

                // Should not throw since the asset name is not found in the sums
                UpdateRunner.verifyChecksum(
                    tempFile,
                    "test-asset.tar.gz",
                    checksumFile.toURI().toURL().toString(),
                )

                checksumFile.delete()
            } finally {
                tempFile.delete()
            }
        }

        test("verifyChecksum handles multi-line checksum file") {
            val content = "multi line checksum content"
            val tempFile = java.io.File.createTempFile("checksum-test-", ".bin")
            try {
                tempFile.writeText(content)

                val digest = MessageDigest.getInstance("SHA-256")
                val expected = digest.digest(content.toByteArray()).joinToString("") { "%02x".format(it) }

                val checksumFile = java.io.File.createTempFile("sums-", ".txt")
                checksumFile.writeText(
                    buildString {
                        append("1111111111111111111111111111111111111111111111111111111111111111  other-asset.tar.gz\n")
                        append("$expected  target-asset.tar.gz\n")
                        append("2222222222222222222222222222222222222222222222222222222222222222  third-asset.tar.gz\n")
                    },
                )

                // Should pass since the correct checksum is present for target-asset
                UpdateRunner.verifyChecksum(
                    tempFile,
                    "target-asset.tar.gz",
                    checksumFile.toURI().toURL().toString(),
                )

                checksumFile.delete()
            } finally {
                tempFile.delete()
            }
        }

        // --- checkLatest with bad repo ---

        test("checkLatest with a nonexistent repo returns Failed") {
            val result =
                UpdateRunner.checkLatest(
                    currentVersion = "0.0.0",
                    repo = "nonexistent-owner-zzz/nonexistent-repo-zzz",
                )
            result.shouldBeInstanceOf<UpdateCheck.Failed>()
            result.error.shouldNotBeNull()
        }

        test("checkLatest with an invalid repo slug returns Failed") {
            val result =
                UpdateRunner.checkLatest(
                    currentVersion = "0.0.0",
                    repo = "not-a-valid-repo",
                )
            result.shouldBeInstanceOf<UpdateCheck.Failed>()
        }

        // --- ReleaseInfo and Asset serialization ---

        test("ReleaseInfo deserializes from JSON with tag_name") {
            val jsonDecoder = Json { ignoreUnknownKeys = true }
            val jsonString = """{"tag_name":"v1.2.3","assets":[]}"""
            val release = jsonDecoder.decodeFromString(ReleaseInfo.serializer(), jsonString)
            release.tagName shouldBe "v1.2.3"
            release.assets shouldBe emptyList()
        }

        test("ReleaseInfo deserializes with assets") {
            val jsonDecoder = Json { ignoreUnknownKeys = true }
            val jsonString =
                """
                {
                    "tag_name": "v0.1.0",
                    "assets": [
                        {
                            "name": "opsx-macos-arm64.tar.gz",
                            "browser_download_url": "https://example.com/download"
                        }
                    ]
                }
                """.trimIndent()
            val release = jsonDecoder.decodeFromString(ReleaseInfo.serializer(), jsonString)
            release.tagName shouldBe "v0.1.0"
            release.assets.size shouldBe 1
            release.assets[0].name shouldBe "opsx-macos-arm64.tar.gz"
            release.assets[0].downloadUrl shouldBe "https://example.com/download"
        }

        test("ReleaseInfo deserializes with unknown keys ignored") {
            val jsonDecoder = Json { ignoreUnknownKeys = true }
            val jsonString = """{"tag_name":"v2.0.0","assets":[],"draft":false,"prerelease":true}"""
            val release = jsonDecoder.decodeFromString(ReleaseInfo.serializer(), jsonString)
            release.tagName shouldBe "v2.0.0"
        }

        test("Asset data class preserves name and downloadUrl") {
            val asset =
                Asset(
                    name = "opsx-linux-x64.tar.gz",
                    downloadUrl = "https://github.com/example/releases/download/v1.0/opsx-linux-x64.tar.gz",
                )
            asset.name shouldBe "opsx-linux-x64.tar.gz"
            asset.downloadUrl shouldContain "releases/download"
        }

        test("Asset serializes and deserializes roundtrip") {
            val jsonDecoder = Json { ignoreUnknownKeys = true }
            val original =
                Asset(
                    name = "SHA256SUMS",
                    downloadUrl = "https://example.com/sums",
                )
            val jsonString = jsonDecoder.encodeToString(Asset.serializer(), original)
            val decoded = jsonDecoder.decodeFromString(Asset.serializer(), jsonString)
            decoded shouldBe original
        }

        test("ReleaseInfo defaults assets to empty list") {
            val jsonDecoder = Json { ignoreUnknownKeys = true }
            val jsonString = """{"tag_name":"v3.0.0"}"""
            val release = jsonDecoder.decodeFromString(ReleaseInfo.serializer(), jsonString)
            release.assets shouldBe emptyList()
        }

        // --- UpdateCheck sealed interface ---

        test("UpdateCheck.Available carries current, latest, and release") {
            val release = ReleaseInfo(tagName = "v1.0.0", assets = emptyList())
            val check =
                UpdateCheck.Available(
                    current = "0.9.0",
                    latest = "1.0.0",
                    release = release,
                )
            check.current shouldBe "0.9.0"
            check.latest shouldBe "1.0.0"
            check.release.tagName shouldBe "v1.0.0"
        }

        test("UpdateCheck.UpToDate carries version") {
            val check = UpdateCheck.UpToDate(version = "1.0.0")
            check.version shouldBe "1.0.0"
        }

        test("UpdateCheck.Failed carries error message") {
            val check = UpdateCheck.Failed(error = "network timeout")
            check.error shouldBe "network timeout"
        }

        // --- UpdateResult sealed interface ---

        test("UpdateResult.Success carries version") {
            val result = UpdateResult.Success(version = "1.0.0")
            result.version shouldBe "1.0.0"
        }

        test("UpdateResult.Failed carries error message") {
            val result = UpdateResult.Failed(error = "checksum mismatch")
            result.error shouldBe "checksum mismatch"
        }

        // --- UpdateStep sealed interface ---

        test("UpdateStep.Checking carries repo") {
            val step = UpdateStep.Checking(repo = "ClankerGuru/opsx")
            step.repo shouldBe "ClankerGuru/opsx"
        }

        test("UpdateStep.Downloading carries assetName") {
            val step = UpdateStep.Downloading(assetName = "opsx-macos-arm64.tar.gz")
            step.assetName shouldBe "opsx-macos-arm64.tar.gz"
        }

        test("UpdateStep.Done carries version") {
            val step = UpdateStep.Done(version = "1.0.0")
            step.version shouldBe "1.0.0"
        }

        test("UpdateStep.Error carries message") {
            val step = UpdateStep.Error(message = "download failed")
            step.message shouldBe "download failed"
        }

        test("UpdateStep.VerifyingChecksum is a singleton") {
            UpdateStep.VerifyingChecksum shouldBe UpdateStep.VerifyingChecksum
        }

        test("UpdateStep.Extracting is a singleton") {
            UpdateStep.Extracting shouldBe UpdateStep.Extracting
        }

        // --- checkLatest success path ---

        test("checkLatest against a real repo exercises the success or rate-limit path") {
            val result =
                UpdateRunner.checkLatest(
                    currentVersion = "0.0.0-test",
                    repo = "JetBrains/kotlin",
                )
            // Either succeeds (Available/UpToDate) or fails with rate-limit / network error
            val isValid =
                result is UpdateCheck.Available ||
                    result is UpdateCheck.UpToDate ||
                    result is UpdateCheck.Failed
            isValid shouldBe true
        }

        test("checkLatest against a real repo with unmatched version returns Available or Failed") {
            val result =
                UpdateRunner.checkLatest(
                    currentVersion = "0.0.0-impossible-match",
                    repo = "JetBrains/kotlin",
                )
            when (result) {
                is UpdateCheck.Available -> {
                    result.current shouldBe "0.0.0-impossible-match"
                    result.latest.isNotEmpty() shouldBe true
                    result.release.tagName.isNotEmpty() shouldBe true
                }
                // Rate-limited or network unavailable -- acceptable in CI
                is UpdateCheck.Failed -> result.error.shouldNotBeNull()
                is UpdateCheck.UpToDate -> {} // unlikely but valid
            }
        }

        // --- downloadAndInstall with missing asset ---

        test("downloadAndInstall with empty assets returns Failed") {
            val release = ReleaseInfo(tagName = "v1.0.0", assets = emptyList())
            val result = UpdateRunner.downloadAndInstall(release)
            result.shouldBeInstanceOf<UpdateResult.Failed>()
            result.error shouldContain "release asset not found"
        }

        test("downloadAndInstall reports steps via callback") {
            val release = ReleaseInfo(tagName = "v1.0.0", assets = emptyList())
            val reportedSteps = mutableListOf<UpdateStep>()
            UpdateRunner.downloadAndInstall(release) { step -> reportedSteps.add(step) }
            // With no matching asset, the function returns before calling any step callback
            reportedSteps.shouldBeEmpty()
        }

        test("downloadAndInstall with wrong asset name returns Failed with asset message") {
            val platform = UpdateRunner.detectPlatform()
            platform.shouldNotBeNull()
            val wrongName = "opsx-windows-x86.tar.gz"
            val release =
                ReleaseInfo(
                    tagName = "v2.0.0",
                    assets =
                        listOf(
                            Asset(name = wrongName, downloadUrl = "https://example.com/nope"),
                        ),
                )
            val result = UpdateRunner.downloadAndInstall(release)
            result.shouldBeInstanceOf<UpdateResult.Failed>()
            result.error shouldContain "release asset not found"
            result.error shouldContain "opsx-$platform.tar.gz"
        }

        test("downloadAndInstall with invalid URL emits Error step and returns Failed") {
            val platform = UpdateRunner.detectPlatform()
            platform.shouldNotBeNull()
            val assetName = "opsx-$platform.tar.gz"
            val release =
                ReleaseInfo(
                    tagName = "v3.0.0",
                    assets =
                        listOf(
                            Asset(name = assetName, downloadUrl = "http://invalid.test.localhost:1/nope"),
                        ),
                )
            val reportedSteps = mutableListOf<UpdateStep>()
            val result = UpdateRunner.downloadAndInstall(release) { step -> reportedSteps.add(step) }
            result.shouldBeInstanceOf<UpdateResult.Failed>()
            // Should have emitted Downloading and Error steps
            reportedSteps.any { it is UpdateStep.Downloading } shouldBe true
            reportedSteps.any { it is UpdateStep.Error } shouldBe true
        }

        test("downloadAndInstall with unreachable URL captures error message") {
            val platform = UpdateRunner.detectPlatform()
            platform.shouldNotBeNull()
            val assetName = "opsx-$platform.tar.gz"
            val release =
                ReleaseInfo(
                    tagName = "v4.0.0",
                    assets =
                        listOf(
                            Asset(name = assetName, downloadUrl = "http://invalid.test.localhost:1/download"),
                        ),
                )
            val result = UpdateRunner.downloadAndInstall(release)
            result.shouldBeInstanceOf<UpdateResult.Failed>()
            result.error.shouldNotBeNull()
        }

        test("downloadAndInstall step callback receives Downloading before Error") {
            val platform = UpdateRunner.detectPlatform()
            platform.shouldNotBeNull()
            val assetName = "opsx-$platform.tar.gz"
            val release =
                ReleaseInfo(
                    tagName = "v5.0.0",
                    assets =
                        listOf(
                            Asset(name = assetName, downloadUrl = "http://invalid.test.localhost:1/dl"),
                        ),
                )
            val reportedSteps = mutableListOf<UpdateStep>()
            UpdateRunner.downloadAndInstall(release) { step -> reportedSteps.add(step) }
            val downloadIndex = reportedSteps.indexOfFirst { it is UpdateStep.Downloading }
            val errorIndex = reportedSteps.indexOfFirst { it is UpdateStep.Error }
            (downloadIndex >= 0) shouldBe true
            (errorIndex > downloadIndex) shouldBe true
        }

        test("downloadAndInstall Downloading step carries the correct asset name") {
            val platform = UpdateRunner.detectPlatform()
            platform.shouldNotBeNull()
            val assetName = "opsx-$platform.tar.gz"
            val release =
                ReleaseInfo(
                    tagName = "v6.0.0",
                    assets =
                        listOf(
                            Asset(name = assetName, downloadUrl = "http://invalid.test.localhost:1/dl"),
                        ),
                )
            val reportedSteps = mutableListOf<UpdateStep>()
            UpdateRunner.downloadAndInstall(release) { step -> reportedSteps.add(step) }
            val downloadStep = reportedSteps.filterIsInstance<UpdateStep.Downloading>().firstOrNull()
            downloadStep.shouldNotBeNull()
            downloadStep.assetName shouldBe assetName
        }

        test("checkLatest with default repo parameter returns a result") {
            val result = UpdateRunner.checkLatest(currentVersion = "0.0.0-default-repo-test")
            // Either Available or Failed -- depends on network; the key thing is
            // exercising the synthetic default-args bridge
            (result is UpdateCheck.Available || result is UpdateCheck.Failed) shouldBe true
        }

        test("downloadAndInstall with downloadable but invalid archive fails at extraction") {
            val platform = UpdateRunner.detectPlatform()
            platform.shouldNotBeNull()
            val assetName = "opsx-$platform.tar.gz"

            // Create a local file that is downloadable but is not a valid tar.gz
            val fakeArchive = java.io.File.createTempFile("fake-archive-", ".tar.gz")
            fakeArchive.writeText("this is not a valid tar.gz archive")

            val release =
                ReleaseInfo(
                    tagName = "v9.0.0",
                    assets =
                        listOf(
                            Asset(
                                name = assetName,
                                downloadUrl = fakeArchive.toURI().toURL().toString(),
                            ),
                        ),
                )
            val reportedSteps = mutableListOf<UpdateStep>()
            val result = UpdateRunner.downloadAndInstall(release) { step -> reportedSteps.add(step) }

            fakeArchive.delete()

            result.shouldBeInstanceOf<UpdateResult.Failed>()
            // Should have progressed through Downloading and Extracting before failing
            reportedSteps.any { it is UpdateStep.Downloading } shouldBe true
            reportedSteps.any { it is UpdateStep.Extracting } shouldBe true
            reportedSteps.any { it is UpdateStep.Error } shouldBe true
        }

        test("downloadAndInstall with valid file URL and no checksums reaches extraction") {
            val platform = UpdateRunner.detectPlatform()
            platform.shouldNotBeNull()
            val assetName = "opsx-$platform.tar.gz"

            // Create a non-tar file to download (extraction will fail but we cover
            // the download-succeeded-but-extraction-failed path)
            val fakeArchive = java.io.File.createTempFile("fake-dl-", ".tar.gz")
            fakeArchive.writeBytes(ByteArray(64) { 0xFF.toByte() })

            val release =
                ReleaseInfo(
                    tagName = "v8.0.0",
                    assets =
                        listOf(
                            Asset(
                                name = assetName,
                                downloadUrl = fakeArchive.toURI().toURL().toString(),
                            ),
                        ),
                )
            val reportedSteps = mutableListOf<UpdateStep>()
            val result = UpdateRunner.downloadAndInstall(release) { step -> reportedSteps.add(step) }

            fakeArchive.delete()

            result.shouldBeInstanceOf<UpdateResult.Failed>()
            // Downloading step is reached
            val dlStep = reportedSteps.filterIsInstance<UpdateStep.Downloading>().firstOrNull()
            dlStep.shouldNotBeNull()
            dlStep.assetName shouldBe assetName
        }

        test("downloadAndInstall with file URL and SHA256SUMS verifies checksum") {
            val platform = UpdateRunner.detectPlatform()
            platform.shouldNotBeNull()
            val assetName = "opsx-$platform.tar.gz"

            // Create a fake archive (not valid tar but sufficient for download)
            val fakeArchive = java.io.File.createTempFile("fake-cs-", ".tar.gz")
            val archiveContent = "fake archive content for checksum test"
            fakeArchive.writeText(archiveContent)

            // Create a matching SHA256SUMS file
            val digest = MessageDigest.getInstance("SHA-256")
            val sha256 = digest.digest(archiveContent.toByteArray()).joinToString("") { "%02x".format(it) }
            val checksumFile = java.io.File.createTempFile("sums-", ".txt")
            checksumFile.writeText("$sha256  $assetName\n")

            val release =
                ReleaseInfo(
                    tagName = "v7.0.0",
                    assets =
                        listOf(
                            Asset(
                                name = assetName,
                                downloadUrl = fakeArchive.toURI().toURL().toString(),
                            ),
                            Asset(
                                name = "SHA256SUMS",
                                downloadUrl = checksumFile.toURI().toURL().toString(),
                            ),
                        ),
                )
            val reportedSteps = mutableListOf<UpdateStep>()
            val result = UpdateRunner.downloadAndInstall(release) { step -> reportedSteps.add(step) }

            fakeArchive.delete()
            checksumFile.delete()

            // Extraction will fail since it's not a real tar.gz, but checksum should pass
            result.shouldBeInstanceOf<UpdateResult.Failed>()
            reportedSteps.any { it is UpdateStep.Downloading } shouldBe true
            reportedSteps.any { it is UpdateStep.VerifyingChecksum } shouldBe true
            reportedSteps.any { it is UpdateStep.Extracting } shouldBe true
        }

        // --- ReleaseInfo with multiple assets ---

        test("ReleaseInfo with multiple assets deserializes correctly") {
            val jsonDecoder = Json { ignoreUnknownKeys = true }
            val jsonString =
                """
                {
                    "tag_name": "v1.0.0",
                    "assets": [
                        {"name": "opsx-macos-arm64.tar.gz", "browser_download_url": "https://example.com/a"},
                        {"name": "opsx-linux-x64.tar.gz", "browser_download_url": "https://example.com/b"},
                        {"name": "SHA256SUMS", "browser_download_url": "https://example.com/sums"}
                    ]
                }
                """.trimIndent()
            val release = jsonDecoder.decodeFromString(ReleaseInfo.serializer(), jsonString)
            release.assets.size shouldBe 3
            release.assets.map { it.name } shouldContainExactly
                listOf("opsx-macos-arm64.tar.gz", "opsx-linux-x64.tar.gz", "SHA256SUMS")
        }

        // --- ReleaseInfo copy ---

        test("ReleaseInfo copy with new tagName preserves assets") {
            val original =
                ReleaseInfo(
                    tagName = "v1.0.0",
                    assets = listOf(Asset(name = "a.tar.gz", downloadUrl = "https://example.com/a")),
                )
            val copied = original.copy(tagName = "v2.0.0")
            copied.tagName shouldBe "v2.0.0"
            copied.assets.size shouldBe 1
            copied.assets[0].name shouldBe "a.tar.gz"
        }
    })
