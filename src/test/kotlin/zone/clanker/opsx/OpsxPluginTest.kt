package zone.clanker.opsx

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testfixtures.ProjectBuilder
import java.io.File

class OpsxPluginTest :
    BehaviorSpec({
        given("Opsx constants") {
            then("GROUP is opsx") {
                Opsx.GROUP shouldBe "opsx"
            }
            then("EXTENSION_NAME is opsx") {
                Opsx.EXTENSION_NAME shouldBe "opsx"
            }
            then("OUTPUT_DIR is opsx") {
                Opsx.OUTPUT_DIR shouldBe "opsx"
            }
            then("workflow task names are correct") {
                Opsx.TASK_PROPOSE shouldBe "opsx-propose"
                Opsx.TASK_APPLY shouldBe "opsx-apply"
                Opsx.TASK_VERIFY shouldBe "opsx-verify"
                Opsx.TASK_ARCHIVE shouldBe "opsx-archive"
                Opsx.TASK_CONTINUE shouldBe "opsx-continue"
                Opsx.TASK_EXPLORE shouldBe "opsx-explore"
                Opsx.TASK_FEEDBACK shouldBe "opsx-feedback"
                Opsx.TASK_ONBOARD shouldBe "opsx-onboard"
                Opsx.TASK_FF shouldBe "opsx-ff"
                Opsx.TASK_BULK_ARCHIVE shouldBe "opsx-bulk-archive"
            }
            then("infrastructure task names are correct") {
                Opsx.TASK_SYNC shouldBe "opsx-sync"
                Opsx.TASK_STATUS shouldBe "opsx-status"
                Opsx.TASK_LIST shouldBe "opsx-list"
            }
        }

        given("Opsx.SettingsExtension") {
            val ext = Opsx.SettingsExtension()

            `when`("created with defaults") {
                then("it has correct defaults") {
                    ext.outputDir shouldBe "opsx"
                    ext.defaultAgent shouldBe "claude"
                    ext.specsDir shouldBe "specs"
                    ext.changesDir shouldBe "changes"
                    ext.projectFile shouldBe "project.md"
                }
            }

            `when`("properties are set") {
                then("outputDir is mutable") {
                    ext.outputDir = "custom-output"
                    ext.outputDir shouldBe "custom-output"
                }
                then("defaultAgent is mutable") {
                    ext.defaultAgent = "copilot"
                    ext.defaultAgent shouldBe "copilot"
                }
                then("specsDir is mutable") {
                    ext.specsDir = "specifications"
                    ext.specsDir shouldBe "specifications"
                }
                then("changesDir is mutable") {
                    ext.changesDir = "proposals"
                    ext.changesDir shouldBe "proposals"
                }
                then("projectFile is mutable") {
                    ext.projectFile = "PROJECT.md"
                    ext.projectFile shouldBe "PROJECT.md"
                }
            }
        }

        given("Opsx.registerTasks") {
            val project = ProjectBuilder.builder().build()
            val ext = project.extensions.create(Opsx.EXTENSION_NAME, Opsx.SettingsExtension::class.java)
            val plugin = Opsx.SettingsPlugin()
            plugin.registerTasks(project, ext)

            `when`("tasks are registered") {
                then("all 13 tasks exist") {
                    project.tasks.findByName(Opsx.TASK_PROPOSE).shouldNotBeNull()
                    project.tasks.findByName(Opsx.TASK_APPLY).shouldNotBeNull()
                    project.tasks.findByName(Opsx.TASK_VERIFY).shouldNotBeNull()
                    project.tasks.findByName(Opsx.TASK_ARCHIVE).shouldNotBeNull()
                    project.tasks.findByName(Opsx.TASK_CONTINUE).shouldNotBeNull()
                    project.tasks.findByName(Opsx.TASK_EXPLORE).shouldNotBeNull()
                    project.tasks.findByName(Opsx.TASK_FEEDBACK).shouldNotBeNull()
                    project.tasks.findByName(Opsx.TASK_ONBOARD).shouldNotBeNull()
                    project.tasks.findByName(Opsx.TASK_FF).shouldNotBeNull()
                    project.tasks.findByName(Opsx.TASK_BULK_ARCHIVE).shouldNotBeNull()
                    project.tasks.findByName(Opsx.TASK_SYNC).shouldNotBeNull()
                    project.tasks.findByName(Opsx.TASK_STATUS).shouldNotBeNull()
                    project.tasks.findByName(Opsx.TASK_LIST).shouldNotBeNull()
                }

                then("all tasks have group opsx") {
                    project.tasks.findByName(Opsx.TASK_PROPOSE)!!.group shouldBe Opsx.GROUP
                    project.tasks.findByName(Opsx.TASK_APPLY)!!.group shouldBe Opsx.GROUP
                    project.tasks.findByName(Opsx.TASK_VERIFY)!!.group shouldBe Opsx.GROUP
                    project.tasks.findByName(Opsx.TASK_ARCHIVE)!!.group shouldBe Opsx.GROUP
                    project.tasks.findByName(Opsx.TASK_CONTINUE)!!.group shouldBe Opsx.GROUP
                    project.tasks.findByName(Opsx.TASK_EXPLORE)!!.group shouldBe Opsx.GROUP
                    project.tasks.findByName(Opsx.TASK_FEEDBACK)!!.group shouldBe Opsx.GROUP
                    project.tasks.findByName(Opsx.TASK_ONBOARD)!!.group shouldBe Opsx.GROUP
                    project.tasks.findByName(Opsx.TASK_FF)!!.group shouldBe Opsx.GROUP
                    project.tasks.findByName(Opsx.TASK_BULK_ARCHIVE)!!.group shouldBe Opsx.GROUP
                    project.tasks.findByName(Opsx.TASK_SYNC)!!.group shouldBe Opsx.GROUP
                    project.tasks.findByName(Opsx.TASK_STATUS)!!.group shouldBe Opsx.GROUP
                    project.tasks.findByName(Opsx.TASK_LIST)!!.group shouldBe Opsx.GROUP
                }

                then("tasks have descriptions") {
                    project.tasks.findByName(Opsx.TASK_PROPOSE)!!.description shouldBe
                        "Propose a new change from a spec"
                    project.tasks.findByName(Opsx.TASK_APPLY)!!.description shouldBe
                        "Apply a change proposal to the codebase"
                    project.tasks.findByName(Opsx.TASK_VERIFY)!!.description shouldBe
                        "Verify a change was applied correctly"
                    project.tasks.findByName(Opsx.TASK_ARCHIVE)!!.description shouldBe
                        "Archive a completed change"
                    project.tasks.findByName(Opsx.TASK_CONTINUE)!!.description shouldBe
                        "Continue work on an in-progress change"
                    project.tasks.findByName(Opsx.TASK_EXPLORE)!!.description shouldBe
                        "Explore the codebase for a change"
                    project.tasks.findByName(Opsx.TASK_FEEDBACK)!!.description shouldBe
                        "Provide feedback on a change"
                    project.tasks.findByName(Opsx.TASK_ONBOARD)!!.description shouldBe
                        "Onboard a new contributor to the project"
                    project.tasks.findByName(Opsx.TASK_FF)!!.description shouldBe
                        "Fast-forward a change to the latest state"
                    project.tasks.findByName(Opsx.TASK_BULK_ARCHIVE)!!.description shouldBe
                        "Archive all completed changes in bulk"
                    project.tasks.findByName(Opsx.TASK_SYNC)!!.description shouldBe
                        "Generate slash commands for all agents"
                    project.tasks.findByName(Opsx.TASK_STATUS)!!.description shouldBe
                        "Show status of all changes"
                    project.tasks.findByName(Opsx.TASK_LIST)!!.description shouldBe
                        "List all changes and their status"
                }
            }
        }

        given("Opsx.SettingsPlugin") {
            `when`("instantiated") {
                then("it is not null") {
                    val plugin = Opsx.SettingsPlugin()
                    plugin.shouldNotBeNull()
                }
            }
        }

        given("Opsx architecture") {
            `when`("checking data object structure") {
                then("Opsx is a data object") {
                    Opsx::class.isData shouldBe true
                    Opsx::class.objectInstance.shouldNotBeNull()
                }
                then("SettingsPlugin is inside Opsx") {
                    Opsx.SettingsPlugin::class.java.enclosingClass shouldBe Opsx::class.java
                }
                then("SettingsExtension is inside Opsx") {
                    Opsx.SettingsExtension::class.java.enclosingClass shouldBe Opsx::class.java
                }
            }
        }

        given("CleanTask.removeMarkers") {
            `when`("file has OPSX markers with content around them") {
                val tempDir =
                    File.createTempFile("opsx-clean", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val claudeMd = File(tempDir, "CLAUDE.md")
                claudeMd.writeText(
                    """
                    |# My Project
                    |
                    |Custom instructions here.
                    |
                    |<!-- OPSX:AUTO -->
                    |# OPSX Workspace
                    |Generated content
                    |<!-- /OPSX:AUTO -->
                    |
                    |# Footer section
                    """.trimMargin(),
                )

                val project =
                    ProjectBuilder
                        .builder()
                        .withProjectDir(tempDir)
                        .build()

                val task = project.tasks.create("clean-test", Opsx.CleanTask::class.java)
                task.run()

                then("removes OPSX marker section") {
                    val content = claudeMd.readText()
                    (content.contains("<!-- OPSX:AUTO -->")) shouldBe false
                    (content.contains("OPSX Workspace")) shouldBe false
                    (content.contains("Generated content")) shouldBe false
                }

                then("preserves content before markers") {
                    val content = claudeMd.readText()
                    content shouldContain "# My Project"
                    content shouldContain "Custom instructions here."
                }

                then("preserves content after markers") {
                    val content = claudeMd.readText()
                    content shouldContain "# Footer section"
                }
            }

            `when`("file contains only OPSX markers") {
                val tempDir =
                    File.createTempFile("opsx-clean-only", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val claudeMd = File(tempDir, "CLAUDE.md")
                claudeMd.writeText(
                    """
                    |<!-- OPSX:AUTO -->
                    |# OPSX Workspace
                    |Generated content
                    |<!-- /OPSX:AUTO -->
                    """.trimMargin(),
                )

                val project =
                    ProjectBuilder
                        .builder()
                        .withProjectDir(tempDir)
                        .build()

                val task = project.tasks.create("clean-test-empty", Opsx.CleanTask::class.java)
                task.run()

                then("deletes the file when empty after removal") {
                    claudeMd.exists() shouldBe false
                }
            }

            `when`("file has no OPSX markers") {
                val tempDir =
                    File.createTempFile("opsx-clean-none", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val claudeMd = File(tempDir, "CLAUDE.md")
                claudeMd.writeText("# My Project\n\nNo markers here.\n")

                val project =
                    ProjectBuilder
                        .builder()
                        .withProjectDir(tempDir)
                        .build()

                val task = project.tasks.create("clean-test-nomarkers", Opsx.CleanTask::class.java)
                task.run()

                then("leaves file unchanged") {
                    val content = claudeMd.readText()
                    content shouldBe "# My Project\n\nNo markers here.\n"
                }
            }

            `when`("generated directories exist") {
                val tempDir =
                    File.createTempFile("opsx-clean-dirs", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val claudeCommands = File(tempDir, ".claude/commands")
                claudeCommands.mkdirs()
                File(claudeCommands, "opsx-list.md").writeText("# opsx-list")
                File(claudeCommands, "opsx-sync.md").writeText("# opsx-sync")

                val githubPrompts = File(tempDir, ".github/prompts")
                githubPrompts.mkdirs()
                File(githubPrompts, "opsx-list.md").writeText("# opsx-list")

                val project =
                    ProjectBuilder
                        .builder()
                        .withProjectDir(tempDir)
                        .build()

                val task = project.tasks.create("clean-test-dirs", Opsx.CleanTask::class.java)
                task.run()

                then("deletes .claude/commands directory") {
                    claudeCommands.exists() shouldBe false
                }

                then("deletes .github/prompts directory") {
                    githubPrompts.exists() shouldBe false
                }
            }

            `when`("nothing to clean") {
                val tempDir =
                    File.createTempFile("opsx-clean-empty", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val project =
                    ProjectBuilder
                        .builder()
                        .withProjectDir(tempDir)
                        .build()

                then("does not throw") {
                    val task = project.tasks.create("clean-test-noop", Opsx.CleanTask::class.java)
                    task.run()
                    // no exception = pass
                }
            }

            `when`("both AGENTS.md and CLAUDE.md have markers") {
                val tempDir =
                    File.createTempFile("opsx-clean-both", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val claudeMd = File(tempDir, "CLAUDE.md")
                claudeMd.writeText(
                    "# Project\n\n<!-- OPSX:AUTO -->\ngenerated\n<!-- /OPSX:AUTO -->\n",
                )

                val agentsMd = File(tempDir, "AGENTS.md")
                agentsMd.writeText(
                    "# Agents\n\n<!-- OPSX:AUTO -->\ngenerated\n<!-- /OPSX:AUTO -->\n",
                )

                val project =
                    ProjectBuilder
                        .builder()
                        .withProjectDir(tempDir)
                        .build()

                val task = project.tasks.create("clean-test-both", Opsx.CleanTask::class.java)
                task.run()

                then("removes markers from CLAUDE.md") {
                    val content = claudeMd.readText()
                    content shouldContain "# Project"
                    (content.contains("<!-- OPSX:AUTO -->")) shouldBe false
                }

                then("removes markers from AGENTS.md") {
                    val content = agentsMd.readText()
                    content shouldContain "# Agents"
                    (content.contains("<!-- OPSX:AUTO -->")) shouldBe false
                }
            }
        }

        given("Opsx.TASK_CLEAN constant") {
            then("value is opsx-clean") {
                Opsx.TASK_CLEAN shouldBe "opsx-clean"
            }
        }
    })
