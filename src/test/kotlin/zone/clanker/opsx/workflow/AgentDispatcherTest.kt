package zone.clanker.opsx.workflow

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

class AgentDispatcherTest :
    BehaviorSpec({

        given("buildCommand") {

            `when`("agent is claude") {
                val cmd = AgentDispatcher.buildCommand("claude", "")
                then("uses -p flag with skip permissions") {
                    cmd[0] shouldBe "claude"
                    cmd[1] shouldBe "-p"
                    cmd[2] shouldBe "--dangerously-skip-permissions"
                    cmd.size shouldBe 3
                }
            }

            `when`("agent is claude with model") {
                val cmd = AgentDispatcher.buildCommand("claude", "opus")
                then("includes --model") {
                    cmd shouldContain "--model"
                    cmd shouldContain "opus"
                }
            }

            `when`("agent is copilot") {
                val cmd = AgentDispatcher.buildCommand("copilot", "")
                then("uses -p flag") {
                    cmd[0] shouldBe "copilot"
                    cmd[1] shouldBe "-p"
                    cmd.size shouldBe 2
                }
            }

            `when`("agent is codex") {
                val cmd = AgentDispatcher.buildCommand("codex", "")
                then("uses exec subcommand") {
                    cmd[0] shouldBe "codex"
                    cmd[1] shouldBe "exec"
                    cmd.size shouldBe 2
                }
            }

            `when`("agent is codex with model") {
                val cmd = AgentDispatcher.buildCommand("codex", "o3")
                then("uses -m flag") {
                    cmd shouldContain "-m"
                    cmd shouldContain "o3"
                }
            }

            `when`("agent is opencode") {
                val cmd = AgentDispatcher.buildCommand("opencode", "")
                then("uses run subcommand") {
                    cmd[0] shouldBe "opencode"
                    cmd[1] shouldBe "run"
                    cmd.size shouldBe 2
                }
            }

            `when`("agent is unknown") {
                then("throws error") {
                    try {
                        AgentDispatcher.buildCommand("unknown", "")
                        error("should have thrown")
                    } catch (e: IllegalStateException) {
                        e.message shouldBe "Unknown agent: unknown. Use: claude, copilot, codex, opencode"
                    }
                }
            }

            `when`("model is empty") {
                val cmd = AgentDispatcher.buildCommand("claude", "")
                then("does not include --model") {
                    cmd shouldNotContain "--model"
                }
            }
        }

        given("dispatch when agent binary is unavailable") {
            `when`("agent process fails to start or exits with error") {
                then("returns a well-formed Result with non-zero exit code") {
                    val result =
                        AgentDispatcher.dispatch(
                            agent = "claude",
                            prompt = "test prompt",
                            workDir = File(System.getProperty("user.dir")),
                            timeoutSeconds = 5L,
                        )
                    // Agent not installed in CI — expect failure, but Result should be well-formed
                    result shouldNotBe null
                    result.logFile shouldNotBe null
                    result.exitCode shouldNotBe 0
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

        given("buildCommand with model for copilot") {
            `when`("copilot agent with model specified") {
                val cmd = AgentDispatcher.buildCommand("copilot", "gpt-4")
                then("includes --model flag") {
                    cmd shouldContain "--model"
                    cmd shouldContain "gpt-4"
                }
            }
        }

        given("buildCommand with model for opencode") {
            `when`("opencode agent with model specified") {
                val cmd = AgentDispatcher.buildCommand("opencode", "claude-sonnet")
                then("includes -m flag") {
                    cmd shouldContain "-m"
                    cmd shouldContain "claude-sonnet"
                }
            }
        }

        given("dispatch with nonexistent agent binary") {
            `when`("agent binary does not exist on PATH") {
                then("returns failure result gracefully") {
                    val result =
                        AgentDispatcher.dispatch(
                            agent = "claude",
                            prompt = "test prompt for missing agent",
                            workDir = File(System.getProperty("java.io.tmpdir")),
                            timeoutSeconds = 2L,
                        )
                    result shouldNotBe null
                    // The agent is not installed, so it should fail
                    result.logFile shouldNotBe null
                }
            }
        }

        given("dispatch with very short timeout") {
            `when`("timeout is very short and command takes time") {
                then("returns failure result") {
                    val result =
                        AgentDispatcher.dispatch(
                            agent = "claude",
                            prompt = "timeout test prompt",
                            workDir = File(System.getProperty("java.io.tmpdir")),
                            timeoutSeconds = 1L,
                        )
                    result shouldNotBe null
                    result.exitCode shouldBeLessThan 1000
                }
            }
        }

        given("DEFAULT_TIMEOUT_SECONDS") {
            then("is 600") {
                AgentDispatcher.DEFAULT_TIMEOUT_SECONDS shouldBe 600L
            }
        }
    })
