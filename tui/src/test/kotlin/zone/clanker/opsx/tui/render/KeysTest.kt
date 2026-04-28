package zone.clanker.opsx.tui.render

import com.github.ajalt.mordant.input.KeyboardEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class KeysTest :
    FunSpec({

        test("UP has key ArrowUp") {
            Keys.UP shouldBe KeyboardEvent("ArrowUp")
        }

        test("DOWN has key ArrowDown") {
            Keys.DOWN shouldBe KeyboardEvent("ArrowDown")
        }

        test("LEFT has key ArrowLeft") {
            Keys.LEFT shouldBe KeyboardEvent("ArrowLeft")
        }

        test("RIGHT has key ArrowRight") {
            Keys.RIGHT shouldBe KeyboardEvent("ArrowRight")
        }

        test("ENTER has key Enter") {
            Keys.ENTER shouldBe KeyboardEvent("Enter")
        }

        test("ESCAPE has key Escape") {
            Keys.ESCAPE shouldBe KeyboardEvent("Escape")
        }

        test("BACKSPACE has key Backspace") {
            Keys.BACKSPACE shouldBe KeyboardEvent("Backspace")
        }

        test("TAB has key Tab") {
            Keys.TAB shouldBe KeyboardEvent("Tab")
        }

        test("SPACE has key space character") {
            Keys.SPACE shouldBe KeyboardEvent(" ")
        }
    })
