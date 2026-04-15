package zone.clanker.opsx.workflow

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import zone.clanker.opsx.model.Agent
import java.io.File

class AgentDispatcherTest :
    BehaviorSpec({

        val promptFile =
            File.createTempFile("opsx-test-prompt", ".md").apply {
                writeText("test prompt")
                deleteOnExit()
            }

        given("buildCommand") {

            `when`("agent is CLAUDE") {
                val cmd = AgentDispatcher.buildCommand(Agent.CLAUDE, promptFile, "")
                then("uses -p flag with skip permissions and prompt") {
                    cmd[0] shouldBe "claude"
                    cmd[1] shouldBe "-p"
                    cmd[2] shouldBe "--dangerously-skip-permissions"
                    cmd.last() shouldBe "test prompt"
                }
            }

            `when`("agent is CLAUDE with model") {
                val cmd = AgentDispatcher.buildCommand(Agent.CLAUDE, promptFile, "opus")
                then("includes --model") {
                    cmd shouldContain "--model"
                    cmd shouldContain "opus"
                }
            }

            `when`("agent is COPILOT") {
                val cmd = AgentDispatcher.buildCommand(Agent.COPILOT, promptFile, "")
                then("uses -p flag and prompt") {
                    cmd[0] shouldBe "copilot"
                    cmd[1] shouldBe "-p"
                    cmd.last() shouldBe "test prompt"
                }
            }

            `when`("agent is CODEX") {
                val cmd = AgentDispatcher.buildCommand(Agent.CODEX, promptFile, "")
                then("uses exec subcommand and prompt") {
                    cmd[0] shouldBe "codex"
                    cmd[1] shouldBe "exec"
                    cmd.last() shouldBe "test prompt"
                }
            }

            `when`("agent is CODEX with model") {
                val cmd = AgentDispatcher.buildCommand(Agent.CODEX, promptFile, "o3")
                then("uses -m flag") {
                    cmd shouldContain "-m"
                    cmd shouldContain "o3"
                }
            }

            `when`("agent is OPENCODE") {
                val cmd = AgentDispatcher.buildCommand(Agent.OPENCODE, promptFile, "")
                then("uses run subcommand and prompt") {
                    cmd[0] shouldBe "opencode"
                    cmd[1] shouldBe "run"
                    cmd.last() shouldBe "test prompt"
                }
            }

            `when`("unknown agent string is passed to Agent.fromId") {
                then("throws error") {
                    shouldThrow<IllegalStateException> {
                        Agent.fromId("unknown")
                    }.message shouldContain "Unknown agent"
                }
            }

            `when`("model is empty") {
                val cmd = AgentDispatcher.buildCommand(Agent.CLAUDE, promptFile, "")
                then("does not include --model") {
                    cmd shouldNotContain "--model"
                }
            }
        }

        given("buildCommand with model for copilot") {
            `when`("COPILOT agent with model specified") {
                val cmd = AgentDispatcher.buildCommand(Agent.COPILOT, promptFile, "gpt-4")
                then("includes --model flag") {
                    cmd shouldContain "--model"
                    cmd shouldContain "gpt-4"
                }
            }
        }

        given("buildCommand with model for opencode") {
            `when`("OPENCODE agent with model specified") {
                val cmd = AgentDispatcher.buildCommand(Agent.OPENCODE, promptFile, "claude-sonnet")
                then("includes -m flag") {
                    cmd shouldContain "-m"
                    cmd shouldContain "claude-sonnet"
                }
            }
        }

        given("Result data class") {
            `when`("created with exit code and log file") {
                val logFile = File("/tmp/test.log")
                val result = AgentDispatcher.Result(0, logFile)
                then("fields are accessible") {
                    result.exitCode shouldBe 0
                    result.logFile shouldBe logFile
                }
            }

            `when`("created with null log file") {
                val result = AgentDispatcher.Result(-1, null)
                then("logFile is null") {
                    result.exitCode shouldBe -1
                    result.logFile shouldBe null
                }
            }
        }

        given("DEFAULT_TIMEOUT_SECONDS") {
            then("is 600") {
                AgentDispatcher.DEFAULT_TIMEOUT_SECONDS shouldBe 600L
            }
        }
    })
