package zone.clanker.opsx.task

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import org.gradle.testfixtures.ProjectBuilder
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.Change
import java.io.File

private fun createExtension(): Opsx.SettingsExtension = Opsx.SettingsExtension()

private fun tempDir(prefix: String): File =
    File.createTempFile(prefix, "").apply {
        delete()
        mkdirs()
        deleteOnExit()
    }

class BulkArchiveTaskTest :
    BehaviorSpec({

        given("BulkArchiveTask.findArchivable") {

            `when`("list contains completed, done, and verified changes") {
                val projectDir = tempDir("opsx-bulk-proj")
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-bulk-archive", BulkArchiveTask::class.java)
                task.extension = createExtension()

                val changes =
                    listOf(
                        Change("c1", "completed", emptyList(), tempDir("chg1")),
                        Change("c2", "done", emptyList(), tempDir("chg2")),
                        Change("c3", "verified", emptyList(), tempDir("chg3")),
                    )

                then("returns all three") {
                    val result = task.findArchivable(changes)
                    result shouldHaveSize 3
                    result.map { it.name } shouldContainExactlyInAnyOrder listOf("c1", "c2", "c3")
                }
            }

            `when`("list contains only non-archivable statuses") {
                val projectDir = tempDir("opsx-bulk-proj")
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-bulk-archive", BulkArchiveTask::class.java)
                task.extension = createExtension()

                val changes =
                    listOf(
                        Change("c1", "active", emptyList(), tempDir("chg1")),
                        Change("c2", "draft", emptyList(), tempDir("chg2")),
                        Change("c3", "pending", emptyList(), tempDir("chg3")),
                        Change("c4", "in-progress", emptyList(), tempDir("chg4")),
                    )

                then("returns empty list") {
                    task.findArchivable(changes).shouldBeEmpty()
                }
            }

            `when`("list is empty") {
                val projectDir = tempDir("opsx-bulk-proj")
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-bulk-archive", BulkArchiveTask::class.java)
                task.extension = createExtension()

                then("returns empty list") {
                    task.findArchivable(emptyList()).shouldBeEmpty()
                }
            }

            `when`("list is a mix of archivable and non-archivable") {
                val projectDir = tempDir("opsx-bulk-proj")
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-bulk-archive", BulkArchiveTask::class.java)
                task.extension = createExtension()

                val changes =
                    listOf(
                        Change("archivable-1", "completed", emptyList(), tempDir("arc1")),
                        Change("active-1", "active", emptyList(), tempDir("arc2")),
                        Change("archivable-2", "verified", emptyList(), tempDir("arc3")),
                        Change("draft-1", "draft", emptyList(), tempDir("arc4")),
                    )

                then("returns only archivable changes") {
                    val result = task.findArchivable(changes)
                    result shouldHaveSize 2
                    result.map { it.name } shouldContainExactlyInAnyOrder
                        listOf("archivable-1", "archivable-2")
                }
            }

            `when`("list contains archived changes") {
                val projectDir = tempDir("opsx-bulk-proj")
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-bulk-archive", BulkArchiveTask::class.java)
                task.extension = createExtension()

                val changes =
                    listOf(
                        Change("already-archived", "archived", emptyList(), tempDir("arc")),
                    )

                then("does not include already-archived changes") {
                    task.findArchivable(changes).shouldBeEmpty()
                }
            }
        }
    })
