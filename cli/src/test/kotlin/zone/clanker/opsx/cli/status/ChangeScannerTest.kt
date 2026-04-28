package zone.clanker.opsx.cli.status

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString

class ChangeScannerTest :
    FunSpec({

        lateinit var rootPath: Path

        beforeEach {
            rootPath = Path(SystemTemporaryDirectory, "opsx-scan-test-${System.nanoTime()}")
            SystemFileSystem.createDirectories(rootPath)
        }

        afterEach {
            java.io.File(rootPath.toString()).deleteRecursively()
        }

        test("scan returns empty list when changes directory is missing") {
            ChangeScanner.scan(rootPath) shouldBe emptyList()
        }

        test("scan reads status and task progress") {
            writeChange(
                path = Path(rootPath, "opsx/changes/active-change"),
                status = "active",
                tasks =
                    """
                    |- [x] finished
                    |- [ ] pending
                    |- not a checkbox
                    """.trimMargin(),
            )

            val entries = ChangeScanner.scan(rootPath)

            entries shouldHaveSize 1
            entries.single() shouldBe ChangeEntry("active-change", "active", done = 1, total = 2)
        }

        test("scan includes archive only when requested and filters by status") {
            writeChange(Path(rootPath, "opsx/changes/draft-change"), status = "draft")
            writeChange(Path(rootPath, "opsx/archive/archived-change"), status = "archived")

            ChangeScanner.scan(rootPath).map { it.name } shouldContainExactly listOf("draft-change")
            val archivedNames =
                ChangeScanner
                    .scan(rootPath, includeArchive = true, onlyStatus = "archived")
                    .map { it.name }
            archivedNames shouldContainExactly listOf("archived-change")
        }

        test("scan defaults missing status to draft and sorts by lifecycle order") {
            writeChange(Path(rootPath, "opsx/changes/z-draft"), status = null)
            writeChange(Path(rootPath, "opsx/changes/a-active"), status = "active")
            writeChange(Path(rootPath, "opsx/changes/m-completed"), status = "completed")

            val entries = ChangeScanner.scan(rootPath)

            entries.map { it.name } shouldContainExactly listOf("a-active", "z-draft", "m-completed")
            entries.first { it.name == "z-draft" }.status shouldBe "draft"
        }
    })

private fun writeChange(
    path: Path,
    status: String?,
    tasks: String = "- [ ] task\n",
) {
    val yaml = status?.let { "status: $it\n" } ?: "name: missing-status\n"
    SystemFileSystem.createDirectories(path)
    SystemFileSystem.sink(Path(path, ".opsx.yaml")).buffered().use { it.writeString(yaml) }
    SystemFileSystem.sink(Path(path, "tasks.md")).buffered().use { it.writeString(tasks) }
}
