package zone.clanker.opsx.workflow

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import zone.clanker.opsx.model.Agent
import zone.clanker.opsx.model.ChangeConfig
import zone.clanker.opsx.model.ChangeStatus
import zone.clanker.opsx.model.TaskStatus
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Integration test covering the full atomic task lifecycle:
 * parse tasks, execute with deps, log appends, status updates,
 * verify gates archive.
 */
class AtomicTaskLifecycleTest :
    BehaviorSpec({

        given("full atomic task lifecycle") {

            `when`("executing tasks, verifying, and archiving") {
                val rootDir =
                    File.createTempFile("lifecycle", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                rootDir.deleteOnExit()

                val changeDir = File(rootDir, "opsx/changes/lifecycle-test").apply { mkdirs() }

                // Set up change config with verify command
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(
                        name = "lifecycle-test",
                        status = "active",
                        verify = "true",
                    ),
                )

                // Set up design.md
                File(changeDir, "design.md").writeText("# Design\n\nTest design.")

                // Set up tasks.md with dependency chain
                File(changeDir, "tasks.md").writeText(
                    """
                    |# Tasks: lifecycle-test
                    |
                    |- [ ] a1b2c3d4e5 | Write test
                    |    Write a unit test for the parser.
                    |  depends: none
                    |
                    |- [ ] c3d4e5f6g7 | Implement parser
                    |    Implement the parser method.
                    |  depends:
                    |    - a1b2c3d4e5
                    |
                    |- [ ] e5f6g7h8i9 | Integration test
                    |    Full lifecycle test.
                    |  depends:
                    |    - c3d4e5f6g7
                    """.trimMargin(),
                )

                // Step 1: Parse tasks
                val tasks = TaskParser.parse(File(changeDir, "tasks.md"))

                then("parses 3 tasks") {
                    tasks.size shouldBe 3
                }

                then("all tasks are TODO") {
                    tasks.all { it.status == TaskStatus.TODO } shouldBe true
                }

                // Step 2: Execute tasks via TaskExecutor with mock dispatcher
                val executionOrder = CopyOnWriteArrayList<String>()
                val executor =
                    TaskExecutor(
                        changeDir = changeDir,
                        agent = Agent.CLAUDE,
                        model = "",
                        workDir = rootDir,
                        dispatcher = { _, prompt, _, _, _ ->
                            val idMatch = Regex("Task ID: ([a-z0-9]{10})").find(prompt)
                            idMatch?.groupValues?.get(1)?.let { executionOrder.add(it) }
                            AgentDispatcher.Result(0, null)
                        },
                    )
                executor.execute(null)

                then("executes in dependency order") {
                    executionOrder.size shouldBe 3
                    executionOrder[0] shouldBe "a1b2c3d4e5"
                    executionOrder[1] shouldBe "c3d4e5f6g7"
                    executionOrder[2] shouldBe "e5f6g7h8i9"
                }

                // Step 3: Verify tasks.md was updated
                val updatedTasks = TaskParser.parse(File(changeDir, "tasks.md"))

                then("all tasks are marked done") {
                    updatedTasks.all { it.status == TaskStatus.DONE } shouldBe true
                }

                // Step 4: Verify log.md was written
                val logContent = File(changeDir, "log.md").readText()

                then("log has entries for all tasks") {
                    logContent shouldContain "a1b2c3d4e5 | Write test"
                    logContent shouldContain "c3d4e5f6g7 | Implement parser"
                    logContent shouldContain "e5f6g7h8i9 | Integration test"
                }

                then("log has started and done entries") {
                    logContent shouldContain "[>]"
                    logContent shouldContain "[x]"
                }

                // Step 5: Verify gates archive — status must be verified first
                val config = ChangeConfig.parse(File(changeDir, ".opsx.yaml"))!!

                then("config still has verify command") {
                    config.verify shouldBe "true"
                }

                then("cannot archive without verified status") {
                    // Status is still "active" — verify must run first
                    val status = ChangeStatus.from(config.status)
                    (status == ChangeStatus.VERIFIED) shouldBe false
                }

                // Step 6: Simulate verify passing
                ChangeConfig.updateStatus(File(changeDir, ".opsx.yaml"), "verified")
                val verifiedConfig = ChangeConfig.parse(File(changeDir, ".opsx.yaml"))!!

                then("status is now verified") {
                    verifiedConfig.status shouldBe "verified"
                }

                then("verified status can transition to archived") {
                    ChangeStatus.from("verified").canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }

                // Step 7: Archive
                ChangeConfig.updateStatus(File(changeDir, ".opsx.yaml"), "archived")
                val archivedConfig = ChangeConfig.parse(File(changeDir, ".opsx.yaml"))!!

                then("status is archived") {
                    archivedConfig.status shouldBe "archived"
                }
            }

            `when`("a task fails mid-execution") {
                val rootDir =
                    File.createTempFile("lifecycle-fail", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                rootDir.deleteOnExit()

                val changeDir = File(rootDir, "opsx/changes/fail-test").apply { mkdirs() }
                File(changeDir, "design.md").writeText("Design.")
                File(changeDir, "tasks.md").writeText(
                    """
                    |# Tasks: fail-test
                    |
                    |- [ ] a1b2c3d4e5 | Passing task
                    |    This will pass.
                    |  depends: none
                    |
                    |- [ ] c3d4e5f6g7 | Failing task
                    |    This will fail.
                    |  depends:
                    |    - a1b2c3d4e5
                    |
                    |- [ ] e5f6g7h8i9 | Blocked task
                    |    Depends on failing task.
                    |  depends:
                    |    - c3d4e5f6g7
                    """.trimMargin(),
                )

                val executed = CopyOnWriteArrayList<String>()
                val executor =
                    TaskExecutor(
                        changeDir = changeDir,
                        agent = Agent.CLAUDE,
                        model = "",
                        workDir = rootDir,
                        dispatcher = { _, prompt, _, _, _ ->
                            val idMatch = Regex("Task ID: ([a-z0-9]{10})").find(prompt)
                            val id = idMatch?.groupValues?.get(1) ?: ""
                            executed.add(id)
                            if (id == "c3d4e5f6g7") {
                                AgentDispatcher.Result(1, null)
                            } else {
                                AgentDispatcher.Result(0, null)
                            }
                        },
                    )
                executor.execute(null)

                then("first task passes, second fails") {
                    executed.size shouldBe 2
                    executed[0] shouldBe "a1b2c3d4e5"
                    executed[1] shouldBe "c3d4e5f6g7"
                }

                val tasks = TaskParser.parse(File(changeDir, "tasks.md"))

                then("first task is done") {
                    tasks[0].status shouldBe TaskStatus.DONE
                }

                then("second task is blocked") {
                    tasks[1].status shouldBe TaskStatus.BLOCKED
                }

                then("third task is blocked by cascade") {
                    tasks[2].status shouldBe TaskStatus.BLOCKED
                }

                then("log reflects the failure") {
                    val logContent = File(changeDir, "log.md").readText()
                    logContent shouldContain "failed (exit 1)"
                    logContent shouldContain "blocked by failed dependency"
                }
            }
        }
    })
