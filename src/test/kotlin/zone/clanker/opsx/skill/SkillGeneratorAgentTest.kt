package zone.clanker.opsx.skill

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class SkillGeneratorAgentTest :
    BehaviorSpec({
        given("SkillGenerator.generateInstructionFiles with agent that has no instructionFile") {
            `when`("defaultAgent is codex (instructionFile = null)") {
                val tempDir =
                    File.createTempFile("opsx-instr-codex", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val tasks = listOf(TaskInfo("opsx-list", "opsx", "List changes"))
                val generator = SkillGenerator(tempDir, tasks, emptyList(), "codex")

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
            `when`("defaultAgent is copilot") {
                val tempDir =
                    File.createTempFile("opsx-instr-copilot", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val tasks = listOf(TaskInfo("opsx-list", "opsx", "List changes"))
                val generator = SkillGenerator(tempDir, tasks, emptyList(), "copilot")

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
            `when`("defaultAgent is claude") {
                val tempDir =
                    File.createTempFile("opsx-agent-claude", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val generator = SkillGenerator(tempDir, emptyList(), emptyList(), "claude")
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
            `when`("defaultAgent is copilot") {
                val tempDir =
                    File.createTempFile("opsx-agent-copilot", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val generator = SkillGenerator(tempDir, emptyList(), emptyList(), "copilot")
                generator.generateAgentDefinitions()

                then("creates .github/agents/opsx.md") {
                    File(tempDir, ".github/agents/opsx.md").exists() shouldBe true
                }

                then("has copilot heading instead of YAML frontmatter") {
                    val content = File(tempDir, ".github/agents/opsx.md").readText()
                    content shouldContain "# opsx Agent"
                    content.startsWith("---") shouldBe false
                    content shouldNotContain "name: opsx"
                }

                then("has system prompt with lifecycle and tasks") {
                    val content = File(tempDir, ".github/agents/opsx.md").readText()
                    content shouldContain "You are the opsx workflow agent"
                    content shouldContain "## Change Lifecycle"
                    content shouldContain "## Strict Rules"
                }
            }
        }

        given("SkillGenerator.generateAgentDefinitions for codex") {
            `when`("defaultAgent is codex (agentDir = null)") {
                val tempDir =
                    File.createTempFile("opsx-agent-codex", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val generator = SkillGenerator(tempDir, emptyList(), emptyList(), "codex")
                generator.generateAgentDefinitions()

                then("does not create any agent definition file") {
                    File(tempDir, ".claude/agents/opsx.md").exists() shouldBe false
                    File(tempDir, ".github/agents/opsx.md").exists() shouldBe false
                }
            }
        }

        given("SkillGenerator.generateAgentDefinitions for opencode") {
            `when`("defaultAgent is opencode (agentDir = null)") {
                val tempDir =
                    File.createTempFile("opsx-agent-opencode", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val generator = SkillGenerator(tempDir, emptyList(), emptyList(), "opencode")
                generator.generateAgentDefinitions()

                then("does not create any agent definition file") {
                    File(tempDir, ".claude/agents/opsx.md").exists() shouldBe false
                    File(tempDir, ".github/agents/opsx.md").exists() shouldBe false
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
                    val dirs = SkillGenerator.generatedDirs("claude")
                    dirs.size shouldBe 2
                    dirs.map { it.path } shouldContain File(home, ".clkx/skills").path
                    dirs.map { it.path } shouldContain File(home, ".claude/commands").path
                }

                then("returns only source dir and copilot dir for copilot agent") {
                    val dirs = SkillGenerator.generatedDirs("copilot")
                    dirs.size shouldBe 2
                    dirs.map { it.path } shouldContain File(home, ".clkx/skills").path
                    dirs.map { it.path } shouldContain File(home, ".github/prompts").path
                }

                then("throws for unknown agent") {
                    val result = runCatching { SkillGenerator.generatedDirs("unknown") }
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
                    val files = SkillGenerator.instructionFiles(rootDir, "claude")
                    files.size shouldBe 2
                    files.map { it.name } shouldContain "AGENTS.md"
                    files.map { it.name } shouldContain "CLAUDE.md"
                }

                then("returns AGENTS.md and copilot-instructions.md for copilot agent") {
                    val files = SkillGenerator.instructionFiles(rootDir, "copilot")
                    files.size shouldBe 2
                    files.map { it.name } shouldContain "AGENTS.md"
                    files.map { it.name } shouldContain "copilot-instructions.md"
                }

                then("returns only AGENTS.md for codex agent") {
                    val files = SkillGenerator.instructionFiles(rootDir, "codex")
                    files.size shouldBe 1
                    files.map { it.name } shouldContain "AGENTS.md"
                }

                then("returns only AGENTS.md for opencode agent") {
                    val files = SkillGenerator.instructionFiles(rootDir, "opencode")
                    files.size shouldBe 1
                    files.map { it.name } shouldContain "AGENTS.md"
                }

                then("throws for unknown agent") {
                    val result = runCatching { SkillGenerator.instructionFiles(rootDir, "unknown") }
                    result.isFailure shouldBe true
                    result.exceptionOrNull()!!.message shouldContain "Unknown agent"
                }
            }
        }
    })
