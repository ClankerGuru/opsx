package zone.clanker.opsx.workflow

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import zone.clanker.opsx.model.TaskStatus
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class ChangeLoggerTest :
    BehaviorSpec({

        given("ChangeLogger.append") {

            `when`("appending to a new log") {
                val tempDir =
                    File.createTempFile("change", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                ChangeLogger.append(tempDir, "a1b2c3d4e5", "Test parse", TaskStatus.IN_PROGRESS, "started just now")

                then("creates log.md with header and entry") {
                    val logFile = File(tempDir, "log.md")
                    logFile.exists() shouldBe true
                    val content = logFile.readText()
                    content shouldContain "# Log"
                    content shouldContain "- [>] a1b2c3d4e5 | Test parse — started just now"
                }
            }

            `when`("appending multiple entries") {
                val tempDir =
                    File.createTempFile("change", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                ChangeLogger.append(tempDir, "a1b2c3d4e5", "Test parse", TaskStatus.IN_PROGRESS, "started")
                ChangeLogger.append(tempDir, "a1b2c3d4e5", "Test parse", TaskStatus.DONE, "done in 22s")

                then("both entries are present") {
                    val content = File(tempDir, "log.md").readText()
                    content shouldContain "- [>] a1b2c3d4e5 | Test parse — started"
                    content shouldContain "- [x] a1b2c3d4e5 | Test parse — done in 22s"
                }
            }

            `when`("concurrent appends from multiple threads") {
                val tempDir =
                    File.createTempFile("change", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val threadCount = 10
                val latch = CountDownLatch(threadCount)
                val executor = Executors.newFixedThreadPool(threadCount)

                (0 until threadCount).forEach { i ->
                    executor.submit {
                        val id = "task${i.toString().padStart(6, '0')}"
                        ChangeLogger.append(tempDir, id, "Task $i", TaskStatus.DONE, "done")
                        latch.countDown()
                    }
                }
                latch.await()
                executor.shutdown()

                then("all entries are present without corruption") {
                    val lines =
                        File(tempDir, "log.md")
                            .readLines()
                            .filter { it.startsWith("- [x]") }
                    lines.size shouldBe threadCount
                }
            }
        }
    })
