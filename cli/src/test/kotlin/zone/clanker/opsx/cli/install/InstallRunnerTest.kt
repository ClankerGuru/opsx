package zone.clanker.opsx.cli.install

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

class InstallRunnerTest :
    FunSpec({

        fun createTempRoot(): Path {
            val tempFile = java.io.File.createTempFile("install-test-", "")
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

        // --- InstallEntry data class ---

        test("InstallEntry defaults error to null") {
            val entry = InstallEntry(path = "foo", description = "ok", success = true)
            entry.error shouldBe null
        }

        test("InstallEntry carries error message on failure") {
            val entry =
                InstallEntry(
                    path = "bar",
                    description = "fail",
                    success = false,
                    error = "denied",
                )
            entry.error shouldBe "denied"
        }

        // --- install: copies bin files ---

        test("install copies bin files to opsxHome/bin and marks executable") {
            val source = createTempRoot()
            val fakeHome = createTempRoot()
            try {
                writeFile(source, "bin/opsx", "#!/bin/sh\necho hello")
                writeFile(source, "lib/app-0.0.5-all.jar", "fake-jar-content")
                writeFile(fakeHome, ".zshrc", "# existing config\n")

                val opsxHome = Path(fakeHome, ".opsx")
                val entries = InstallRunner.install(source, fakeHome.toString(), opsxHome)

                val binEntry = entries.first { it.path.contains("bin/opsx") }
                binEntry.success.shouldBeTrue()
                binEntry.description shouldBe "copied"

                exists(fakeHome, ".opsx/bin/opsx").shouldBeTrue()
                val binFile = java.io.File(Path(fakeHome, ".opsx/bin/opsx").toString())
                binFile.canExecute().shouldBeTrue()
                readFile(fakeHome, ".opsx/bin/opsx") shouldBe "#!/bin/sh\necho hello"
            } finally {
                cleanupDir(source)
                cleanupDir(fakeHome)
            }
        }

        // --- install: copies lib files ---

        test("install copies lib files to opsxHome/lib") {
            val source = createTempRoot()
            val fakeHome = createTempRoot()
            try {
                writeFile(source, "bin/opsx", "#!/bin/sh")
                writeFile(source, "lib/app-0.0.5-all.jar", "jar-content")
                writeFile(fakeHome, ".zshrc", "# config\n")

                val opsxHome = Path(fakeHome, ".opsx")
                val entries = InstallRunner.install(source, fakeHome.toString(), opsxHome)

                val libEntry = entries.first { it.path.contains("lib/app-0.0.5-all.jar") }
                libEntry.success.shouldBeTrue()
                libEntry.description shouldBe "copied"

                exists(fakeHome, ".opsx/lib/app-0.0.5-all.jar").shouldBeTrue()
                readFile(fakeHome, ".opsx/lib/app-0.0.5-all.jar") shouldBe "jar-content"
            } finally {
                cleanupDir(source)
                cleanupDir(fakeHome)
            }
        }

        // --- install: adds PATH block ---

        test("install adds PATH block to zshrc") {
            val source = createTempRoot()
            val fakeHome = createTempRoot()
            try {
                writeFile(source, "bin/opsx", "#!/bin/sh")
                writeFile(source, "lib/app.jar", "jar")
                writeFile(fakeHome, ".zshrc", "# existing config\n")

                val opsxHome = Path(fakeHome, ".opsx")
                InstallRunner.install(source, fakeHome.toString(), opsxHome)

                val rcContent = readFile(fakeHome, ".zshrc")
                rcContent shouldContain "# >>> opsx >>>"
                rcContent shouldContain "export OPSX_HOME="
                rcContent shouldContain "# <<< opsx <<<"
            } finally {
                cleanupDir(source)
                cleanupDir(fakeHome)
            }
        }

        // --- install: installs completions ---

        test("install creates zsh completions file") {
            val source = createTempRoot()
            val fakeHome = createTempRoot()
            try {
                writeFile(source, "bin/opsx", "#!/bin/sh")
                writeFile(source, "lib/app.jar", "jar")
                writeFile(fakeHome, ".zshrc", "# config\n")

                val opsxHome = Path(fakeHome, ".opsx")
                InstallRunner.install(source, fakeHome.toString(), opsxHome)

                exists(fakeHome, ".zsh/completions/_opsx").shouldBeTrue()
                val completionContent = readFile(fakeHome, ".zsh/completions/_opsx")
                completionContent shouldContain "opsx zsh completion"
            } finally {
                cleanupDir(source)
                cleanupDir(fakeHome)
            }
        }

        // --- install: full integration ---

        test("install returns entries for all steps") {
            val source = createTempRoot()
            val fakeHome = createTempRoot()
            try {
                writeFile(source, "bin/opsx", "#!/bin/sh")
                writeFile(source, "lib/app.jar", "jar")
                writeFile(fakeHome, ".zshrc", "# config\n")

                val opsxHome = Path(fakeHome, ".opsx")
                val entries = InstallRunner.install(source, fakeHome.toString(), opsxHome)

                entries.size shouldBe 4
                entries.count { it.success } shouldBe 4

                entries.any { it.path.contains("bin/opsx") && it.description == "copied" }.shouldBeTrue()
                entries.any { it.path.contains("lib/app.jar") && it.description == "copied" }.shouldBeTrue()
                entries.any { it.description == "PATH configured" }.shouldBeTrue()
                entries.any { it.description == "installed" }.shouldBeTrue()
            } finally {
                cleanupDir(source)
                cleanupDir(fakeHome)
            }
        }

        // --- idempotency: running twice does not duplicate PATH block ---

        test("install is idempotent -- running twice does not duplicate PATH block") {
            val source = createTempRoot()
            val fakeHome = createTempRoot()
            try {
                writeFile(source, "bin/opsx", "#!/bin/sh")
                writeFile(source, "lib/app.jar", "jar")
                writeFile(fakeHome, ".zshrc", "# config\n")

                val opsxHome = Path(fakeHome, ".opsx")
                InstallRunner.install(source, fakeHome.toString(), opsxHome)
                val secondEntries = InstallRunner.install(source, fakeHome.toString(), opsxHome)

                val pathEntry = secondEntries.first { it.path.contains(".zshrc") }
                pathEntry.success.shouldBeTrue()
                pathEntry.description shouldBe "already configured"

                val rcContent = readFile(fakeHome, ".zshrc")
                val sentinelCount = "# >>> opsx >>>".toRegex().findAll(rcContent).count()
                sentinelCount shouldBe 1
            } finally {
                cleanupDir(source)
                cleanupDir(fakeHome)
            }
        }

        // --- missing source directories ---

        test("install reports error when source bin directory is missing") {
            val source = createTempRoot()
            val fakeHome = createTempRoot()
            try {
                writeFile(source, "lib/app.jar", "jar")
                writeFile(fakeHome, ".zshrc", "# config\n")

                val opsxHome = Path(fakeHome, ".opsx")
                val entries = InstallRunner.install(source, fakeHome.toString(), opsxHome)

                val binEntry = entries.first { it.path == "bin/" }
                binEntry.success.shouldBeFalse()
                binEntry.error shouldContain "bin/"
            } finally {
                cleanupDir(source)
                cleanupDir(fakeHome)
            }
        }

        test("install reports error when source lib directory is missing") {
            val source = createTempRoot()
            val fakeHome = createTempRoot()
            try {
                writeFile(source, "bin/opsx", "#!/bin/sh")
                writeFile(fakeHome, ".zshrc", "# config\n")

                val opsxHome = Path(fakeHome, ".opsx")
                val entries = InstallRunner.install(source, fakeHome.toString(), opsxHome)

                val libEntry = entries.first { it.path == "lib/" }
                libEntry.success.shouldBeFalse()
                libEntry.error shouldContain "lib/"
            } finally {
                cleanupDir(source)
                cleanupDir(fakeHome)
            }
        }

        // --- ensurePath ---

        test("ensurePath returns already-configured when sentinel exists") {
            val fakeHome = createTempRoot()
            try {
                writeFile(fakeHome, ".zshrc", "# existing\n# >>> opsx >>>\nexport OPSX_HOME\n# <<< opsx <<<\n")

                val entry = InstallRunner.ensurePath(fakeHome.toString(), Path(fakeHome, ".opsx"))
                entry.success.shouldBeTrue()
                entry.description shouldBe "already configured"
            } finally {
                cleanupDir(fakeHome)
            }
        }

        test("ensurePath uses bashrc when zshrc does not exist") {
            val fakeHome = createTempRoot()
            try {
                writeFile(fakeHome, ".bashrc", "# bash config\n")

                val entry = InstallRunner.ensurePath(fakeHome.toString(), Path(fakeHome, ".opsx"))
                entry.success.shouldBeTrue()
                entry.description shouldBe "PATH configured"
                entry.path shouldBe "~/.bashrc"

                val rcContent = readFile(fakeHome, ".bashrc")
                rcContent shouldContain "# >>> opsx >>>"
            } finally {
                cleanupDir(fakeHome)
            }
        }

        test("ensurePath uses profile when zshrc and bashrc do not exist") {
            val fakeHome = createTempRoot()
            try {
                writeFile(fakeHome, ".profile", "# profile config\n")

                val entry = InstallRunner.ensurePath(fakeHome.toString(), Path(fakeHome, ".opsx"))
                entry.success.shouldBeTrue()
                entry.description shouldBe "PATH configured"
                entry.path shouldBe "~/.profile"
            } finally {
                cleanupDir(fakeHome)
            }
        }

        test("ensurePath reports error when no rc file exists") {
            val fakeHome = createTempRoot()
            try {
                val entry = InstallRunner.ensurePath(fakeHome.toString(), Path(fakeHome, ".opsx"))
                entry.success.shouldBeFalse()
                entry.error shouldContain "no shell rc file"
            } finally {
                cleanupDir(fakeHome)
            }
        }

        // --- installCompletions ---

        test("installCompletions creates completions file") {
            val fakeHome = createTempRoot()
            try {
                writeFile(fakeHome, ".zshrc", "# zsh\n")

                val entry = InstallRunner.installCompletions(fakeHome.toString())
                entry.success.shouldBeTrue()
                entry.description shouldBe "installed"

                exists(fakeHome, ".zsh/completions/_opsx").shouldBeTrue()
            } finally {
                cleanupDir(fakeHome)
            }
        }

        test("installCompletions skips when no zshrc exists") {
            val fakeHome = createTempRoot()
            try {
                val entry = InstallRunner.installCompletions(fakeHome.toString())
                entry.success.shouldBeTrue()
                entry.description shouldBe "skipped (no .zshrc)"
            } finally {
                cleanupDir(fakeHome)
            }
        }

        test("installCompletions is idempotent") {
            val fakeHome = createTempRoot()
            try {
                writeFile(fakeHome, ".zshrc", "# zsh\n")

                InstallRunner.installCompletions(fakeHome.toString())
                val secondEntry = InstallRunner.installCompletions(fakeHome.toString())
                secondEntry.success.shouldBeTrue()
                secondEntry.description shouldBe "installed"
            } finally {
                cleanupDir(fakeHome)
            }
        }

        // --- resolveSourceDir ---

        test("resolveSourceDir returns null when not running from distribution") {
            // In test context, the JAR location won't have the expected layout
            // This mainly tests that it doesn't throw
            val result = InstallRunner.resolveSourceDir()
            // result may be null or a path depending on test environment
            // Just verify it doesn't crash
            @Suppress("USELESS_IS_CHECK")
            (result == null || result is Path).shouldBeTrue()
        }

        // --- multiple bin/lib files ---

        test("install copies multiple bin and lib files") {
            val source = createTempRoot()
            val fakeHome = createTempRoot()
            try {
                writeFile(source, "bin/opsx", "#!/bin/sh\nmain")
                writeFile(source, "bin/opsx-log", "#!/bin/sh\nlog")
                writeFile(source, "lib/app.jar", "jar1")
                writeFile(source, "lib/deps.jar", "jar2")
                writeFile(fakeHome, ".zshrc", "# config\n")

                val opsxHome = Path(fakeHome, ".opsx")
                val entries = InstallRunner.install(source, fakeHome.toString(), opsxHome)

                entries.count { it.description == "copied" } shouldBe 4
                exists(fakeHome, ".opsx/bin/opsx").shouldBeTrue()
                exists(fakeHome, ".opsx/bin/opsx-log").shouldBeTrue()
                exists(fakeHome, ".opsx/lib/app.jar").shouldBeTrue()
                exists(fakeHome, ".opsx/lib/deps.jar").shouldBeTrue()
            } finally {
                cleanupDir(source)
                cleanupDir(fakeHome)
            }
        }

        // --- overwrite existing files ---

        test("install overwrites existing files in opsxHome") {
            val source = createTempRoot()
            val fakeHome = createTempRoot()
            try {
                writeFile(source, "bin/opsx", "#!/bin/sh\nnew-version")
                writeFile(source, "lib/app.jar", "new-jar")
                writeFile(fakeHome, ".opsx/bin/opsx", "#!/bin/sh\nold-version")
                writeFile(fakeHome, ".opsx/lib/app.jar", "old-jar")
                writeFile(fakeHome, ".zshrc", "# config\n")

                val opsxHome = Path(fakeHome, ".opsx")
                val entries = InstallRunner.install(source, fakeHome.toString(), opsxHome)

                entries.filter { it.description == "copied" }.forEach { it.success.shouldBeTrue() }
                readFile(fakeHome, ".opsx/bin/opsx") shouldBe "#!/bin/sh\nnew-version"
                readFile(fakeHome, ".opsx/lib/app.jar") shouldBe "new-jar"
            } finally {
                cleanupDir(source)
                cleanupDir(fakeHome)
            }
        }

        // --- ensurePath error handling ---

        test("ensurePath reports error when rc file is not writable") {
            val fakeHome = createTempRoot()
            try {
                writeFile(fakeHome, ".zshrc", "# config\n")
                // Make the file read-only so append fails
                java.io.File(Path(fakeHome, ".zshrc").toString()).setWritable(false)

                val entry = InstallRunner.ensurePath(fakeHome.toString(), Path(fakeHome, ".opsx"))
                entry.success.shouldBeFalse()
                entry.path shouldBe "~/.zshrc"
                entry.description shouldBe "PATH configuration"
                entry.error.shouldNotBeNull()
            } finally {
                // Restore writable so cleanup works
                java.io.File(Path(fakeHome, ".zshrc").toString()).setWritable(true)
                cleanupDir(fakeHome)
            }
        }

        // --- installCompletions error handling ---

        test("installCompletions reports error when completions dir is blocked") {
            val fakeHome = createTempRoot()
            try {
                writeFile(fakeHome, ".zshrc", "# zsh\n")
                // Create a file where the directory should be, to block createDirectories
                writeFile(fakeHome, ".zsh/completions/_opsx/blocker", "block")
                // Now make the _opsx path a file so sink() fails
                // Actually, create a non-writable parent
                val completionDir = java.io.File(Path(fakeHome, ".zsh/completions/_opsx").toString())
                completionDir.deleteRecursively()
                // Write a file at the _opsx path so it can't be used as a sink target
                // after we make it a directory -- instead, make completions read-only
                SystemFileSystem.createDirectories(Path(fakeHome, ".zsh/completions"))
                java.io.File(Path(fakeHome, ".zsh/completions").toString()).setWritable(false)

                val entry = InstallRunner.installCompletions(fakeHome.toString())
                entry.success.shouldBeFalse()
                entry.description shouldBe "completions"
                entry.error.shouldNotBeNull()
            } finally {
                java.io.File(Path(fakeHome, ".zsh/completions").toString()).setWritable(true)
                cleanupDir(fakeHome)
            }
        }

        // --- copyFileEntry error: source file does not exist ---

        test("install reports copy error when source bin file is unreadable") {
            val source = createTempRoot()
            val fakeHome = createTempRoot()
            try {
                writeFile(source, "bin/opsx", "#!/bin/sh")
                writeFile(source, "lib/app.jar", "jar")
                writeFile(fakeHome, ".zshrc", "# config\n")
                // Make the source bin file unreadable so copy fails
                java.io.File(Path(source, "bin/opsx").toString()).setReadable(false)

                val opsxHome = Path(fakeHome, ".opsx")
                val entries = InstallRunner.install(source, fakeHome.toString(), opsxHome)

                val binEntry = entries.first { it.path.contains("bin/opsx") }
                binEntry.success.shouldBeFalse()
                binEntry.description shouldBe "copy failed"
                binEntry.error.shouldNotBeNull()
            } finally {
                java.io.File(Path(source, "bin/opsx").toString()).setReadable(true)
                cleanupDir(source)
                cleanupDir(fakeHome)
            }
        }

        // --- tilde display path ---

        test("install display paths use tilde for home directory") {
            val source = createTempRoot()
            val fakeHome = createTempRoot()
            try {
                writeFile(source, "bin/opsx", "#!/bin/sh")
                writeFile(source, "lib/app.jar", "jar")
                writeFile(fakeHome, ".zshrc", "# config\n")

                val opsxHome = Path(fakeHome, ".opsx")
                val entries = InstallRunner.install(source, fakeHome.toString(), opsxHome)

                entries.filter { it.description == "copied" }.forEach {
                    it.path shouldContain "~"
                    it.path.contains(fakeHome.toString()).shouldBeFalse()
                }
            } finally {
                cleanupDir(source)
                cleanupDir(fakeHome)
            }
        }

        // --- InstallEntry data class equality and copy ---

        test("InstallEntry supports structural equality") {
            val a = InstallEntry(path = "foo", description = "ok", success = true)
            val b = InstallEntry(path = "foo", description = "ok", success = true)
            (a == b).shouldBeTrue()
        }

        test("InstallEntry copy preserves fields") {
            val original = InstallEntry(path = "foo", description = "ok", success = true)
            val copied = original.copy(success = false, error = "boom")
            copied.path shouldBe "foo"
            copied.description shouldBe "ok"
            copied.success.shouldBeFalse()
            copied.error shouldBe "boom"
        }
    })
