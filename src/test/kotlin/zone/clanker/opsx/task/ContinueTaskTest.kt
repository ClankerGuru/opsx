package zone.clanker.opsx.task

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testfixtures.ProjectBuilder
import zone.clanker.opsx.Opsx
import java.io.File

private fun createExtension(): Opsx.SettingsExtension = Opsx.SettingsExtension()

class ContinueTaskTest :
    BehaviorSpec({

        given("ContinueTask.detectProgress") {

            `when`("tasks content has checked and unchecked items") {
                val projectDir =
                    File.createTempFile("opsx-continue", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-continue", ContinueTask::class.java)
                task.extension = createExtension()

                val tasksContent =
                    """
                    - [x] First task
                    - [x] Second task
                    - [ ] Third task
                    - [ ] Fourth task
                    - [ ] Fifth task
                    """.trimIndent()

                then("reports correct progress") {
                    task.detectProgress(tasksContent) shouldBe "2/5 tasks complete"
                }
            }

            `when`("all tasks are completed") {
                val projectDir =
                    File.createTempFile("opsx-continue", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-continue", ContinueTask::class.java)
                task.extension = createExtension()

                val tasksContent =
                    """
                    - [x] First task
                    - [x] Second task
                    - [x] Third task
                    """.trimIndent()

                then("reports all complete") {
                    task.detectProgress(tasksContent) shouldBe "3/3 tasks complete"
                }
            }

            `when`("no tasks are completed") {
                val projectDir =
                    File.createTempFile("opsx-continue", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-continue", ContinueTask::class.java)
                task.extension = createExtension()

                val tasksContent =
                    """
                    - [ ] First task
                    - [ ] Second task
                    """.trimIndent()

                then("reports zero progress") {
                    task.detectProgress(tasksContent) shouldBe "0/2 tasks complete"
                }
            }

            `when`("tasks content is empty") {
                val projectDir =
                    File.createTempFile("opsx-continue", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-continue", ContinueTask::class.java)
                task.extension = createExtension()

                then("reports no tasks found") {
                    task.detectProgress("") shouldBe "no tasks found"
                }
            }

            `when`("tasks content has no checkboxes") {
                val projectDir =
                    File.createTempFile("opsx-continue", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-continue", ContinueTask::class.java)
                task.extension = createExtension()

                val tasksContent = "Just some text without any task checkboxes."

                then("reports no tasks found") {
                    task.detectProgress(tasksContent) shouldBe "no tasks found"
                }
            }

            `when`("tasks have uppercase X") {
                val projectDir =
                    File.createTempFile("opsx-continue", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-continue", ContinueTask::class.java)
                task.extension = createExtension()

                val tasksContent =
                    """
                    - [X] First task
                    - [x] Second task
                    - [ ] Third task
                    """.trimIndent()

                then("counts uppercase X as done") {
                    task.detectProgress(tasksContent) shouldBe "2/3 tasks complete"
                }
            }
        }

        given("ContinueTask.buildContinuePrompt") {

            `when`("all inputs provided") {
                val projectDir =
                    File.createTempFile("opsx-continue", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-continue", ContinueTask::class.java)
                task.extension = createExtension()

                val prompt = task.buildContinuePrompt("codebase ctx", "change ctx", "3/5 tasks complete")

                then("includes codebase context") {
                    prompt shouldContain "codebase ctx"
                }

                then("includes change context") {
                    prompt shouldContain "change ctx"
                }

                then("includes progress") {
                    prompt shouldContain "3/5 tasks complete"
                }

                then("includes continue instructions") {
                    prompt shouldContain "Continue implementing this change"
                    prompt shouldContain "unchecked tasks"
                    prompt shouldContain "tasks.md"
                    prompt shouldContain "design document"
                }
            }

            `when`("progress shows no tasks") {
                val projectDir =
                    File.createTempFile("opsx-continue", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-continue", ContinueTask::class.java)
                task.extension = createExtension()

                val prompt = task.buildContinuePrompt("ctx", "change", "no tasks found")

                then("includes no tasks found in instructions") {
                    prompt shouldContain "no tasks found"
                }
            }
        }
    })
