package zone.clanker.opsx.task

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import zone.clanker.opsx.Opsx
import java.io.File
import java.nio.file.Files

private fun createExtension(): Opsx.SettingsExtension = Opsx.SettingsExtension()

class SyncTaskTest :
    BehaviorSpec({

        given("SyncTask.isOpsxSymlink (via reflection)") {

            `when`("file is a symlink pointing into sourceDir") {
                val tempDir =
                    File.createTempFile("opsx-sync", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val sourceDir = File(tempDir, "source")
                sourceDir.mkdirs()
                val sourceFile = File(sourceDir, "test.md")
                sourceFile.writeText("content")

                val linkDir = File(tempDir, "links")
                linkDir.mkdirs()
                val link = File(linkDir, "test.md")
                Files.createSymbolicLink(link.toPath(), sourceFile.toPath())

                then("returns true") {
                    val result = invokeIsOpsxSymlink(link, sourceDir)
                    result shouldBe true
                }
            }

            `when`("file is a symlink pointing outside sourceDir") {
                val tempDir =
                    File.createTempFile("opsx-sync", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val sourceDir = File(tempDir, "source")
                sourceDir.mkdirs()
                val otherDir = File(tempDir, "other")
                otherDir.mkdirs()
                val otherFile = File(otherDir, "test.md")
                otherFile.writeText("content")

                val linkDir = File(tempDir, "links")
                linkDir.mkdirs()
                val link = File(linkDir, "test.md")
                Files.createSymbolicLink(link.toPath(), otherFile.toPath())

                then("returns false") {
                    val result = invokeIsOpsxSymlink(link, sourceDir)
                    result shouldBe false
                }
            }

            `when`("file is a regular file, not a symlink") {
                val tempDir =
                    File.createTempFile("opsx-sync", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val sourceDir = File(tempDir, "source")
                sourceDir.mkdirs()
                val regularFile = File(tempDir, "regular.md")
                regularFile.writeText("not a symlink")

                then("returns false") {
                    val result = invokeIsOpsxSymlink(regularFile, sourceDir)
                    result shouldBe false
                }
            }

            `when`("file does not exist") {
                val tempDir =
                    File.createTempFile("opsx-sync", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val sourceDir = File(tempDir, "source")
                sourceDir.mkdirs()
                val missing = File(tempDir, "missing.md")

                then("returns false") {
                    val result = invokeIsOpsxSymlink(missing, sourceDir)
                    result shouldBe false
                }
            }
        }

        given("SyncTask gitignore generation") {

            `when`("sync runs") {
                val tempDir =
                    File.createTempFile("opsx-gitignore", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val skillsDir = File(tempDir, ".clkx/skills")
                skillsDir.mkdirs()
                val agentDir = File(tempDir, ".claude/commands")
                agentDir.mkdirs()

                // Invoke private methods via reflection
                val project =
                    org.gradle.testfixtures.ProjectBuilder
                        .builder()
                        .withProjectDir(tempDir)
                        .build()
                val task = project.tasks.create("gitignore-sync", SyncTask::class.java)
                task.extension = createExtension()

                val writeMethod =
                    SyncTask::class.java.getDeclaredMethod(
                        "writeGitignore",
                        File::class.java,
                        String::class.java,
                    )
                writeMethod.isAccessible = true

                val ensureMethod =
                    SyncTask::class.java.getDeclaredMethod(
                        "ensureOpsxIgnorePattern",
                        File::class.java,
                    )
                ensureMethod.isAccessible = true

                then("writeGitignore creates .gitignore with content") {
                    writeMethod.invoke(task, skillsDir, "*\n")
                    val gitignore = File(skillsDir, ".gitignore")
                    gitignore.exists() shouldBe true
                    val content = gitignore.readText()
                    content shouldContain "Generated by opsx"
                    content shouldContain "*"
                }

                then("ensureOpsxIgnorePattern creates .gitignore with opsx-* pattern") {
                    ensureMethod.invoke(task, agentDir)
                    val gitignore = File(agentDir, ".gitignore")
                    gitignore.exists() shouldBe true
                    val content = gitignore.readText()
                    content shouldContain "opsx-*"
                }

                then("ensureOpsxIgnorePattern appends to existing .gitignore") {
                    val gitignore = File(agentDir, ".gitignore")
                    gitignore.writeText("my-custom-rule\n")
                    ensureMethod.invoke(task, agentDir)
                    val content = gitignore.readText()
                    content shouldContain "my-custom-rule"
                    content shouldContain "opsx-*"
                }

                then("ensureOpsxIgnorePattern does not duplicate pattern") {
                    val gitignore = File(agentDir, ".gitignore")
                    gitignore.writeText("existing\nopsx-*\n")
                    ensureMethod.invoke(task, agentDir)
                    val content = gitignore.readText()
                    content.split("opsx-*").size shouldBe 2 // appears once
                }
            }
        }
    }) {
    companion object {
        /**
         * Uses reflection to test the private isOpsxSymlink method.
         * This is a private method on an abstract Gradle task, so we use
         * reflection to verify its logic without running the full task.
         */
        fun invokeIsOpsxSymlink(
            file: File,
            sourceDir: File,
        ): Boolean {
            val method =
                SyncTask::class.java.getDeclaredMethod(
                    "isOpsxSymlink",
                    File::class.java,
                    File::class.java,
                )
            method.isAccessible = true
            // We need an instance; use a Gradle project to create one
            val projectDir =
                File.createTempFile("opsx-sync-reflect", "").apply {
                    delete()
                    mkdirs()
                    deleteOnExit()
                }
            val project =
                org.gradle.testfixtures.ProjectBuilder
                    .builder()
                    .withProjectDir(projectDir)
                    .build()
            val task = project.tasks.create("reflect-sync", SyncTask::class.java)
            task.extension = createExtension()
            return method.invoke(task, file, sourceDir) as Boolean
        }

        private fun createExtension(): Opsx.SettingsExtension = Opsx.SettingsExtension()
    }
}
