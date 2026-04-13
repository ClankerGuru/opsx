package zone.clanker.opsx.task

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.gradle.testfixtures.ProjectBuilder
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.Change
import java.io.File

private fun createExtension(): Opsx.SettingsExtension = Opsx.SettingsExtension()

class ArchiveTaskTest :
    BehaviorSpec({

        given("ArchiveTask.validateForArchive") {

            `when`("status is completed") {
                val changeDir =
                    File.createTempFile("opsx-archive", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val change = Change("test", "completed", emptyList(), changeDir)

                val projectDir =
                    File.createTempFile("opsx-archive-proj", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-archive", ArchiveTask::class.java)
                task.extension = createExtension()

                then("returns true") {
                    task.validateForArchive(change) shouldBe true
                }
            }

            `when`("status is done") {
                val changeDir =
                    File.createTempFile("opsx-archive", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val change = Change("test", "done", emptyList(), changeDir)

                val projectDir =
                    File.createTempFile("opsx-archive-proj", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-archive", ArchiveTask::class.java)
                task.extension = createExtension()

                then("returns true") {
                    task.validateForArchive(change) shouldBe true
                }
            }

            `when`("status is verified") {
                val changeDir =
                    File.createTempFile("opsx-archive", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val change = Change("test", "verified", emptyList(), changeDir)

                val projectDir =
                    File.createTempFile("opsx-archive-proj", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-archive", ArchiveTask::class.java)
                task.extension = createExtension()

                then("returns true") {
                    task.validateForArchive(change) shouldBe true
                }
            }

            `when`("status is archived") {
                val changeDir =
                    File.createTempFile("opsx-archive", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val change = Change("test", "archived", emptyList(), changeDir)

                val projectDir =
                    File.createTempFile("opsx-archive-proj", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-archive", ArchiveTask::class.java)
                task.extension = createExtension()

                then("returns true") {
                    task.validateForArchive(change) shouldBe true
                }
            }

            `when`("status is active") {
                val changeDir =
                    File.createTempFile("opsx-archive", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val change = Change("test", "active", emptyList(), changeDir)

                val projectDir =
                    File.createTempFile("opsx-archive-proj", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-archive", ArchiveTask::class.java)
                task.extension = createExtension()

                then("returns false") {
                    task.validateForArchive(change) shouldBe false
                }
            }

            `when`("status is draft") {
                val changeDir =
                    File.createTempFile("opsx-archive", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val change = Change("test", "draft", emptyList(), changeDir)

                val projectDir =
                    File.createTempFile("opsx-archive-proj", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-archive", ArchiveTask::class.java)
                task.extension = createExtension()

                then("returns false") {
                    task.validateForArchive(change) shouldBe false
                }
            }

            `when`("status is pending") {
                val changeDir =
                    File.createTempFile("opsx-archive", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val change = Change("test", "pending", emptyList(), changeDir)

                val projectDir =
                    File.createTempFile("opsx-archive-proj", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-archive", ArchiveTask::class.java)
                task.extension = createExtension()

                then("returns false") {
                    task.validateForArchive(change) shouldBe false
                }
            }

            `when`("status is in-progress") {
                val changeDir =
                    File.createTempFile("opsx-archive", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val change = Change("test", "in-progress", emptyList(), changeDir)

                val projectDir =
                    File.createTempFile("opsx-archive-proj", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-archive", ArchiveTask::class.java)
                task.extension = createExtension()

                then("returns false") {
                    task.validateForArchive(change) shouldBe false
                }
            }
        }
    })
