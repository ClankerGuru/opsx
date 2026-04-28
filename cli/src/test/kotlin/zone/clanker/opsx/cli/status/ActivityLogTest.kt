package zone.clanker.opsx.cli.status

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readString

class ActivityLogTest :
    FunSpec({

        lateinit var rootPath: Path

        beforeEach {
            rootPath = Path(SystemTemporaryDirectory, "opsx-test-${System.nanoTime()}")
            SystemFileSystem.createDirectories(Path(rootPath, "opsx/changes/test-change"))
        }

        afterEach { java.io.File(rootPath.toString()).deleteRecursively() }

        test("append writes a single JSON line") {
            val event = ActivityEvent("2026-04-22T00:00:00Z", "lead", State.START, "abc", "scaffold")
            ActivityLog.append(rootPath, "test-change", event)

            val text =
                SystemFileSystem
                    .source(Path(rootPath, "opsx/changes/test-change/activity.log"))
                    .buffered()
                    .use { it.readString() }
            text shouldContain """"agent":"lead""""
            text shouldContain """"state":"start""""
            text.lines().filter { it.isNotBlank() } shouldHaveSize 1
        }

        test("append accumulates events") {
            ActivityLog.append(rootPath, "test-change", ActivityEvent("t1", "a", State.START))
            ActivityLog.append(rootPath, "test-change", ActivityEvent("t2", "a", State.DONE))

            val events = ActivityLog.read(rootPath, "test-change")
            events shouldHaveSize 2
            events[0].state shouldBe State.START
            events[1].state shouldBe State.DONE
        }

        test("read returns empty for missing change") {
            ActivityLog.read(rootPath, "nonexistent") shouldBe emptyList()
        }

        test("optional fields omitted when null") {
            val event = ActivityEvent("t1", "lead", State.DONE)
            ActivityLog.append(rootPath, "test-change", event)

            val text =
                SystemFileSystem
                    .source(Path(rootPath, "opsx/changes/test-change/activity.log"))
                    .buffered()
                    .use { it.readString() }
            text shouldContain """"agent":"lead""""
            // task and desc should not appear
            text.shouldNotContainKey("task")
            text.shouldNotContainKey("desc")
        }
    })

private fun String.shouldNotContainKey(key: String) {
    this shouldBe this.replace(Regex(""""$key"\s*:"""), "")
}
