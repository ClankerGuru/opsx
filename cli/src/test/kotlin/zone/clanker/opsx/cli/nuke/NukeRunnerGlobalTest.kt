package zone.clanker.opsx.cli.nuke

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

/** Tests for [NukeRunner.nukeGlobal]. */
class NukeRunnerGlobalTest :
    FunSpec({

        fun createTempRoot(): Path {
            val tempFile = java.io.File.createTempFile("nuke-global-test-", "")
            val tempDir = Path(tempFile.absolutePath + "-dir")
            SystemFileSystem.createDirectories(tempDir)
            return tempDir
        }

        fun cleanupDir(path: Path) {
            java.io.File(path.toString()).deleteRecursively()
        }

        fun writeFile(
            root: Path,
            relativePath: String,
            content: String,
        ) {
            val filePath = Path(root, relativePath)
            filePath.parent?.let { SystemFileSystem.createDirectories(it) }
            SystemFileSystem.sink(filePath).buffered().use { it.writeString(content) }
        }

        fun readFile(
            root: Path,
            relativePath: String,
        ): String = SystemFileSystem.source(Path(root, relativePath)).buffered().use { it.readString() }

        fun exists(
            root: Path,
            relativePath: String,
        ): Boolean = SystemFileSystem.exists(Path(root, relativePath))

        test("nukeGlobal removes opsx home directory") {
            val fakeHome = createTempRoot()
            try {
                val opsxHome = Path(fakeHome, ".opsx").toString()
                writeFile(fakeHome, ".opsx/bin/opsx", "#!/bin/sh")
                writeFile(fakeHome, ".opsx/lib/foo.jar", "jar")
                val entries = NukeRunner.nukeGlobal(fakeHome.toString(), opsxHome)
                val homeEntry = entries.first { it.path == "~/.opsx" }
                homeEntry.success.shouldBeTrue()
                homeEntry.description shouldBe "removed"
                exists(fakeHome, ".opsx").shouldBeFalse()
            } finally {
                cleanupDir(fakeHome)
            }
        }

        test("nukeGlobal strips PATH block from rc files") {
            val fakeHome = createTempRoot()
            try {
                val rcContent =
                    buildString {
                        append("# existing config\n")
                        append("# >>> opsx >>>\n")
                        append("export PATH=\"\$HOME/.opsx/bin:\$PATH\"\n")
                        append("# <<< opsx <<<\n")
                        append("# more config\n")
                    }
                writeFile(fakeHome, ".zshrc", rcContent)
                writeFile(fakeHome, ".bashrc", rcContent)
                writeFile(fakeHome, ".profile", "clean profile\n")

                val entries = NukeRunner.nukeGlobal(fakeHome.toString(), Path(fakeHome, ".opsx").toString())

                val zshrc = entries.first { it.path == "~/.zshrc" }
                zshrc.success.shouldBeTrue()
                zshrc.description shouldBe "PATH block stripped"
                val zshContent = readFile(fakeHome, ".zshrc")
                zshContent shouldContain "# existing config"
                zshContent shouldContain "# more config"
                zshContent.contains(">>> opsx >>>").shouldBeFalse()

                entries.first { it.path == "~/.bashrc" }.description shouldBe "PATH block stripped"
                entries.first { it.path == "~/.profile" }.description shouldBe "no PATH block"
            } finally {
                cleanupDir(fakeHome)
            }
        }

        test("nukeGlobal removes zsh completions") {
            val fakeHome = createTempRoot()
            try {
                writeFile(fakeHome, ".zsh/completions/_opsx", "#compdef opsx")
                val entries = NukeRunner.nukeGlobal(fakeHome.toString(), Path(fakeHome, ".opsx").toString())
                val completions = entries.first { it.path == "~/.zsh/completions/_opsx" }
                completions.success.shouldBeTrue()
                completions.description shouldBe "removed"
                exists(fakeHome, ".zsh/completions/_opsx").shouldBeFalse()
            } finally {
                cleanupDir(fakeHome)
            }
        }

        test("nukeGlobal on empty home reports all not found") {
            val fakeHome = createTempRoot()
            try {
                val entries = NukeRunner.nukeGlobal(fakeHome.toString(), Path(fakeHome, ".opsx").toString())
                entries.forEach { entry ->
                    entry.success.shouldBeTrue()
                    (entry.description == "not found" || entry.description == "no PATH block").shouldBeTrue()
                }
            } finally {
                cleanupDir(fakeHome)
            }
        }

        test("nukeGlobal with keepRc skips rc file stripping") {
            val fakeHome = createTempRoot()
            try {
                val rcContent =
                    buildString {
                        append("# >>> opsx >>>\n")
                        append("export PATH=\"\$HOME/.opsx/bin:\$PATH\"\n")
                        append("# <<< opsx <<<\n")
                    }
                writeFile(fakeHome, ".zshrc", rcContent)
                val entries = NukeRunner.nukeGlobal(fakeHome.toString(), Path(fakeHome, ".opsx").toString(), true)
                entries.none { it.path == "~/.zshrc" }.shouldBeTrue()
                entries.none { it.path == "~/.bashrc" }.shouldBeTrue()
                entries.none { it.path == "~/.profile" }.shouldBeTrue()
                readFile(fakeHome, ".zshrc") shouldContain ">>> opsx >>>"
            } finally {
                cleanupDir(fakeHome)
            }
        }

        test("nukeGlobal returns 5 entries without keepRc") {
            val fakeHome = createTempRoot()
            try {
                NukeRunner.nukeGlobal(fakeHome.toString(), Path(fakeHome, ".opsx").toString()) shouldHaveSize 5
            } finally {
                cleanupDir(fakeHome)
            }
        }

        test("nukeGlobal returns 2 entries with keepRc") {
            val fakeHome = createTempRoot()
            try {
                NukeRunner.nukeGlobal(fakeHome.toString(), Path(fakeHome, ".opsx").toString(), true) shouldHaveSize 2
            } finally {
                cleanupDir(fakeHome)
            }
        }

        test("nukeGlobal reports failure when rc file is unreadable") {
            val fakeHome = createTempRoot()
            try {
                writeFile(fakeHome, ".zshrc", "# >>> opsx >>>\nexport PATH\n# <<< opsx <<<\n")
                java.io.File(Path(fakeHome, ".zshrc").toString()).setReadable(false)
                val entries = NukeRunner.nukeGlobal(fakeHome.toString(), Path(fakeHome, ".opsx").toString())
                val zshrc = entries.first { it.path == "~/.zshrc" }
                zshrc.success.shouldBeFalse()
                zshrc.description shouldBe "strip PATH block"
                zshrc.error.shouldNotBeNull()
            } finally {
                java.io.File(Path(fakeHome, ".zshrc").toString()).setReadable(true)
                cleanupDir(fakeHome)
            }
        }

        test("nukeGlobal reports failure when completions parent is read-only") {
            val fakeHome = createTempRoot()
            try {
                writeFile(fakeHome, ".zsh/completions/_opsx", "#compdef opsx")
                java.io.File(Path(fakeHome, ".zsh/completions").toString()).setWritable(false)
                val entries = NukeRunner.nukeGlobal(fakeHome.toString(), Path(fakeHome, ".opsx").toString())
                val completions = entries.first { it.path == "~/.zsh/completions/_opsx" }
                completions.success.shouldBeFalse()
                completions.error.shouldNotBeNull()
            } finally {
                java.io.File(Path(fakeHome, ".zsh/completions").toString()).setWritable(true)
                cleanupDir(fakeHome)
            }
        }

        test("nukeGlobal reports failure when opsx home dir has undeletable contents") {
            val fakeHome = createTempRoot()
            try {
                writeFile(fakeHome, ".opsx/bin/opsx", "#!/bin/sh")
                java.io.File(Path(fakeHome, ".opsx/bin").toString()).setWritable(false)
                val entries = NukeRunner.nukeGlobal(fakeHome.toString(), Path(fakeHome, ".opsx").toString())
                val homeEntry = entries.first { it.path == "~/.opsx" }
                homeEntry.success.shouldBeFalse()
                homeEntry.error shouldContain "failed to delete"
            } finally {
                java.io.File(Path(fakeHome, ".opsx/bin").toString()).setWritable(true)
                cleanupDir(fakeHome)
            }
        }
    })
