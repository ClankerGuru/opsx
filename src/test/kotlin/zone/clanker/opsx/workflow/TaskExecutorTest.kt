package zone.clanker.opsx.workflow

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import zone.clanker.opsx.model.Agent
import zone.clanker.opsx.model.TaskDefinition
import zone.clanker.opsx.model.TaskStatus
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class TaskExecutorTest :
    BehaviorSpec({

        given("TaskExecutor.resolveExecutionTarget") {

            val tasks =
                listOf(
                    TaskDefinition("a1b2c3d4e5", "Test parse", "Write test.", TaskStatus.TODO, emptyList()),
                    TaskDefinition("c3d4e5f6g7", "Impl parse", "Implement.", TaskStatus.TODO, listOf("a1b2c3d4e5")),
                    TaskDefinition("e5f6g7h8i9", "Test write", "Write test.", TaskStatus.TODO, emptyList()),
                    TaskDefinition("g7h8i9j0k1", "Impl write", "Implement.", TaskStatus.TODO, listOf("e5f6g7h8i9")),
                    TaskDefinition(
                        "i9j0k1l2m3",
                        "Integration",
                        "Full test.",
                        TaskStatus.TODO,
                        listOf("c3d4e5f6g7", "g7h8i9j0k1"),
                    ),
                )

            `when`("targeting by task ID") {
                val target = TaskExecutor.resolveExecutionTarget(tasks, "c3d4e5f6g7")

                then("returns the task and its unfinished dependencies") {
                    val ids = target.map { it.id }.toSet()
                    ids shouldBe setOf("a1b2c3d4e5", "c3d4e5f6g7")
                }
            }

            `when`("targeting by task ID with no deps") {
                val target = TaskExecutor.resolveExecutionTarget(tasks, "a1b2c3d4e5")

                then("returns only the single task") {
                    target shouldHaveSize 1
                    target[0].id shouldBe "a1b2c3d4e5"
                }
            }

            `when`("targeting all tasks (epic name — null ID)") {
                val target = TaskExecutor.resolveExecutionTarget(tasks, null)

                then("returns all tasks") {
                    target shouldHaveSize 5
                }
            }

            `when`("targeting a task with done dependencies") {
                val tasksWithDone =
                    listOf(
                        TaskDefinition("a1b2c3d4e5", "Test parse", "Write test.", TaskStatus.DONE, emptyList()),
                        TaskDefinition("c3d4e5f6g7", "Impl parse", "Implement.", TaskStatus.TODO, listOf("a1b2c3d4e5")),
                    )
                val target = TaskExecutor.resolveExecutionTarget(tasksWithDone, "c3d4e5f6g7")

                then("excludes done dependencies") {
                    target shouldHaveSize 1
                    target[0].id shouldBe "c3d4e5f6g7"
                }
            }
        }

        given("TaskExecutor.buildTaskPrompt") {

            `when`("building a scoped prompt") {
                val changeDir =
                    File.createTempFile("change", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                changeDir.deleteOnExit()
                File(changeDir, "context.md").writeText("Scoped codebase context here.")
                File(changeDir, "design.md").writeText("Design details here.")

                val task =
                    TaskDefinition(
                        "a1b2c3d4e5",
                        "Test parse",
                        "Write Kotest BehaviorSpec for parse method.\nCover valid YAML and malformed input.",
                        TaskStatus.TODO,
                        emptyList(),
                    )

                val prompt = TaskExecutor.buildTaskPrompt(task, changeDir)

                then("includes task name and description") {
                    prompt shouldContain "Test parse"
                    prompt shouldContain "Write Kotest BehaviorSpec for parse method."
                    prompt shouldContain "Cover valid YAML and malformed input."
                }

                then("includes design content") {
                    prompt shouldContain "Design details here."
                }

                then("includes scoped context") {
                    prompt shouldContain "Scoped codebase context here."
                }
            }

            `when`("context.md does not exist") {
                val changeDir =
                    File.createTempFile("change", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                changeDir.deleteOnExit()
                File(changeDir, "design.md").writeText("Design only.")

                val task =
                    TaskDefinition("a1b2c3d4e5", "Task", "Do something.", TaskStatus.TODO, emptyList())

                val prompt = TaskExecutor.buildTaskPrompt(task, changeDir)

                then("still includes task details and design") {
                    prompt shouldContain "Do something."
                    prompt shouldContain "Design only."
                }
            }
        }

        given("TaskExecutor.execute") {

            `when`("executing tasks with dependencies using a mock dispatcher") {
                val changeDir =
                    File.createTempFile("change", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                changeDir.deleteOnExit()
                File(changeDir, "design.md").writeText("Design.")
                File(
                    changeDir,
                    "tasks.md",
                ).writeText(
                    """
                    |# Tasks: test
                    |
                    |- [ ] a1b2c3d4e5 | First task
                    |    Do first thing.
                    |  depends: none
                    |
                    |- [ ] c3d4e5f6g7 | Second task
                    |    Do second thing.
                    |  depends:
                    |    - a1b2c3d4e5
                    """.trimMargin(),
                )

                val executionOrder = CopyOnWriteArrayList<String>()
                val executor =
                    TaskExecutor(
                        changeDir = changeDir,
                        agent = Agent.CLAUDE,
                        model = "",
                        workDir = changeDir,
                        dispatcher = { _, prompt, _, _, _ ->
                            // Extract task ID from the prompt
                            val idMatch = Regex("Task ID: ([a-z0-9]{10})").find(prompt)
                            idMatch?.groupValues?.get(1)?.let { executionOrder.add(it) }
                            AgentDispatcher.Result(0, null)
                        },
                    )

                executor.execute(null)

                then("executes tasks in dependency order") {
                    executionOrder shouldContainExactly listOf("a1b2c3d4e5", "c3d4e5f6g7")
                }

                then("updates tasks.md with done status") {
                    val content = File(changeDir, "tasks.md").readText()
                    content shouldContain "- [x] a1b2c3d4e5"
                    content shouldContain "- [x] c3d4e5f6g7"
                }

                then("appends to log.md") {
                    val logContent = File(changeDir, "log.md").readText()
                    logContent shouldContain "a1b2c3d4e5 | First task"
                    logContent shouldContain "c3d4e5f6g7 | Second task"
                }
            }

            `when`("executing a single task by ID") {
                val changeDir =
                    File.createTempFile("change", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                changeDir.deleteOnExit()
                File(changeDir, "design.md").writeText("Design.")
                File(
                    changeDir,
                    "tasks.md",
                ).writeText(
                    """
                    |# Tasks: test
                    |
                    |- [ ] a1b2c3d4e5 | First task
                    |    Do first thing.
                    |  depends: none
                    |
                    |- [ ] c3d4e5f6g7 | Second task
                    |    Do second thing.
                    |  depends:
                    |    - a1b2c3d4e5
                    |
                    |- [ ] e5f6g7h8i9 | Third task
                    |    Do third thing.
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
                        workDir = changeDir,
                        dispatcher = { _, prompt, _, _, _ ->
                            val idMatch = Regex("Task ID: ([a-z0-9]{10})").find(prompt)
                            idMatch?.groupValues?.get(1)?.let { executed.add(it) }
                            AgentDispatcher.Result(0, null)
                        },
                    )

                executor.execute("c3d4e5f6g7")

                then("executes only the target and its unfinished deps") {
                    executed shouldContainExactly listOf("a1b2c3d4e5", "c3d4e5f6g7")
                }

                then("does not execute unrelated tasks") {
                    executed.size shouldBe 2
                    executed.contains("e5f6g7h8i9") shouldBe false
                }
            }

            `when`("a task fails") {
                val changeDir =
                    File.createTempFile("change", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                changeDir.deleteOnExit()
                File(changeDir, "design.md").writeText("Design.")
                File(
                    changeDir,
                    "tasks.md",
                ).writeText(
                    """
                    |# Tasks: test
                    |
                    |- [ ] a1b2c3d4e5 | Failing task
                    |    This will fail.
                    |  depends: none
                    |
                    |- [ ] c3d4e5f6g7 | Dependent task
                    |    Depends on failing task.
                    |  depends:
                    |    - a1b2c3d4e5
                    """.trimMargin(),
                )

                val executed = CopyOnWriteArrayList<String>()
                val executor =
                    TaskExecutor(
                        changeDir = changeDir,
                        agent = Agent.CLAUDE,
                        model = "",
                        workDir = changeDir,
                        dispatcher = { _, prompt, _, _, _ ->
                            val idMatch = Regex("Task ID: ([a-z0-9]{10})").find(prompt)
                            idMatch?.groupValues?.get(1)?.let { executed.add(it) }
                            AgentDispatcher.Result(1, null)
                        },
                    )

                executor.execute(null)

                then("marks the failed task as blocked") {
                    val content = File(changeDir, "tasks.md").readText()
                    content shouldContain "- [!] a1b2c3d4e5"
                }

                then("does not execute dependent tasks") {
                    executed shouldHaveSize 1
                    executed[0] shouldBe "a1b2c3d4e5"
                }
            }

            `when`("skipping already-done tasks") {
                val changeDir =
                    File.createTempFile("change", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                changeDir.deleteOnExit()
                File(changeDir, "design.md").writeText("Design.")
                File(
                    changeDir,
                    "tasks.md",
                ).writeText(
                    """
                    |# Tasks: test
                    |
                    |- [x] a1b2c3d4e5 | Already done
                    |    This is done.
                    |  depends: none
                    |
                    |- [ ] c3d4e5f6g7 | Next task
                    |    Do next thing.
                    |  depends:
                    |    - a1b2c3d4e5
                    """.trimMargin(),
                )

                val executed = CopyOnWriteArrayList<String>()
                val executor =
                    TaskExecutor(
                        changeDir = changeDir,
                        agent = Agent.CLAUDE,
                        model = "",
                        workDir = changeDir,
                        dispatcher = { _, prompt, _, _, _ ->
                            val idMatch = Regex("Task ID: ([a-z0-9]{10})").find(prompt)
                            idMatch?.groupValues?.get(1)?.let { executed.add(it) }
                            AgentDispatcher.Result(0, null)
                        },
                    )

                executor.execute(null)

                then("only executes the pending task") {
                    executed shouldHaveSize 1
                    executed[0] shouldBe "c3d4e5f6g7"
                }
            }
        }
    })
