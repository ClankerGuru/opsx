package zone.clanker.opsx.task

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testfixtures.ProjectBuilder
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.Change
import zone.clanker.opsx.model.ChangeConfig
import zone.clanker.opsx.workflow.ChangeReader
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
                File(changeDir, "tasks.md").writeText("Tasks content")

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
                File(changeDir, "tasks.md").writeText("Tasks content")

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
                File(changeDir, "tasks.md").writeText("Tasks content")

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

                then("returns all three missing files") {
                    val missing = task.validateForApply(change)
                    missing shouldHaveSize 3
                    missing shouldContain "proposal.md"
                    missing shouldContain "design.md"
                    missing shouldContain "tasks.md"
                }
            }
        }

        given("ApplyTask.resolveTarget") {

            `when`("input matches a change name") {
                val projectDir =
                    File.createTempFile("opsx-resolve", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changeDir = File(projectDir, "opsx/changes/my-change").apply { mkdirs() }
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(name = "my-change", status = "active"),
                )

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-apply", ApplyTask::class.java)
                task.extension = createExtension()
                val reader = ChangeReader(projectDir, createExtension())

                val (change, taskId) = task.resolveTarget(reader, "my-change")

                then("returns the change with null task ID") {
                    change.name shouldBe "my-change"
                    taskId.shouldBeNull()
                }
            }

            `when`("input matches a task ID") {
                val projectDir =
                    File.createTempFile("opsx-resolve", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changeDir = File(projectDir, "opsx/changes/my-change").apply { mkdirs() }
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(name = "my-change", status = "active"),
                )
                File(changeDir, "tasks.md").writeText(
                    """
                    |# Tasks
                    |
                    |- [ ] a1b2c3d4e5 | Some task
                    |    Description.
                    |  depends: none
                    """.trimMargin(),
                )

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-apply", ApplyTask::class.java)
                task.extension = createExtension()
                val reader = ChangeReader(projectDir, createExtension())

                val (change, taskId) = task.resolveTarget(reader, "a1b2c3d4e5")

                then("returns the change and the task ID") {
                    change.name shouldBe "my-change"
                    taskId shouldBe "a1b2c3d4e5"
                }
            }

            `when`("input does not match anything") {
                val projectDir =
                    File.createTempFile("opsx-resolve", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-apply", ApplyTask::class.java)
                task.extension = createExtension()
                val reader = ChangeReader(projectDir, createExtension())

                then("throws error") {
                    val ex = shouldThrow<IllegalStateException> { task.resolveTarget(reader, "nonexistent") }
                    ex.message shouldContain "not found"
                }
            }
        }
    })
