package zone.clanker.opsx.task

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import org.gradle.testfixtures.ProjectBuilder
import zone.clanker.opsx.Opsx
import java.io.File

private fun createExtension(): Opsx.SettingsExtension = Opsx.SettingsExtension()

class FfTaskTest :
    BehaviorSpec({

        given("FfTask.buildFfPrompt") {

            `when`("context and change context are provided") {
                val projectDir =
                    File.createTempFile("opsx-ff", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-ff", FfTask::class.java)
                task.extension = createExtension()

                val prompt = task.buildFfPrompt("codebase context", "change context", "opsx/changes/test")

                then("includes codebase context") {
                    prompt shouldContain "codebase context"
                }

                then("includes change context") {
                    prompt shouldContain "change context"
                }

                then("includes fast-forward instructions") {
                    prompt shouldContain "Fast-forward this change"
                    prompt shouldContain "current codebase state"
                }

                then("includes update guidance for proposal and design") {
                    prompt shouldContain "proposal.md"
                    prompt shouldContain "design.md"
                }

                then("includes guidance about what to look for") {
                    prompt shouldContain "files that have moved, been renamed, or deleted"
                    prompt shouldContain "New APIs or patterns"
                    prompt shouldContain "Dependencies that have changed"
                }

                then("includes note instruction") {
                    prompt shouldContain "Note what changed and why"
                }
            }

            `when`("context is empty but change context is present") {
                val projectDir =
                    File.createTempFile("opsx-ff", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-ff", FfTask::class.java)
                task.extension = createExtension()

                val prompt = task.buildFfPrompt("", "change context only", "opsx/changes/test")

                then("still includes change context") {
                    prompt shouldContain "change context only"
                }

                then("still includes fast-forward instructions") {
                    prompt shouldContain "Fast-forward this change"
                }
            }

            `when`("both context and change context are non-empty") {
                val projectDir =
                    File.createTempFile("opsx-ff", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-ff", FfTask::class.java)
                task.extension = createExtension()

                val prompt =
                    task.buildFfPrompt("large context blob", "change with proposal and design", "opsx/changes/test")

                then("includes all sections via PromptBuilder.build") {
                    prompt shouldContain "# Codebase Context"
                    prompt shouldContain "# Change"
                    prompt shouldContain "# Instructions"
                }

                then("includes separators") {
                    prompt shouldContain "---"
                }
            }
        }
    })
