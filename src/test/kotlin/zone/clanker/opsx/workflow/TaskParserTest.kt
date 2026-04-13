package zone.clanker.opsx.workflow

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import zone.clanker.opsx.model.TaskStatus
import java.io.File

class TaskParserTest :
    BehaviorSpec({

        given("TaskParser.parse") {

            `when`("parsing a single task with no dependencies") {
                val lines =
                    listOf(
                        "# Tasks: test-change",
                        "",
                        "- [ ] a1b2c3d4e5 | Test ChangeConfig.parse",
                        "    Write Kotest BehaviorSpec for parse method.",
                        "    Cover valid YAML and malformed input.",
                        "  depends: none",
                    )
                val tasks = TaskParser.parse(lines)

                then("extracts one task") {
                    tasks shouldHaveSize 1
                }
                then("parses ID correctly") {
                    tasks[0].id shouldBe "a1b2c3d4e5"
                }
                then("parses name correctly") {
                    tasks[0].name shouldBe "Test ChangeConfig.parse"
                }
                then("parses description correctly") {
                    tasks[0].description shouldBe
                        "Write Kotest BehaviorSpec for parse method.\n" +
                        "Cover valid YAML and malformed input."
                }
                then("status is TODO") {
                    tasks[0].status shouldBe TaskStatus.TODO
                }
                then("no dependencies") {
                    tasks[0].dependencies.shouldBeEmpty()
                }
            }

            `when`("parsing a task with dependencies") {
                val lines =
                    listOf(
                        "- [ ] e5f6g7h8i9 | Implement ChangeConfig.parse",
                        "    Add parse method to ChangeConfig.kt.",
                        "  depends:",
                        "    - a1b2c3d4e5",
                        "    - c3d4e5f6g7",
                    )
                val tasks = TaskParser.parse(lines)

                then("extracts dependencies") {
                    tasks[0].dependencies shouldBe listOf("a1b2c3d4e5", "c3d4e5f6g7")
                }
            }

            `when`("parsing multiple tasks") {
                val lines =
                    listOf(
                        "- [ ] a1b2c3d4e5 | Test parse",
                        "    Write test.",
                        "  depends: none",
                        "",
                        "- [x] c3d4e5f6g7 | Test write",
                        "    Write test for write.",
                        "  depends: none",
                        "",
                        "- [>] e5f6g7h8i9 | Implement parse",
                        "    Implement it.",
                        "  depends:",
                        "    - a1b2c3d4e5",
                    )
                val tasks = TaskParser.parse(lines)

                then("extracts all three tasks") {
                    tasks shouldHaveSize 3
                }
                then("first is TODO") {
                    tasks[0].status shouldBe TaskStatus.TODO
                }
                then("second is DONE") {
                    tasks[1].status shouldBe TaskStatus.DONE
                }
                then("third is IN_PROGRESS") {
                    tasks[2].status shouldBe TaskStatus.IN_PROGRESS
                }
            }

            `when`("parsing task with blocked status") {
                val lines =
                    listOf(
                        "- [!] a1b2c3d4e5 | Blocked task",
                        "    This is blocked.",
                        "  depends: none",
                    )
                val tasks = TaskParser.parse(lines)

                then("status is BLOCKED") {
                    tasks[0].status shouldBe TaskStatus.BLOCKED
                }
            }

            `when`("parsing task with skipped status") {
                val lines =
                    listOf(
                        "- [~] a1b2c3d4e5 | Skipped task",
                        "    This was skipped.",
                        "  depends: none",
                    )
                val tasks = TaskParser.parse(lines)

                then("status is SKIPPED") {
                    tasks[0].status shouldBe TaskStatus.SKIPPED
                }
            }

            `when`("parsing empty input") {
                val tasks = TaskParser.parse(emptyList())

                then("returns empty list") {
                    tasks.shouldBeEmpty()
                }
            }

            `when`("parsing input with no tasks") {
                val lines =
                    listOf(
                        "# Tasks: something",
                        "",
                        "Some random text.",
                    )
                val tasks = TaskParser.parse(lines)

                then("returns empty list") {
                    tasks.shouldBeEmpty()
                }
            }
        }

        given("TaskParser.generateId") {
            `when`("generating an ID") {
                val id = TaskParser.generateId()

                then("is 10 characters") {
                    id shouldHaveLength 10
                }
                then("is alphanumeric lowercase") {
                    id.all { it in 'a'..'z' || it in '0'..'9' } shouldBe true
                }
            }

            `when`("generating multiple IDs") {
                val ids = (1..100).map { TaskParser.generateId() }.toSet()

                then("all are unique") {
                    ids shouldHaveSize 100
                }
            }
        }

        given("TaskParser.findTerminalTasks") {
            `when`("task graph has terminal nodes") {
                val tasks =
                    TaskParser.parse(
                        listOf(
                            "- [ ] a1b2c3d4e5 | First",
                            "    Do first thing.",
                            "  depends: none",
                            "",
                            "- [ ] c3d4e5f6g7 | Second",
                            "    Do second thing.",
                            "  depends:",
                            "    - a1b2c3d4e5",
                            "",
                            "- [ ] e5f6g7h8i9 | Third",
                            "    Do third thing.",
                            "  depends:",
                            "    - c3d4e5f6g7",
                        ),
                    )
                val terminals = TaskParser.findTerminalTasks(tasks)

                then("returns only the leaf task") {
                    terminals shouldHaveSize 1
                    terminals[0].id shouldBe "e5f6g7h8i9"
                }
            }
        }

        given("TaskParser.topologicalOrder") {
            `when`("tasks have dependencies") {
                val tasks =
                    TaskParser.parse(
                        listOf(
                            "- [ ] e5f6g7h8i9 | Third",
                            "    Depends on second.",
                            "  depends:",
                            "    - c3d4e5f6g7",
                            "",
                            "- [ ] a1b2c3d4e5 | First",
                            "    No deps.",
                            "  depends: none",
                            "",
                            "- [ ] c3d4e5f6g7 | Second",
                            "    Depends on first.",
                            "  depends:",
                            "    - a1b2c3d4e5",
                        ),
                    )
                val ordered = TaskParser.topologicalOrder(tasks)

                then("first has no deps, comes first") {
                    ordered[0].id shouldBe "a1b2c3d4e5"
                }
                then("second depends on first, comes second") {
                    ordered[1].id shouldBe "c3d4e5f6g7"
                }
                then("third depends on second, comes last") {
                    ordered[2].id shouldBe "e5f6g7h8i9"
                }
            }
        }

        given("TaskParser.updateStatus") {
            `when`("updating a task status in a file") {
                val file = File.createTempFile("tasks", ".md")
                file.deleteOnExit()
                file.writeText(
                    """
                    |# Tasks: test
                    |
                    |- [ ] a1b2c3d4e5 | First task
                    |    Description.
                    |  depends: none
                    |
                    |- [ ] c3d4e5f6g7 | Second task
                    |    Description.
                    |  depends: none
                    """.trimMargin(),
                )

                TaskParser.updateStatus(file, "a1b2c3d4e5", TaskStatus.DONE)

                then("updates only the targeted task") {
                    val content = file.readText()
                    content.contains("- [x] a1b2c3d4e5") shouldBe true
                    content.contains("- [ ] c3d4e5f6g7") shouldBe true
                }
            }
        }
    })
