package zone.clanker.opsx

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testfixtures.ProjectBuilder
import zone.clanker.opsx.model.Agent
import zone.clanker.opsx.task.CleanTask
import zone.clanker.opsx.task.ListTask
import zone.clanker.opsx.task.ProposeTask
import zone.clanker.opsx.task.StatusTask
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
            then("property names are namespaced") {
                Opsx.PROP_PROMPT shouldBe "zone.clanker.opsx.prompt"
                Opsx.PROP_SPEC shouldBe "zone.clanker.opsx.spec"
                Opsx.PROP_CHANGE shouldBe "zone.clanker.opsx.change"
                Opsx.PROP_CHANGE_NAME shouldBe "zone.clanker.opsx.changeName"
                Opsx.PROP_AGENT shouldBe "zone.clanker.opsx.agent"
                Opsx.PROP_MODEL shouldBe "zone.clanker.opsx.model"
            }
            then("infrastructure task names are correct") {
                Opsx.TASK_SYNC shouldBe "opsx-sync"
                Opsx.TASK_CLEAN shouldBe "opsx-clean"
                Opsx.TASK_STATUS shouldBe "opsx-status"
                Opsx.TASK_LIST shouldBe "opsx-list"
            }
        }

        given("Opsx.SettingsExtension") {
            val ext = Opsx.SettingsExtension()

            `when`("created with defaults") {
                then("it has correct defaults") {
                    ext.outputDir shouldBe "opsx"
                    ext.agents shouldBe mutableListOf()
                    ext.skillDirectories shouldBe mutableListOf()
                    ext.agentDirectories shouldBe mutableListOf()
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
                then("agents is assignable") {
                    ext.agents = listOf(Agent.COPILOT)
                    ext.agents shouldContain Agent.COPILOT
                }
                then("skillDirectories is assignable") {
                    ext.skillDirectories = listOf("custom-skills")
                    ext.skillDirectories shouldContain "custom-skills"
                }
                then("agentDirectories is assignable") {
                    ext.agentDirectories = listOf("custom-agents")
                    ext.agentDirectories shouldContain "custom-agents"
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
                then("all 14 tasks exist") {
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
                    project.tasks.findByName(Opsx.TASK_CLEAN).shouldNotBeNull()
                    project.tasks.findByName(Opsx.TASK_LIST).shouldNotBeNull()
                }

                then("all tasks have group opsx") {
                    val allTasks =
                        listOf(
                            Opsx.TASK_PROPOSE,
                            Opsx.TASK_APPLY,
                            Opsx.TASK_VERIFY,
                            Opsx.TASK_ARCHIVE,
                            Opsx.TASK_CONTINUE,
                            Opsx.TASK_EXPLORE,
                            Opsx.TASK_FEEDBACK,
                            Opsx.TASK_ONBOARD,
                            Opsx.TASK_FF,
                            Opsx.TASK_BULK_ARCHIVE,
                            Opsx.TASK_STATUS,
                            Opsx.TASK_SYNC,
                            Opsx.TASK_CLEAN,
                            Opsx.TASK_LIST,
                        )
                    allTasks.forEach { taskName ->
                        project.tasks.findByName(taskName)!!.group shouldBe Opsx.GROUP
                    }
                }

                then("all tasks have descriptions") {
                    val allTasks =
                        listOf(
                            Opsx.TASK_PROPOSE,
                            Opsx.TASK_APPLY,
                            Opsx.TASK_VERIFY,
                            Opsx.TASK_ARCHIVE,
                            Opsx.TASK_CONTINUE,
                            Opsx.TASK_EXPLORE,
                            Opsx.TASK_FEEDBACK,
                            Opsx.TASK_ONBOARD,
                            Opsx.TASK_FF,
                            Opsx.TASK_BULK_ARCHIVE,
                            Opsx.TASK_STATUS,
                            Opsx.TASK_SYNC,
                            Opsx.TASK_CLEAN,
                            Opsx.TASK_LIST,
                        )
                    allTasks.forEach { taskName ->
                        val desc = project.tasks.findByName(taskName)!!.description
                        (desc != null && desc.isNotBlank()) shouldBe true
                    }
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

                val task = project.tasks.create("clean-test", CleanTask::class.java)
                task.rootDir.set(tempDir)
                task.includedBuildDirs.set(emptyList())
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

                val task = project.tasks.create("clean-test-empty", CleanTask::class.java)
                task.rootDir.set(tempDir)
                task.includedBuildDirs.set(emptyList())
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

                val task = project.tasks.create("clean-test-nomarkers", CleanTask::class.java)
                task.rootDir.set(tempDir)
                task.includedBuildDirs.set(emptyList())
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

                val origHome = System.getProperty("user.home")
                System.setProperty("user.home", tempDir.absolutePath)

                val clkxSkills = File(tempDir, ".clkx/skills")
                clkxSkills.mkdirs()
                val sourceFile = File(clkxSkills, "opsx-list.md")
                sourceFile.writeText("# opsx-list")

                val claudeCommands = File(tempDir, ".claude/commands")
                claudeCommands.mkdirs()
                java.nio.file.Files.createSymbolicLink(
                    File(claudeCommands, "opsx-list.md").toPath(),
                    sourceFile.toPath(),
                )
                // Also add a user-owned file that should NOT be deleted
                File(claudeCommands, "my-custom.md").writeText("# custom")

                val project =
                    ProjectBuilder
                        .builder()
                        .withProjectDir(tempDir)
                        .build()

                val task = project.tasks.create("clean-test-dirs", CleanTask::class.java)
                task.rootDir.set(tempDir)
                task.includedBuildDirs.set(emptyList())
                task.run()

                System.setProperty("user.home", origHome)

                then("deletes .clkx/skills directory") {
                    clkxSkills.exists() shouldBe false
                }

                then("removes opsx symlinks from .claude/commands") {
                    File(claudeCommands, "opsx-list.md").exists() shouldBe false
                }

                then("preserves user-owned files in .claude/commands") {
                    File(claudeCommands, "my-custom.md").exists() shouldBe true
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
                    val task = project.tasks.create("clean-test-noop", CleanTask::class.java)
                    task.rootDir.set(tempDir)
                    task.includedBuildDirs.set(emptyList())
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

                val task = project.tasks.create("clean-test-both", CleanTask::class.java)
                task.rootDir.set(tempDir)
                task.includedBuildDirs.set(emptyList())
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

        given("ListTask @TaskAction") {
            `when`("no changes exist") {
                val projectDir =
                    File.createTempFile("opsx-inner-list", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-inner-list", ListTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(Opsx.SettingsExtension().toOpsxConfig())

                then("runs without error reporting no changes") {
                    task.run()
                }
            }

            `when`("changes exist") {
                val projectDir =
                    File.createTempFile("opsx-inner-list2", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changeDir = File(projectDir, "opsx/changes/inner-list-change")
                changeDir.mkdirs()
                zone.clanker.opsx.model.ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    zone.clanker.opsx.model
                        .ChangeConfig(name = "inner-list-change", status = "active"),
                )

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-inner-list2", ListTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(Opsx.SettingsExtension().toOpsxConfig())

                then("lists the changes") {
                    task.run()
                }
            }
        }

        given("StatusTask @TaskAction") {
            `when`("no changes exist") {
                val projectDir =
                    File.createTempFile("opsx-inner-status", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-inner-status", StatusTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(Opsx.SettingsExtension().toOpsxConfig())

                then("runs without error") {
                    task.run()
                }
            }

            `when`("changes with artifacts exist") {
                val projectDir =
                    File.createTempFile("opsx-inner-status2", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changeDir = File(projectDir, "opsx/changes/status-change")
                changeDir.mkdirs()
                zone.clanker.opsx.model.ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    zone.clanker.opsx.model.ChangeConfig(
                        name = "status-change",
                        status = "active",
                        depends = listOf("dep-a"),
                    ),
                )
                File(changeDir, "proposal.md").writeText("proposal")
                File(changeDir, "design.md").writeText("design")
                File(changeDir, "tasks.md").writeText("# Tasks\n- [x] done\n")

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-inner-status2", StatusTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(Opsx.SettingsExtension().toOpsxConfig())

                then("shows status with artifacts and deps") {
                    task.run()
                }
            }
        }

        given("Opsx.StubTask @TaskAction") {
            `when`("run is called") {
                val projectDir =
                    File.createTempFile("opsx-inner-stub", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-inner-stub", Opsx.StubTask::class.java)

                then("logs the default message") {
                    task.run()
                }
            }

            `when`("custom message is set") {
                val projectDir =
                    File.createTempFile("opsx-inner-stub2", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-inner-stub2", Opsx.StubTask::class.java)
                task.taskMessage = "custom message"

                then("logs the custom message") {
                    task.run()
                    task.taskMessage shouldBe "custom message"
                }
            }
        }

        given("Dispatch agent resolution") {
            `when`("extension.agents is set") {
                val project = ProjectBuilder.builder().build()
                val ext = project.extensions.create(Opsx.EXTENSION_NAME, Opsx.SettingsExtension::class.java)
                ext.agents = listOf(Agent.COPILOT, Agent.CLAUDE)
                val plugin = Opsx.SettingsPlugin()
                plugin.registerTasks(project, ext)

                then("agentProp resolves to first element of extension.agents") {
                    val proposeTask = project.tasks.findByName(Opsx.TASK_PROPOSE) as zone.clanker.opsx.task.ProposeTask
                    proposeTask.agent.get() shouldBe "copilot"
                }
            }
        }
    })
