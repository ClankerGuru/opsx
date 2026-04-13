package zone.clanker.opsx.task

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.gradle.testfixtures.ProjectBuilder
import zone.clanker.opsx.Opsx
import java.io.File

private fun createExtension(): Opsx.SettingsExtension = Opsx.SettingsExtension()

class OnboardTaskTest :
    BehaviorSpec({

        given("OnboardTask.buildOnboardPrompt") {

            `when`("all inputs provided with user prompt") {
                val projectDir =
                    File.createTempFile("opsx-onboard", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-onboard", OnboardTask::class.java)
                task.extension = createExtension()

                val prompt =
                    task.buildOnboardPrompt(
                        "srcx codebase context here",
                        "project description here",
                        "I am a backend developer",
                    )

                then("includes codebase context") {
                    prompt shouldContain "srcx codebase context here"
                }

                then("includes project description") {
                    prompt shouldContain "project description here"
                }

                then("includes guided tour instructions") {
                    prompt shouldContain "guided tour"
                    prompt shouldContain "Architecture overview"
                    prompt shouldContain "Key files"
                    prompt shouldContain "How to build, test, and run"
                    prompt shouldContain "opsx workflow"
                }

                then("includes user prompt context") {
                    prompt shouldContain "background/context"
                    prompt shouldContain "I am a backend developer"
                }
            }

            `when`("user prompt is empty") {
                val projectDir =
                    File.createTempFile("opsx-onboard", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-onboard", OnboardTask::class.java)
                task.extension = createExtension()

                val prompt =
                    task.buildOnboardPrompt(
                        "context",
                        "project desc",
                        "",
                    )

                then("does not include contributor background section") {
                    prompt shouldNotContain "background/context"
                }

                then("still includes guided tour instructions") {
                    prompt shouldContain "guided tour"
                    prompt shouldContain "Architecture overview"
                }
            }

            `when`("user prompt is blank (whitespace only)") {
                val projectDir =
                    File.createTempFile("opsx-onboard", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-onboard", OnboardTask::class.java)
                task.extension = createExtension()

                val prompt =
                    task.buildOnboardPrompt(
                        "context",
                        "project desc",
                        "   ",
                    )

                then("does not include contributor background section") {
                    prompt shouldNotContain "background/context"
                }
            }

            `when`("context and project description are empty") {
                val projectDir =
                    File.createTempFile("opsx-onboard", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-onboard", OnboardTask::class.java)
                task.extension = createExtension()

                val prompt =
                    task.buildOnboardPrompt(
                        "",
                        "",
                        "user question",
                    )

                then("skips empty context sections but includes instructions") {
                    prompt shouldNotContain "# Codebase Context"
                    prompt shouldNotContain "# Project Description"
                    prompt shouldContain "guided tour"
                    prompt shouldContain "user question"
                }
            }

            `when`("all inputs are empty") {
                val projectDir =
                    File.createTempFile("opsx-onboard", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-onboard", OnboardTask::class.java)
                task.extension = createExtension()

                val prompt = task.buildOnboardPrompt("", "", "")

                then("still includes instructions section") {
                    prompt shouldContain "guided tour"
                    prompt shouldContain "Architecture overview"
                }
            }
        }
    })
