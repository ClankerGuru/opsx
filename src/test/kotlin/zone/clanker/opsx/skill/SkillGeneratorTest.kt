package zone.clanker.opsx.skill

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Files

class SkillGeneratorTest :
    BehaviorSpec({
        given("SkillGenerator.discoverTasks") {
            `when`("project has tasks in tracked groups") {
                val project = ProjectBuilder.builder().build()
                project.tasks.register("opsx-list") {
                    it.group = "opsx"
                    it.description = "List all changes"
                }
                project.tasks.register("some-other-task") {
                    it.group = "build"
                    it.description = "Not tracked"
                }

                val generator = SkillGenerator(project)

                then("discovers only tracked group tasks") {
                    val tasks = generator.discoverTasks()
                    tasks.map { it.name } shouldContain "opsx-list"
                    tasks.none { it.name == "some-other-task" } shouldBe true
                }
            }

            `when`("project has no tasks in tracked groups") {
                val project = ProjectBuilder.builder().build()
                val generator = SkillGenerator(project)

                then("returns empty list") {
                    val tasks = generator.discoverTasks()
                    tasks.none { it.group in SkillGenerator.TRACKED_GROUPS } shouldBe true
                }
            }
        }

        given("SkillGenerator.buildCommandFile") {
            val project = ProjectBuilder.builder().build()
            val generator = SkillGenerator(project)
            val task = TaskInfo("opsx-sync", "opsx", "Generate slash commands for all agents")

            `when`("building command file content") {
                val content = generator.buildCommandFile(task, emptyList())

                then("contains task name as heading") {
                    content shouldContain "# opsx-sync"
                }

                then("contains description") {
                    content shouldContain "Generate slash commands for all agents"
                }

                then("contains usage with gradlew") {
                    content shouldContain "./gradlew -q opsx-sync"
                }

                then("contains group") {
                    content shouldContain "opsx"
                }

                then("contains notes for known tasks") {
                    content shouldContain "## Notes"
                }
            }
        }

        given("SkillGenerator.generateSkillFiles") {
            `when`("generating skills to ~/.clkx/skills with symlinks") {
                val tempDir =
                    File.createTempFile("opsx-gen", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val origHome = System.getProperty("user.home")
                System.setProperty("user.home", tempDir.absolutePath)

                val project =
                    ProjectBuilder
                        .builder()
                        .withProjectDir(tempDir)
                        .build()

                val generator = SkillGenerator(project)
                val tasks =
                    listOf(
                        TaskInfo("opsx-list", "opsx", "List changes"),
                        TaskInfo("opsx-sync", "opsx", "Sync skills"),
                    )

                generator.generateSkillFiles(tasks, emptyList())
                System.setProperty("user.home", origHome)

                then("creates source directory") {
                    File(tempDir, ".clkx/skills").exists() shouldBe true
                }

                then("writes skill files to source") {
                    File(tempDir, ".clkx/skills/opsx-list.md").exists() shouldBe true
                    File(tempDir, ".clkx/skills/opsx-sync.md").exists() shouldBe true
                }

                then("skill files have correct content") {
                    val content = File(tempDir, ".clkx/skills/opsx-list.md").readText()
                    content shouldContain "# opsx-list"
                    content shouldContain "List changes"
                }

                then("creates symlinks for claude") {
                    val path = File(tempDir, ".claude/commands/opsx-list.md").toPath()
                    Files.exists(path) shouldBe true
                    Files.isSymbolicLink(path) shouldBe true
                }

                then("creates symlinks for copilot") {
                    val path = File(tempDir, ".github/prompts/opsx-list.md").toPath()
                    Files.exists(path) shouldBe true
                    Files.isSymbolicLink(path) shouldBe true
                }

                then("creates symlinks for codex") {
                    val path = File(tempDir, ".codex/prompts/opsx-list.md").toPath()
                    Files.exists(path) shouldBe true
                    Files.isSymbolicLink(path) shouldBe true
                }

                then("creates symlinks for opencode") {
                    val path = File(tempDir, ".opencode/commands/opsx-list.md").toPath()
                    Files.exists(path) shouldBe true
                    Files.isSymbolicLink(path) shouldBe true
                }
            }
        }

        given("SkillGenerator.buildCommandFile for agent-dispatching task") {
            val project = ProjectBuilder.builder().build()
            val generator = SkillGenerator(project)
            val task = TaskInfo("opsx-onboard", "opsx", "Onboard a new contributor")

            `when`("task is in AGENT_TASKS") {
                val content = generator.buildCommandFile(task, emptyList())

                then("contains Execution section") {
                    content shouldContain "## Execution"
                    content shouldContain "Run the Gradle command in the background"
                }
            }
        }

        given("SkillGenerator.buildCommandFile for non-agent task") {
            val project = ProjectBuilder.builder().build()
            val generator = SkillGenerator(project)
            val task = TaskInfo("opsx-status", "opsx", "Show all changes and their status")

            `when`("task is not in AGENT_TASKS") {
                val content = generator.buildCommandFile(task, emptyList())

                then("does not contain Execution section") {
                    content shouldNotContain "## Execution"
                }
            }
        }

        given("SkillGenerator.buildCommandFile with empty description") {
            val project = ProjectBuilder.builder().build()
            val generator = SkillGenerator(project)
            val task = TaskInfo("my-task", "opsx", "")

            `when`("description is empty") {
                val content = generator.buildCommandFile(task, emptyList())

                then("uses fallback description") {
                    content shouldContain "No description available."
                }
            }
        }

        given("SkillGenerator.generate") {
            `when`("project has tracked tasks") {
                val tempDir =
                    File.createTempFile("opsx-gen", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val origHome = System.getProperty("user.home")
                System.setProperty("user.home", tempDir.absolutePath)

                val project =
                    ProjectBuilder
                        .builder()
                        .withProjectDir(tempDir)
                        .build()

                project.tasks.register("opsx-test-task") {
                    it.group = "opsx"
                    it.description = "A test task"
                }

                val generator = SkillGenerator(project)
                generator.generate()

                System.setProperty("user.home", origHome)

                then("generates files for all agents") {
                    File(tempDir, ".claude/commands/opsx-test-task.md").exists() shouldBe true
                    File(tempDir, ".github/prompts/opsx-test-task.md").exists() shouldBe true
                    File(tempDir, ".opencode/commands/opsx-test-task.md").exists() shouldBe true
                    File(tempDir, ".codex/prompts/opsx-test-task.md").exists() shouldBe true
                }
            }
        }

        given("SkillGenerator.TRACKED_GROUPS") {
            then("contains all expected groups") {
                SkillGenerator.TRACKED_GROUPS shouldContain "opsx"
                SkillGenerator.TRACKED_GROUPS shouldContain "srcx"
                SkillGenerator.TRACKED_GROUPS shouldContain "claude"
                SkillGenerator.TRACKED_GROUPS shouldContain "copilot"
                SkillGenerator.TRACKED_GROUPS shouldContain "codex"
                SkillGenerator.TRACKED_GROUPS shouldContain "opencode"
                SkillGenerator.TRACKED_GROUPS shouldContain "wrkx"
            }
        }

        given("TaskInfo data class") {
            val info = TaskInfo("my-task", "my-group", "My description")

            then("has correct values") {
                info.name shouldBe "my-task"
                info.group shouldBe "my-group"
                info.description shouldBe "My description"
            }
        }

        given("SkillGenerator.generateInstructionFiles") {
            `when`("generating instruction files from scratch") {
                val tempDir =
                    File.createTempFile("opsx-instr", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val project =
                    ProjectBuilder
                        .builder()
                        .withProjectDir(tempDir)
                        .build()

                val generator = SkillGenerator(project)
                val tasks =
                    listOf(
                        TaskInfo("opsx-list", "opsx", "List changes"),
                        TaskInfo("opsx-sync", "opsx", "Sync skills"),
                    )

                generator.generateInstructionFiles(tasks, emptyList())

                then("creates CLAUDE.md") {
                    File(tempDir, "CLAUDE.md").exists() shouldBe true
                }

                then("creates AGENTS.md") {
                    File(tempDir, "AGENTS.md").exists() shouldBe true
                }

                then("CLAUDE.md contains OPSX:AUTO markers") {
                    val content = File(tempDir, "CLAUDE.md").readText()
                    content shouldContain "<!-- OPSX:AUTO -->"
                    content shouldContain "<!-- /OPSX:AUTO -->"
                }

                then("AGENTS.md contains OPSX:AUTO markers") {
                    val content = File(tempDir, "AGENTS.md").readText()
                    content shouldContain "<!-- OPSX:AUTO -->"
                    content shouldContain "<!-- /OPSX:AUTO -->"
                }

                then("contains task listings") {
                    val content = File(tempDir, "CLAUDE.md").readText()
                    content shouldContain "./gradlew -q opsx-list"
                    content shouldContain "./gradlew -q opsx-sync"
                    content shouldContain "List changes"
                    content shouldContain "Sync skills"
                }

                then("contains rules section") {
                    val content = File(tempDir, "CLAUDE.md").readText()
                    content shouldContain "## Rules"
                    content shouldContain "Do NOT use grep/sed/awk"
                    content shouldContain "opsx-propose"
                }

                then("contains workspace header") {
                    val content = File(tempDir, "CLAUDE.md").readText()
                    content shouldContain "# OPSX Workspace"
                    content shouldContain "Gradle workspace managed by OPSX"
                }

                then("groups tasks by group with table") {
                    val content = File(tempDir, "CLAUDE.md").readText()
                    content shouldContain "## opsx"
                    content shouldContain "| Skill | Gradle Task | Description |"
                    content shouldContain "`/opsx-list`"
                }
            }

            `when`("instruction files already exist with markers") {
                val tempDir =
                    File.createTempFile("opsx-instr-exist", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val existingContent =
                    """
                    |# My Project
                    |
                    |Some custom content here.
                    |
                    |<!-- OPSX:AUTO -->
                    |old generated content
                    |<!-- /OPSX:AUTO -->
                    |
                    |# Footer
                    |More content after markers.
                    """.trimMargin()

                File(tempDir, "CLAUDE.md").writeText(existingContent)
                File(tempDir, "AGENTS.md").writeText(existingContent)

                val project =
                    ProjectBuilder
                        .builder()
                        .withProjectDir(tempDir)
                        .build()

                val generator = SkillGenerator(project)
                val tasks = listOf(TaskInfo("opsx-status", "opsx", "Show status"))

                generator.generateInstructionFiles(tasks, emptyList())

                then("replaces old marker content in CLAUDE.md") {
                    val content = File(tempDir, "CLAUDE.md").readText()
                    content shouldContain "opsx-status"
                    content shouldContain "Show status"
                    (content.contains("old generated content")) shouldBe false
                }

                then("preserves content before markers") {
                    val content = File(tempDir, "CLAUDE.md").readText()
                    content shouldContain "# My Project"
                    content shouldContain "Some custom content here."
                }

                then("preserves content after markers") {
                    val content = File(tempDir, "CLAUDE.md").readText()
                    content shouldContain "# Footer"
                    content shouldContain "More content after markers."
                }
            }

            `when`("instruction files exist without markers") {
                val tempDir =
                    File.createTempFile("opsx-instr-nomark", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val existingContent = "# Existing Project\n\nSome docs here.\n"
                File(tempDir, "CLAUDE.md").writeText(existingContent)
                File(tempDir, "AGENTS.md").writeText(existingContent)

                val project =
                    ProjectBuilder
                        .builder()
                        .withProjectDir(tempDir)
                        .build()

                val generator = SkillGenerator(project)
                val tasks = listOf(TaskInfo("opsx-propose", "opsx", "Propose a change"))

                generator.generateInstructionFiles(tasks, emptyList())

                then("preserves existing content in CLAUDE.md") {
                    val content = File(tempDir, "CLAUDE.md").readText()
                    content shouldContain "# Existing Project"
                    content shouldContain "Some docs here."
                }

                then("appends markers to CLAUDE.md") {
                    val content = File(tempDir, "CLAUDE.md").readText()
                    content shouldContain "<!-- OPSX:AUTO -->"
                    content shouldContain "<!-- /OPSX:AUTO -->"
                    content shouldContain "opsx-propose"
                }

                then("existing content appears before markers") {
                    val content = File(tempDir, "CLAUDE.md").readText()
                    val existingIdx = content.indexOf("# Existing Project")
                    val markerIdx = content.indexOf("<!-- OPSX:AUTO -->")
                    (existingIdx < markerIdx) shouldBe true
                }
            }

            `when`("tasks have included builds") {
                val tempDir =
                    File.createTempFile("opsx-instr-builds", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val project =
                    ProjectBuilder
                        .builder()
                        .withProjectDir(tempDir)
                        .build()

                val generator = SkillGenerator(project)
                val tasks = listOf(TaskInfo("opsx-list", "opsx", "List changes"))
                // Use real includedBuilds from project (will be empty in test)
                generator.generateInstructionFiles(tasks, project.gradle.includedBuilds)

                then("generates without included builds section when empty") {
                    val content = File(tempDir, "CLAUDE.md").readText()
                    (content.contains("## Included Builds")) shouldBe false
                }
            }
        }

        given("SkillGenerator.generate creates instruction files") {
            `when`("project has tracked tasks") {
                val tempDir =
                    File.createTempFile("opsx-gen-instr", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val origHome = System.getProperty("user.home")
                System.setProperty("user.home", tempDir.absolutePath)

                val project =
                    ProjectBuilder
                        .builder()
                        .withProjectDir(tempDir)
                        .build()

                project.tasks.register("opsx-test-instr") {
                    it.group = "opsx"
                    it.description = "Test instruction generation"
                }

                val generator = SkillGenerator(project)
                generator.generate()

                System.setProperty("user.home", origHome)

                then("creates CLAUDE.md") {
                    File(tempDir, "CLAUDE.md").exists() shouldBe true
                }

                then("creates AGENTS.md") {
                    File(tempDir, "AGENTS.md").exists() shouldBe true
                }

                then("CLAUDE.md has the task") {
                    val content = File(tempDir, "CLAUDE.md").readText()
                    content shouldContain "opsx-test-instr"
                    content shouldContain "Test instruction generation"
                }
            }
        }

        given("SkillGenerator.buildCommandFile with included builds") {
            val project = ProjectBuilder.builder().build()
            val generator = SkillGenerator(project)
            val task = TaskInfo("opsx-list", "opsx", "List changes")

            `when`("builds collection is not empty") {
                // Create a mock IncludedBuild via a real composite build setup
                val tempDir =
                    File.createTempFile("opsx-incl-build", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                // Use buildList to create a list of mock IncludedBuild objects
                val mockBuild =
                    object : org.gradle.api.initialization.IncludedBuild {
                        override fun getName(): String = "my-included-build"

                        override fun getProjectDir(): File = tempDir

                        @Suppress("MaxLineLength")
                        override fun task(p0: String): org.gradle.api.tasks.TaskReference = error("stub")
                    }
                val content = generator.buildCommandFile(task, listOf(mockBuild))

                then("contains Included Builds section") {
                    content shouldContain "## Included Builds"
                    content shouldContain "my-included-build"
                }
            }
        }

        given("SkillGenerator.buildCommandFile for task with TASK_USAGE flags") {
            val project = ProjectBuilder.builder().build()
            val generator = SkillGenerator(project)

            `when`("task is opsx-propose which has flags and notes") {
                val task = TaskInfo("opsx-propose", "opsx", "Propose a new change")
                val content = generator.buildCommandFile(task, emptyList())

                then("contains Usage section with example") {
                    content shouldContain "## Usage"
                    content shouldContain "opsx-propose"
                }

                then("contains Flags section") {
                    content shouldContain "## Flags"
                }

                then("contains Notes section") {
                    content shouldContain "## Notes"
                    content shouldContain "Creates a new change directory"
                }
            }
        }

        given("SkillGenerator companion helpers") {
            `when`("getting generatedDirs") {
                val dirs = SkillGenerator.generatedDirs()
                val home = System.getProperty("user.home")

                then("returns source dir and all agent directories") {
                    dirs.size shouldBe 5
                    dirs.map { it.path } shouldContain File(home, ".clkx/skills").path
                    dirs.map { it.path } shouldContain File(home, ".claude/commands").path
                    dirs.map { it.path } shouldContain File(home, ".github/prompts").path
                    dirs.map { it.path } shouldContain File(home, ".codex/prompts").path
                    dirs.map { it.path } shouldContain File(home, ".opencode/commands").path
                }
            }

            `when`("getting instructionFiles") {
                val rootDir = File("/fake/root")
                val files = SkillGenerator.instructionFiles(rootDir)

                then("returns all instruction files") {
                    files.size shouldBe 3
                    files.map { it.name } shouldContain "CLAUDE.md"
                    files.map { it.name } shouldContain "AGENTS.md"
                    files.map { it.name } shouldContain "copilot-instructions.md"
                }
            }
        }
    })
