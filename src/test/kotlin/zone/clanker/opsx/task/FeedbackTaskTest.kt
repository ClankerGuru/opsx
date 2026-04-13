package zone.clanker.opsx.task

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import org.gradle.testfixtures.ProjectBuilder
import zone.clanker.opsx.Opsx
import java.io.File
import java.time.LocalDate

private fun createExtension(): Opsx.SettingsExtension = Opsx.SettingsExtension()

class FeedbackTaskTest :
    BehaviorSpec({

        given("FeedbackTask.formatFeedbackEntry") {

            `when`("feedback text is provided") {
                val projectDir =
                    File.createTempFile("opsx-feedback", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-feedback", FeedbackTask::class.java)
                task.extension = createExtension()

                val feedback = "The auth module needs better error handling"
                val entry = task.formatFeedbackEntry(feedback)

                then("includes today's date as heading") {
                    entry shouldContain "### ${LocalDate.now()}"
                }

                then("includes the feedback text") {
                    entry shouldContain feedback
                }
            }

            `when`("feedback text is multiline") {
                val projectDir =
                    File.createTempFile("opsx-feedback", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-feedback", FeedbackTask::class.java)
                task.extension = createExtension()

                val feedback = "Line one\nLine two\nLine three"
                val entry = task.formatFeedbackEntry(feedback)

                then("preserves multiline feedback") {
                    entry shouldContain "Line one\nLine two\nLine three"
                }

                then("includes date heading") {
                    entry shouldContain "### ${LocalDate.now()}"
                }
            }

            `when`("feedback text is empty") {
                val projectDir =
                    File.createTempFile("opsx-feedback", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-feedback", FeedbackTask::class.java)
                task.extension = createExtension()

                val entry = task.formatFeedbackEntry("")

                then("still includes date heading") {
                    entry shouldContain "### ${LocalDate.now()}"
                }
            }
        }

        given("FeedbackTask.buildFeedbackPrompt") {

            `when`("all inputs provided") {
                val projectDir =
                    File.createTempFile("opsx-feedback", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-feedback", FeedbackTask::class.java)
                task.extension = createExtension()

                val prompt =
                    task.buildFeedbackPrompt(
                        "codebase context",
                        "change context",
                        "Please add error handling to the API layer",
                    )

                then("includes codebase context") {
                    prompt shouldContain "codebase context"
                }

                then("includes change context") {
                    prompt shouldContain "change context"
                }

                then("includes feedback text") {
                    prompt shouldContain "Please add error handling to the API layer"
                }

                then("includes feedback instructions") {
                    prompt shouldContain "user has provided feedback"
                    prompt shouldContain "Incorporate this feedback"
                    prompt shouldContain "design.md"
                    prompt shouldContain "tasks.md"
                }
            }

            `when`("context is empty") {
                val projectDir =
                    File.createTempFile("opsx-feedback", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-feedback", FeedbackTask::class.java)
                task.extension = createExtension()

                val prompt = task.buildFeedbackPrompt("", "change ctx", "some feedback")

                then("still includes change context") {
                    prompt shouldContain "change ctx"
                }

                then("still includes feedback in instructions") {
                    prompt shouldContain "some feedback"
                }
            }
        }
    })
