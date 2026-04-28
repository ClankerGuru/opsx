package zone.clanker.opsx.cli.init.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readString
import kotlinx.io.writeString

class MarkerBlockTest :
    FunSpec({

        lateinit var filePath: Path

        beforeEach {
            filePath = Path(SystemTemporaryDirectory, "marker-${System.nanoTime()}.md")
        }

        afterEach { SystemFileSystem.delete(filePath, mustExist = false) }

        test("upsert into empty file") {
            SystemFileSystem.sink(filePath).buffered().use { it.writeString("") }
            MarkerBlock.upsert(filePath, "hello")
            SystemFileSystem.source(filePath).buffered().use { it.readString() } shouldContain "hello"
            SystemFileSystem.source(filePath).buffered().use { it.readString() } shouldContain "<!-- >>> opsx >>> -->"
            SystemFileSystem.source(filePath).buffered().use { it.readString() } shouldContain "<!-- <<< opsx <<< -->"
        }

        test("upsert preserves existing content") {
            SystemFileSystem.sink(filePath).buffered().use { it.writeString("# My Doc\n\nSome content.\n") }
            MarkerBlock.upsert(filePath, "injected")
            val text = SystemFileSystem.source(filePath).buffered().use { it.readString() }
            text shouldContain "# My Doc"
            text shouldContain "injected"
        }

        test("upsert replaces existing marker block") {
            SystemFileSystem.sink(filePath).buffered().use {
                it.writeString("before\n<!-- >>> opsx >>> -->\nold\n<!-- <<< opsx <<< -->\nafter\n")
            }
            MarkerBlock.upsert(filePath, "new")
            val text = SystemFileSystem.source(filePath).buffered().use { it.readString() }
            text shouldContain "new"
            text shouldNotContain "old"
            text shouldContain "before"
            text shouldContain "after"
        }

        test("remove strips marker and keeps surrounding content") {
            SystemFileSystem.sink(filePath).buffered().use {
                it.writeString("before\n<!-- >>> opsx >>> -->\nstuff\n<!-- <<< opsx <<< -->\nafter\n")
            }
            MarkerBlock.remove(filePath)
            val text = SystemFileSystem.source(filePath).buffered().use { it.readString() }
            text shouldContain "before"
            text shouldContain "after"
            text shouldNotContain "opsx"
        }

        test("remove deletes file when only marker content") {
            SystemFileSystem.sink(filePath).buffered().use {
                it.writeString("<!-- >>> opsx >>> -->\nonly this\n<!-- <<< opsx <<< -->\n")
            }
            MarkerBlock.remove(filePath)
            SystemFileSystem.exists(filePath) shouldBe false
        }
    })
