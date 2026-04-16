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
                task.rootDir.set(projectDir)
                task.includedBuildDirs.set(emptyList())

                then("completes without error") {
                    task.run()
                }
            }

            `when`("skills directory exists with skill subdirs") {
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
                File(skillsDir, "opsx-list").mkdirs()
                File(skillsDir, "opsx-list/SKILL.md").writeText("# opsx-list")
                File(skillsDir, "opsx-sync").mkdirs()
                File(skillsDir, "opsx-sync/SKILL.md").writeText("# opsx-sync")

                val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
                val task = project.tasks.create("test-clean-skills", CleanTask::class.java)
                task.rootDir.set(tempDir)
                task.includedBuildDirs.set(emptyList())
                task.run()

                System.setProperty("user.home", origHome)

                then("deletes skills directory") {
                    skillsDir.exists() shouldBe false
                }
            }

            `when`("agent target directories have directory symlinks pointing to source dir") {
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
                val sourceSkillDir = File(skillsDir, "opsx-test")
                sourceSkillDir.mkdirs()
                File(sourceSkillDir, "SKILL.md").writeText("# opsx-test")

                val claudeDir = File(tempDir, ".claude/skills")
                claudeDir.mkdirs()
                Files.createSymbolicLink(
                    File(claudeDir, "opsx-test").toPath(),
                    sourceSkillDir.toPath(),
                )
                // Also create a user-owned dir that should survive
                File(claudeDir, "my-custom").mkdirs()
                File(claudeDir, "my-custom/SKILL.md").writeText("# custom")

                // Also create symlink in shared .agents/skills/
                val sharedDir = File(tempDir, ".agents/skills")
                sharedDir.mkdirs()
                Files.createSymbolicLink(
                    File(sharedDir, "opsx-test").toPath(),
                    sourceSkillDir.toPath(),
                )

                val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
                val task = project.tasks.create("test-clean-links", CleanTask::class.java)
                task.rootDir.set(tempDir)
                task.includedBuildDirs.set(emptyList())
                task.run()

                System.setProperty("user.home", origHome)

                then("removes directory symlinks from agent dirs") {
                    Files.exists(File(claudeDir, "opsx-test").toPath()) shouldBe false
                }

                then("removes directory symlinks from shared skills dir") {
                    Files.exists(File(sharedDir, "opsx-test").toPath()) shouldBe false
                }

                then("preserves non-symlink dirs in agent dirs") {
                    File(claudeDir, "my-custom/SKILL.md").exists() shouldBe true
                }
            }

            `when`("broken directory symlinks exist in agent dirs") {
                val tempDir =
                    File.createTempFile("opsx-clean-broken", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val origHome = System.getProperty("user.home")
                System.setProperty("user.home", tempDir.absolutePath)

                val claudeDir = File(tempDir, ".claude/skills")
                claudeDir.mkdirs()
                val nonexistentTarget = File(tempDir, "nonexistent/target-skill")
                Files.createSymbolicLink(
                    File(claudeDir, "broken-link").toPath(),
                    nonexistentTarget.toPath(),
                )

                val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
                val task = project.tasks.create("test-clean-broken", CleanTask::class.java)
                task.rootDir.set(tempDir)
                task.includedBuildDirs.set(emptyList())
                task.run()

                System.setProperty("user.home", origHome)

                then("removes broken directory symlinks") {
                    // Even the symlink itself should be gone
                    Files.exists(File(claudeDir, "broken-link").toPath()) shouldBe false
                }
            }

            `when`("agent definitions exist at project and home level") {
                val tempDir =
                    File.createTempFile("opsx-clean-agentdefs", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val origHome = System.getProperty("user.home")
                System.setProperty("user.home", tempDir.absolutePath)

                // Create ~/.clkx/agents/opsx.md (source of truth)
                val agentsDir = File(tempDir, SkillGenerator.AGENTS_DIR)
                agentsDir.mkdirs()
                val agentSource = File(agentsDir, "opsx.md")
                agentSource.writeText("# opsx agent")

                // Create project-level symlink .claude/agents/opsx.md -> ~/.clkx/agents/opsx.md
                val claudeAgentsDir = File(tempDir, ".claude/agents")
                claudeAgentsDir.mkdirs()
                Files.createSymbolicLink(
                    File(claudeAgentsDir, "opsx.md").toPath(),
                    agentSource.toPath(),
                )

                val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
                val task = project.tasks.create("test-clean-agentdefs", CleanTask::class.java)
                task.rootDir.set(tempDir)
                task.includedBuildDirs.set(emptyList())
                task.run()

                System.setProperty("user.home", origHome)

                then("deletes ~/.clkx/agents/ directory") {
                    agentsDir.exists() shouldBe false
                }

                then("removes project-level agent definition symlink") {
                    Files.exists(File(claudeAgentsDir, "opsx.md").toPath()) shouldBe false
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
                task.rootDir.set(tempDir)
                task.includedBuildDirs.set(emptyList())
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
                task.rootDir.set(tempDir)
                task.includedBuildDirs.set(emptyList())
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
                task.rootDir.set(tempDir)
                task.includedBuildDirs.set(emptyList())
                task.run()

                then("leaves file unchanged") {
                    claudeMd.readText() shouldBe "# Plain file\n\nNo markers here.\n"
                }
            }
        }
    })
