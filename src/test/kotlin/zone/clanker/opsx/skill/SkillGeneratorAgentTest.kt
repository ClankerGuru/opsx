package zone.clanker.opsx.skill

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import zone.clanker.opsx.model.Agent
import java.io.File
import java.nio.file.Files

class SkillGeneratorAgentTest :
    BehaviorSpec({
        given("SkillGenerator.generateInstructionFiles with agent that has no instructionFile") {
            `when`("agents is listOf(CODEX) (instructionFile = null)") {
                val tempDir =
                    File.createTempFile("opsx-instr-codex", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val tasks = listOf(TaskInfo("opsx-list", "opsx", "List changes"))
                val generator = SkillGenerator(tempDir, tasks, emptyList(), listOf(Agent.CODEX))

                generator.generateInstructionFiles(tasks, emptyList())

                then("creates AGENTS.md") {
                    File(tempDir, "AGENTS.md").exists() shouldBe true
                }

                then("does not create CLAUDE.md") {
                    File(tempDir, "CLAUDE.md").exists() shouldBe false
                }

                then("does not create copilot-instructions.md") {
                    File(tempDir, ".github/copilot-instructions.md").exists() shouldBe false
                }
            }
        }

        given("SkillGenerator.generateInstructionFiles with copilot agent") {
            `when`("agents is listOf(COPILOT)") {
                val tempDir =
                    File.createTempFile("opsx-instr-copilot", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val tasks = listOf(TaskInfo("opsx-list", "opsx", "List changes"))
                val generator = SkillGenerator(tempDir, tasks, emptyList(), listOf(Agent.COPILOT))

                generator.generateInstructionFiles(tasks, emptyList())

                then("creates AGENTS.md") {
                    File(tempDir, "AGENTS.md").exists() shouldBe true
                }

                then("creates copilot-instructions.md") {
                    File(tempDir, ".github/copilot-instructions.md").exists() shouldBe true
                }

                then("does not create CLAUDE.md") {
                    File(tempDir, "CLAUDE.md").exists() shouldBe false
                }
            }
        }

        given("SkillGenerator.generateAgentDefinitions for claude") {
            `when`("agents is listOf(CLAUDE)") {
                val tempDir =
                    File.createTempFile("opsx-agent-claude", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val generator = SkillGenerator(tempDir, emptyList(), emptyList(), listOf(Agent.CLAUDE))
                generator.generateAgentDefinitions()

                then("creates .claude/agents/opsx.md") {
                    File(tempDir, ".claude/agents/opsx.md").exists() shouldBe true
                }

                then("has YAML frontmatter with correct fields") {
                    val content = File(tempDir, ".claude/agents/opsx.md").readText()
                    content shouldContain "---"
                    content shouldContain "name: opsx"
                    content shouldContain "model: inherit"
                    content shouldContain "color: green"
                    content shouldContain "description: |"
                }

                then("has system prompt with lifecycle and rules") {
                    val content = File(tempDir, ".claude/agents/opsx.md").readText()
                    content shouldContain "You are the opsx workflow agent"
                    content shouldContain "## Change Lifecycle"
                    content shouldContain "## Strict Rules"
                }
            }
        }

        given("SkillGenerator.generateAgentDefinitions for copilot") {
            `when`("agents is listOf(COPILOT)") {
                val tempDir =
                    File.createTempFile("opsx-agent-copilot", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val generator = SkillGenerator(tempDir, emptyList(), emptyList(), listOf(Agent.COPILOT))
                generator.generateAgentDefinitions()

                then("creates .github/agents/opsx.md") {
                    File(tempDir, ".github/agents/opsx.md").exists() shouldBe true
                }

                then("has YAML frontmatter for copilot") {
                    val content = File(tempDir, ".github/agents/opsx.md").readText()
                    content.startsWith("---") shouldBe true
                    content shouldContain "name: opsx"
                    content shouldContain "description: |"
                }

                then("has system prompt with lifecycle and tasks") {
                    val content = File(tempDir, ".github/agents/opsx.md").readText()
                    content shouldContain "You are the opsx workflow agent"
                    content shouldContain "## Change Lifecycle"
                    content shouldContain "## Strict Rules"
                }

                then("cleans claude agent definition") {
                    File(tempDir, ".claude/agents/opsx.md").exists() shouldBe false
                }
            }
        }

        given("SkillGenerator.generateAgentDefinitions for codex") {
            `when`("agents is listOf(CODEX) (agentDir = null)") {
                val tempDir =
                    File.createTempFile("opsx-agent-codex", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val generator = SkillGenerator(tempDir, emptyList(), emptyList(), listOf(Agent.CODEX))
                generator.generateAgentDefinitions()

                then("does not create any agent definition file") {
                    File(tempDir, ".claude/agents/opsx.md").exists() shouldBe false
                    File(tempDir, ".github/agents/opsx.md").exists() shouldBe false
                }
            }
        }

        given("SkillGenerator.generateAgentDefinitions for opencode") {
            `when`("agents is listOf(OPENCODE) (agentDir = null)") {
                val tempDir =
                    File.createTempFile("opsx-agent-opencode", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val generator = SkillGenerator(tempDir, emptyList(), emptyList(), listOf(Agent.OPENCODE))
                generator.generateAgentDefinitions()

                then("does not create any agent definition file") {
                    File(tempDir, ".claude/agents/opsx.md").exists() shouldBe false
                    File(tempDir, ".github/agents/opsx.md").exists() shouldBe false
                }
            }
        }

        given("SkillGenerator.generateAgentDefinitions for multiple agents") {
            `when`("agents is listOf(CLAUDE, COPILOT)") {
                val tempDir =
                    File.createTempFile("opsx-agent-multi", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val generator =
                    SkillGenerator(tempDir, emptyList(), emptyList(), listOf(Agent.CLAUDE, Agent.COPILOT))
                generator.generateAgentDefinitions()

                then("creates .claude/agents/opsx.md") {
                    File(tempDir, ".claude/agents/opsx.md").exists() shouldBe true
                }

                then("creates .github/agents/opsx.md") {
                    File(tempDir, ".github/agents/opsx.md").exists() shouldBe true
                }

                then("claude definition has correct frontmatter") {
                    val content = File(tempDir, ".claude/agents/opsx.md").readText()
                    content shouldContain "---"
                    content shouldContain "name: opsx"
                    content shouldContain "model: inherit"
                    content shouldContain "color: green"
                }

                then("copilot definition has correct frontmatter") {
                    val content = File(tempDir, ".github/agents/opsx.md").readText()
                    content.startsWith("---") shouldBe true
                    content shouldContain "name: opsx"
                    content shouldContain "description: |"
                }

                then("both definitions have system prompt") {
                    val claudeContent = File(tempDir, ".claude/agents/opsx.md").readText()
                    val copilotContent = File(tempDir, ".github/agents/opsx.md").readText()
                    claudeContent shouldContain "You are the opsx workflow agent"
                    copilotContent shouldContain "You are the opsx workflow agent"
                }
            }
        }

        given("SkillGenerator.generate with multi-agent list") {
            `when`("agents is listOf(CLAUDE, COPILOT)") {
                val tempDir =
                    File.createTempFile("opsx-multi-agent-full", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val origHome = System.getProperty("user.home")
                System.setProperty("user.home", tempDir.absolutePath)

                val tasks = listOf(TaskInfo("opsx-list", "opsx", "List changes"))
                val generator =
                    SkillGenerator(tempDir, tasks, emptyList(), listOf(Agent.CLAUDE, Agent.COPILOT))
                generator.generate()

                System.setProperty("user.home", origHome)

                then("creates symlinks in .claude/commands/") {
                    val path = File(tempDir, ".claude/commands/opsx-list.md").toPath()
                    Files.exists(path) shouldBe true
                    Files.isSymbolicLink(path) shouldBe true
                }

                then("creates symlinks in .github/prompts/") {
                    val path = File(tempDir, ".github/prompts/opsx-list.md").toPath()
                    Files.exists(path) shouldBe true
                    Files.isSymbolicLink(path) shouldBe true
                }

                then("writes CLAUDE.md instruction file") {
                    val file = File(tempDir, "CLAUDE.md")
                    file.exists() shouldBe true
                    file.readText() shouldContain "<!-- OPSX:AUTO -->"
                    file.readText() shouldContain "opsx-list"
                }

                then("writes copilot-instructions.md instruction file") {
                    val file = File(tempDir, ".github/copilot-instructions.md")
                    file.exists() shouldBe true
                    file.readText() shouldContain "<!-- OPSX:AUTO -->"
                    file.readText() shouldContain "opsx-list"
                }

                then("writes AGENTS.md") {
                    val file = File(tempDir, "AGENTS.md")
                    file.exists() shouldBe true
                    file.readText() shouldContain "<!-- OPSX:AUTO -->"
                }

                then("creates .claude/agents/opsx.md") {
                    File(tempDir, ".claude/agents/opsx.md").exists() shouldBe true
                }

                then("creates .github/agents/opsx.md") {
                    File(tempDir, ".github/agents/opsx.md").exists() shouldBe true
                }

                then("does not create symlinks for inactive agents") {
                    File(tempDir, ".codex/prompts/opsx-list.md").exists() shouldBe false
                    File(tempDir, ".opencode/commands/opsx-list.md").exists() shouldBe false
                }
            }
        }

        given("SkillGenerator companion helpers") {
            `when`("getting generatedDirs without agent filter") {
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

            `when`("getting generatedDirs with agent filter") {
                val home = System.getProperty("user.home")

                then("returns only source dir and claude dir for claude agent") {
                    val dirs = SkillGenerator.generatedDirs(Agent.CLAUDE)
                    dirs.size shouldBe 2
                    dirs.map { it.path } shouldContain File(home, ".clkx/skills").path
                    dirs.map { it.path } shouldContain File(home, ".claude/commands").path
                }

                then("returns only source dir and copilot dir for copilot agent") {
                    val dirs = SkillGenerator.generatedDirs(Agent.COPILOT)
                    dirs.size shouldBe 2
                    dirs.map { it.path } shouldContain File(home, ".clkx/skills").path
                    dirs.map { it.path } shouldContain File(home, ".github/prompts").path
                }

                then("throws for unknown agent") {
                    val result = runCatching { Agent.fromId("unknown") }
                    result.isFailure shouldBe true
                    result.exceptionOrNull()!!.message shouldContain "Unknown agent"
                }
            }

            `when`("getting instructionFiles without agent filter") {
                val rootDir = File("/fake/root")
                val files = SkillGenerator.instructionFiles(rootDir)

                then("returns all instruction files") {
                    files.size shouldBe 3
                    files.map { it.name } shouldContain "CLAUDE.md"
                    files.map { it.name } shouldContain "AGENTS.md"
                    files.map { it.name } shouldContain "copilot-instructions.md"
                }
            }

            `when`("getting instructionFiles with agent filter") {
                val rootDir = File("/fake/root")

                then("returns AGENTS.md and CLAUDE.md for claude agent") {
                    val files = SkillGenerator.instructionFiles(rootDir, Agent.CLAUDE)
                    files.size shouldBe 2
                    files.map { it.name } shouldContain "AGENTS.md"
                    files.map { it.name } shouldContain "CLAUDE.md"
                }

                then("returns AGENTS.md and copilot-instructions.md for copilot agent") {
                    val files = SkillGenerator.instructionFiles(rootDir, Agent.COPILOT)
                    files.size shouldBe 2
                    files.map { it.name } shouldContain "AGENTS.md"
                    files.map { it.name } shouldContain "copilot-instructions.md"
                }

                then("returns only AGENTS.md for codex agent") {
                    val files = SkillGenerator.instructionFiles(rootDir, Agent.CODEX)
                    files.size shouldBe 1
                    files.map { it.name } shouldContain "AGENTS.md"
                }

                then("returns only AGENTS.md for opencode agent") {
                    val files = SkillGenerator.instructionFiles(rootDir, Agent.OPENCODE)
                    files.size shouldBe 1
                    files.map { it.name } shouldContain "AGENTS.md"
                }

                then("throws for unknown agent") {
                    val result = runCatching { Agent.fromId("unknown") }
                    result.isFailure shouldBe true
                    result.exceptionOrNull()!!.message shouldContain "Unknown agent"
                }
            }
        }
    })
