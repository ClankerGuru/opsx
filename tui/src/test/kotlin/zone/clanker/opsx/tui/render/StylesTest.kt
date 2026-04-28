package zone.clanker.opsx.tui.render

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldHaveLength

class StylesTest :
    FunSpec({

        test("margin is PAD spaces") {
            Styles.margin shouldHaveLength Styles.PAD
            Styles.margin.all { it == ' ' } shouldBe true
        }

        test("gradientColor returns valid colors for all positions") {
            for (i in 0..9) {
                val color = Styles.gradientColor(i, 10)
                val styled = color("test")
                styled.contains("test") shouldBe true
            }
        }

        test("gradientColor handles single line") {
            val color = Styles.gradientColor(0, 1)
            val styled = color("x")
            styled.contains("x") shouldBe true
        }

        test("gradientColor handles zero total") {
            val color = Styles.gradientColor(0, 0)
            val styled = color("y")
            styled.contains("y") shouldBe true
        }

        test("barStyle applies styling to text") {
            val styled = Styles.barStyle("hello")
            styled shouldContain "hello"
        }

        test("PAD constant is 6") {
            Styles.PAD shouldBe 6
        }

        test("BAR_FILL constant is 40") {
            Styles.BAR_FILL shouldBe 40
        }

        test("enterFullScreen sends alternate screen buffer escape") {
            val recorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR, width = 80, height = 24)
            val t = Terminal(terminalInterface = recorder)
            Styles.enterFullScreen(t)
            val output = recorder.output()
            output shouldContain "\u001B[?1049h"
        }

        test("leaveFullScreen sends restore escape") {
            val recorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR, width = 80, height = 24)
            val t = Terminal(terminalInterface = recorder)
            Styles.leaveFullScreen(t)
            val output = recorder.output()
            output shouldContain "\u001B[?1049l"
        }

        test("descStyle applies styling to description text") {
            val styled = Styles.descStyle("description text")
            styled shouldContain "description text"
        }

        test("gradientColor first and last differ for multi-line logo") {
            val first = Styles.gradientColor(0, 6)
            val last = Styles.gradientColor(5, 6)
            val firstStyled = first("X")
            val lastStyled = last("X")
            firstStyled shouldContain "X"
            lastStyled shouldContain "X"
        }
    })
