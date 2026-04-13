package zone.clanker.opsx.task

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.gradle.testfixtures.ProjectBuilder
import zone.clanker.opsx.Opsx
import java.io.File

private fun createExtension(): Opsx.SettingsExtension = Opsx.SettingsExtension()

class ExploreTaskTest :
    BehaviorSpec({

        given("ExploreTask.buildPrompt") {

            `when`("context and question are provided") {
                val projectDir =
                    File.createTempFile("opsx-explore", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-explore", ExploreTask::class.java)
                task.extension = createExtension()

                val prompt = task.buildPrompt("full codebase context here", "How does auth work?")

                then("includes codebase context header") {
                    prompt shouldContain "# Codebase Context"
                }

                then("includes the context content") {
                    prompt shouldContain "full codebase context here"
                }

                then("includes the question header") {
                    prompt shouldContain "# Question"
                }

                then("includes the question") {
                    prompt shouldContain "How does auth work?"
                }

                then("includes answer instruction") {
                    prompt shouldContain "Answer based on the codebase context above."
                    prompt shouldContain "Reference specific files, classes, and line numbers"
                }

                then("includes separator between context and question") {
                    prompt shouldContain "---"
                }
            }

            `when`("context is empty") {
                val projectDir =
                    File.createTempFile("opsx-explore", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-explore", ExploreTask::class.java)
                task.extension = createExtension()

                val prompt = task.buildPrompt("", "What is the project structure?")

                then("does not include codebase context header") {
                    prompt shouldNotContain "# Codebase Context"
                }

                then("does not include separator") {
                    prompt shouldNotContain "---"
                }

                then("still includes the question") {
                    prompt shouldContain "# Question"
                    prompt shouldContain "What is the project structure?"
                }

                then("still includes answer instruction") {
                    prompt shouldContain "Answer based on the codebase context above."
                }
            }

            `when`("question contains special characters") {
                val projectDir =
                    File.createTempFile("opsx-explore", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-explore", ExploreTask::class.java)
                task.extension = createExtension()

                val question = "Where is `UserService.kt` and what does the @Transactional annotation do?"
                val prompt = task.buildPrompt("some context", question)

                then("preserves special characters in question") {
                    prompt shouldContain question
                }
            }
        }
    })
