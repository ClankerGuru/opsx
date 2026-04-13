package zone.clanker.opsx.task

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.gradle.testfixtures.ProjectBuilder
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.Change
import java.io.File

private fun createExtension(): Opsx.SettingsExtension = Opsx.SettingsExtension()

class ApplyTaskTest :
    BehaviorSpec({

        given("ApplyTask.validateForApply") {

            `when`("both proposal.md and design.md exist") {
                val changeDir =
                    File.createTempFile("opsx-change", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                File(changeDir, "proposal.md").writeText("Proposal content")
                File(changeDir, "design.md").writeText("Design content")

                val change = Change("test", "active", emptyList(), changeDir)

                val projectDir =
                    File.createTempFile("opsx-apply", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-apply", ApplyTask::class.java)
                task.extension = createExtension()

                then("returns empty list") {
                    task.validateForApply(change).shouldBeEmpty()
                }
            }

            `when`("proposal.md is missing") {
                val changeDir =
                    File.createTempFile("opsx-change", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                File(changeDir, "design.md").writeText("Design content")

                val change = Change("test", "active", emptyList(), changeDir)

                val projectDir =
                    File.createTempFile("opsx-apply", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-apply", ApplyTask::class.java)
                task.extension = createExtension()

                then("returns list with proposal.md") {
                    val missing = task.validateForApply(change)
                    missing shouldHaveSize 1
                    missing shouldContain "proposal.md"
                }
            }

            `when`("design.md is missing") {
                val changeDir =
                    File.createTempFile("opsx-change", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                File(changeDir, "proposal.md").writeText("Proposal content")

                val change = Change("test", "active", emptyList(), changeDir)

                val projectDir =
                    File.createTempFile("opsx-apply", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-apply", ApplyTask::class.java)
                task.extension = createExtension()

                then("returns list with design.md") {
                    val missing = task.validateForApply(change)
                    missing shouldHaveSize 1
                    missing shouldContain "design.md"
                }
            }

            `when`("both files are missing") {
                val changeDir =
                    File.createTempFile("opsx-change", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }

                val change = Change("test", "active", emptyList(), changeDir)

                val projectDir =
                    File.createTempFile("opsx-apply", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-apply", ApplyTask::class.java)
                task.extension = createExtension()

                then("returns both missing files") {
                    val missing = task.validateForApply(change)
                    missing shouldHaveSize 2
                    missing shouldContain "proposal.md"
                    missing shouldContain "design.md"
                }
            }
        }

        given("ApplyTask.buildApplyPrompt") {

            `when`("prompt is built") {
                val projectDir =
                    File.createTempFile("opsx-apply", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-apply", ApplyTask::class.java)
                task.extension = createExtension()

                val changeDir = File(projectDir, "opsx/changes/test-change").apply { mkdirs() }
                val prompt = task.buildApplyPrompt("codebase context", "change context", changeDir)

                then("includes codebase context") {
                    prompt shouldContain "codebase context"
                }

                then("includes change context") {
                    prompt shouldContain "change context"
                }

                then("includes change directory path") {
                    prompt shouldContain "opsx/changes/test-change"
                }

                then("includes implementation instructions") {
                    prompt shouldContain "Implement the change"
                    prompt shouldContain "Follow the design faithfully"
                    prompt shouldContain "tasks.md"
                }
            }
        }
    })
