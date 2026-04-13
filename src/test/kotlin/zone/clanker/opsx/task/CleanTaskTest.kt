package zone.clanker.opsx.task

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testfixtures.ProjectBuilder
import zone.clanker.opsx.skill.SkillGenerator
import java.io.File
import java.nio.file.Files

class CleanTaskTest :
    BehaviorSpec({

        given("CleanTask.run") {

            `when`("nothing to clean") {
                val projectDir =
                    File.createTempFile("opsx-clean", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-clean", CleanTask::class.java)

                then("completes without error") {
                    task.run()
                }
            }

            `when`("skills directory exists with md files") {
                val tempDir =
                    File.createTempFile("opsx-clean-skills", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val origHome = System.getProperty("user.home")
                System.setProperty("user.home", tempDir.absolutePath)

                val skillsDir = File(tempDir, SkillGenerator.SKILLS_DIR)
                skillsDir.mkdirs()
                File(skillsDir, "opsx-list.md").writeText("# opsx-list")
                File(skillsDir, "opsx-sync.md").writeText("# opsx-sync")

                val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
                val task = project.tasks.create("test-clean-skills", CleanTask::class.java)
                task.run()

                System.setProperty("user.home", origHome)

                then("deletes skills directory") {
                    skillsDir.exists() shouldBe false
                }
            }

            `when`("agent target directories have symlinks pointing to source dir") {
                val tempDir =
                    File.createTempFile("opsx-clean-symlinks", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val origHome = System.getProperty("user.home")
                System.setProperty("user.home", tempDir.absolutePath)

                val skillsDir = File(tempDir, SkillGenerator.SKILLS_DIR)
                skillsDir.mkdirs()
                val sourceFile = File(skillsDir, "opsx-test.md")
                sourceFile.writeText("# opsx-test")

                val claudeDir = File(tempDir, ".claude/commands")
                claudeDir.mkdirs()
                Files.createSymbolicLink(
                    File(claudeDir, "opsx-test.md").toPath(),
                    sourceFile.toPath(),
                )
                // Also create a user-owned file that should survive
                File(claudeDir, "my-custom.md").writeText("# custom")

                val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
                val task = project.tasks.create("test-clean-links", CleanTask::class.java)
                task.run()

                System.setProperty("user.home", origHome)

                then("removes symlinks from agent dirs") {
                    File(claudeDir, "opsx-test.md").exists() shouldBe false
                }

                then("preserves non-symlink files in agent dirs") {
                    File(claudeDir, "my-custom.md").exists() shouldBe true
                }
            }

            `when`("broken symlinks exist in agent dirs") {
                val tempDir =
                    File.createTempFile("opsx-clean-broken", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val origHome = System.getProperty("user.home")
                System.setProperty("user.home", tempDir.absolutePath)

                val claudeDir = File(tempDir, ".claude/commands")
                claudeDir.mkdirs()
                val nonexistentTarget = File(tempDir, "nonexistent/target.md")
                Files.createSymbolicLink(
                    File(claudeDir, "broken-link.md").toPath(),
                    nonexistentTarget.toPath(),
                )

                val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
                val task = project.tasks.create("test-clean-broken", CleanTask::class.java)
                task.run()

                System.setProperty("user.home", origHome)

                then("removes broken symlinks") {
                    File(claudeDir, "broken-link.md").exists() shouldBe false
                    // Even the symlink itself should be gone
                    Files.exists(File(claudeDir, "broken-link.md").toPath()) shouldBe false
                }
            }

            `when`("instruction files have OPSX markers") {
                val tempDir =
                    File.createTempFile("opsx-clean-instr", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val claudeMd = File(tempDir, "CLAUDE.md")
                claudeMd.writeText(
                    "# My Project\n\n<!-- OPSX:AUTO -->\ngenerated\n<!-- /OPSX:AUTO -->\n\n# Footer\n",
                )
                val agentsMd = File(tempDir, "AGENTS.md")
                agentsMd.writeText(
                    "<!-- OPSX:AUTO -->\nonly opsx\n<!-- /OPSX:AUTO -->",
                )

                val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
                val task = project.tasks.create("test-clean-markers", CleanTask::class.java)
                task.run()

                then("removes OPSX sections from files preserving other content") {
                    val content = claudeMd.readText()
                    (content.contains("<!-- OPSX:AUTO -->")) shouldBe false
                    content shouldContain "# My Project"
                    content shouldContain "# Footer"
                }

                then("deletes files that become empty after marker removal") {
                    agentsMd.exists() shouldBe false
                }
            }

            `when`(".srcx directory exists at project root") {
                val tempDir =
                    File.createTempFile("opsx-clean-srcx", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val srcxDir = File(tempDir, ".srcx")
                srcxDir.mkdirs()
                File(srcxDir, "context.md").writeText("context")
                File(srcxDir, "other.md").writeText("other")

                val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
                val task = project.tasks.create("test-clean-srcx", CleanTask::class.java)
                task.run()

                then("deletes .srcx directory") {
                    srcxDir.exists() shouldBe false
                }
            }

            `when`("instruction file has no markers") {
                val tempDir =
                    File.createTempFile("opsx-clean-nomark", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val claudeMd = File(tempDir, "CLAUDE.md")
                claudeMd.writeText("# Plain file\n\nNo markers here.\n")

                val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
                val task = project.tasks.create("test-clean-nomark", CleanTask::class.java)
                task.run()

                then("leaves file unchanged") {
                    claudeMd.readText() shouldBe "# Plain file\n\nNo markers here.\n"
                }
            }
        }
    })
