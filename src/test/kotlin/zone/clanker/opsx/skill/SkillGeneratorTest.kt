package zone.clanker.opsx.skill

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testfixtures.ProjectBuilder
import java.io.File

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
                    content shouldContain "./gradlew opsx-sync"
                }

                then("contains group") {
                    content shouldContain "opsx"
                }

                then("contains context reference") {
                    content shouldContain ".srcx/context.md"
                }
            }
        }

        given("SkillGenerator.generateForClaude") {
            `when`("generating claude command files") {
                val tempDir =
                    File.createTempFile("opsx-gen", "").also {
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

                generator.generateForClaude(tasks, emptyList())

                then("creates .claude/commands directory") {
                    File(tempDir, ".claude/commands").exists() shouldBe true
                }

                then("creates command files") {
                    File(tempDir, ".claude/commands/opsx-list.md").exists() shouldBe true
                    File(tempDir, ".claude/commands/opsx-sync.md").exists() shouldBe true
                }

                then("command files have correct content") {
                    val content = File(tempDir, ".claude/commands/opsx-list.md").readText()
                    content shouldContain "# opsx-list"
                    content shouldContain "List changes"
                    content shouldContain "./gradlew opsx-list"
                }
            }
        }

        given("SkillGenerator.generateForCopilot") {
            `when`("generating copilot prompt files") {
                val tempDir =
                    File.createTempFile("opsx-gen", "").also {
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
                val tasks = listOf(TaskInfo("opsx-status", "opsx", "Show status"))

                generator.generateForCopilot(tasks, emptyList())

                then("creates .github/prompts directory") {
                    File(tempDir, ".github/prompts").exists() shouldBe true
                }

                then("creates prompt files") {
                    File(tempDir, ".github/prompts/opsx-status.md").exists() shouldBe true
                }
            }
        }

        given("SkillGenerator.generateForOpenCode") {
            `when`("generating opencode command files") {
                val tempDir =
                    File.createTempFile("opsx-gen", "").also {
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
                val tasks = listOf(TaskInfo("opsx-propose", "opsx", "Propose a change"))

                generator.generateForOpenCode(tasks, emptyList())

                then("creates .opencode/commands directory") {
                    File(tempDir, ".opencode/commands").exists() shouldBe true
                }

                then("creates command files") {
                    File(tempDir, ".opencode/commands/opsx-propose.md").exists() shouldBe true
                }
            }
        }

        given("SkillGenerator.generateForCodex") {
            `when`("generating codex prompt files") {
                val tempDir =
                    File.createTempFile("opsx-gen", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                // Override user.home so we don't write into real home
                val origHome = System.getProperty("user.home")
                System.setProperty("user.home", tempDir.absolutePath)

                val project =
                    ProjectBuilder
                        .builder()
                        .withProjectDir(tempDir)
                        .build()

                val generator = SkillGenerator(project)
                val tasks = listOf(TaskInfo("opsx-apply", "opsx", "Apply a change"))

                generator.generateForCodex(tasks, emptyList())

                // Restore original home
                System.setProperty("user.home", origHome)

                then("creates .codex/prompts directory") {
                    File(tempDir, ".codex/prompts").exists() shouldBe true
                }

                then("creates prompt files") {
                    File(tempDir, ".codex/prompts/opsx-apply.md").exists() shouldBe true
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
                    content shouldContain "./gradlew opsx-list"
                    content shouldContain "./gradlew opsx-sync"
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

                then("groups tasks by group") {
                    val content = File(tempDir, "CLAUDE.md").readText()
                    content shouldContain "### opsx"
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

        given("SkillGenerator companion helpers") {
            `when`("getting generatedDirs") {
                val rootDir = File("/fake/root")
                val dirs = SkillGenerator.generatedDirs(rootDir)

                then("returns all agent command directories") {
                    dirs.size shouldBe 3
                    dirs.map { it.path } shouldContain File("/fake/root/.claude/commands").path
                    dirs.map { it.path } shouldContain File("/fake/root/.github/prompts").path
                    dirs.map { it.path } shouldContain File("/fake/root/.opencode/commands").path
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
