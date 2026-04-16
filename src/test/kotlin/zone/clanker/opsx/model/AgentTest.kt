package zone.clanker.opsx.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class AgentTest :
    BehaviorSpec({

        given("Agent.fromId") {
            `when`("given a valid agent id") {
                then("returns CLAUDE for 'claude'") {
                    Agent.fromId("claude") shouldBe Agent.CLAUDE
                }

                then("returns COPILOT for 'copilot'") {
                    Agent.fromId("copilot") shouldBe Agent.COPILOT
                }

                then("returns CODEX for 'codex'") {
                    Agent.fromId("codex") shouldBe Agent.CODEX
                }

                then("returns OPENCODE for 'opencode'") {
                    Agent.fromId("opencode") shouldBe Agent.OPENCODE
                }
            }

            `when`("given an unknown agent id") {
                then("throws with descriptive message") {
                    shouldThrow<IllegalStateException> {
                        Agent.fromId("unknown")
                    }.message shouldContain "Unknown agent 'unknown'"
                }
            }
        }

        given("Agent.id") {
            then("returns lowercase name for each agent") {
                Agent.CLAUDE.id shouldBe "claude"
                Agent.COPILOT.id shouldBe "copilot"
                Agent.CODEX.id shouldBe "codex"
                Agent.OPENCODE.id shouldBe "opencode"
            }
        }

        given("Agent.allSkillsDirs") {
            then("returns all four skill directories") {
                Agent.allSkillsDirs shouldContainExactlyInAnyOrder
                    listOf(
                        ".claude/skills",
                        ".github/skills",
                        ".codex/skills",
                        ".opencode/skills",
                    )
            }
        }

        given("Agent.allIds") {
            then("returns set of all four agent ids") {
                Agent.allIds shouldContainExactlyInAnyOrder listOf("claude", "copilot", "codex", "opencode")
            }
        }

        given("Agent enum properties") {
            `when`("agent is CLAUDE") {
                then("has correct CLI configuration") {
                    Agent.CLAUDE.cliCommand shouldBe "claude"
                    Agent.CLAUDE.nonInteractiveArgs shouldBe listOf("-p", "--dangerously-skip-permissions")
                    Agent.CLAUDE.modelFlag shouldBe "--model"
                    Agent.CLAUDE.skillsDir shouldBe ".claude/skills"
                    Agent.CLAUDE.instructionFile shouldBe "CLAUDE.md"
                    Agent.CLAUDE.agentDir shouldBe ".claude/agents"
                    Agent.CLAUDE.usesCopy shouldBe false
                }
            }

            `when`("agent is COPILOT") {
                then("usesCopy is true") {
                    Agent.COPILOT.usesCopy shouldBe true
                }
            }

            `when`("agent is CODEX") {
                then("has null instructionFile and agentDir") {
                    Agent.CODEX.instructionFile shouldBe null
                    Agent.CODEX.agentDir shouldBe null
                }
            }

            `when`("agent is OPENCODE") {
                then("has null instructionFile and agentDir") {
                    Agent.OPENCODE.instructionFile shouldBe null
                    Agent.OPENCODE.agentDir shouldBe null
                }
            }
        }
    })
