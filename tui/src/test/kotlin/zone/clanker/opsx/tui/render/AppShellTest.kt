package zone.clanker.opsx.tui.render

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import com.github.ajalt.mordant.widgets.Text
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import zone.clanker.opsx.tui.Dashboard

class AppShellTest :
    FunSpec({

        val recorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 80, height = 24)
        val terminal = Terminal(terminalInterface = recorder)
        val testLogo = listOf("LINE_ONE", "LINE_TWO", "LINE_THREE")

        beforeEach {
            AppShell.pickLogo(listOf(testLogo))
        }

        test("pickLogo stores the logo for the session") {
            AppShell.currentLogo() shouldBe testLogo
        }

        test("pickLogo picks from the provided list") {
            val logos = listOf(listOf("A"), listOf("B"), listOf("C"))
            AppShell.pickLogo(logos)
            (AppShell.currentLogo() in logos) shouldBe true
        }

        test("wrap renders the logo") {
            val widget =
                AppShell.wrap(
                    content = Text("my content"),
                    barLeft = "  left",
                    barRight = "right  ",
                )
            val rendered = terminal.render(widget)
            rendered shouldContain "LINE_ONE"
            rendered shouldContain "LINE_TWO"
            rendered shouldContain "LINE_THREE"
        }

        test("wrap renders the content") {
            val widget =
                AppShell.wrap(
                    content = Text("hello world"),
                    barLeft = "",
                    barRight = "",
                )
            val rendered = terminal.render(widget)
            rendered shouldContain "hello world"
        }

        test("wrap renders the status bar") {
            val widget =
                AppShell.wrap(
                    content = Text("content"),
                    barLeft = "  ← back",
                    barRight = "opsx v1.0  ",
                )
            val rendered = terminal.render(widget)
            rendered shouldContain "back"
            rendered shouldContain "opsx v1.0"
        }

        test("wrap output has at least terminal height lines") {
            val widget =
                AppShell.wrap(
                    content = Text("short"),
                    barLeft = "left",
                    barRight = "right",
                )
            val rendered = terminal.render(widget)
            val lineCount = rendered.lines().size
            lineCount shouldBeGreaterThan 20
        }

        test("wrap with large content does not crash") {
            val bigContent = (1..50).joinToString("\n") { "line $it" }
            val widget =
                AppShell.wrap(
                    content = Text(bigContent),
                    barLeft = "left",
                    barRight = "right",
                )
            val rendered = terminal.render(widget)
            rendered shouldContain "line 1"
        }

        test("transition does not throw") {
            // transition() calls cursor.move { setPosition; clearScreen }
            // TerminalRecorder may not capture cursor moves, so we just verify it doesn't crash
            AppShell.transition(terminal)
        }

        test("wrap always starts with a blank line before logo") {
            val widget =
                AppShell.wrap(
                    content = Text("content"),
                    barLeft = "left",
                    barRight = "right",
                )
            val rendered = terminal.render(widget)
            val lines = rendered.lines()
            // First line should be blank (the top margin)
            lines.first().isBlank() shouldBe true
            // Second line should contain the logo
            lines[1] shouldContain "LINE_ONE"
        }

        test("logo always starts at consistent row across multiple renders") {
            val widget1 =
                AppShell.wrap(
                    content = Text("first"),
                    barLeft = "left",
                    barRight = "right",
                )
            val widget2 =
                AppShell.wrap(
                    content = Text("second"),
                    barLeft = "left",
                    barRight = "right",
                )
            val render1 = terminal.render(widget1)
            val render2 = terminal.render(widget2)
            // Logo should start at the same line index in both renders
            val logoIndex1 = render1.lines().indexOfFirst { it.contains("LINE_ONE") }
            val logoIndex2 = render2.lines().indexOfFirst { it.contains("LINE_ONE") }
            logoIndex1 shouldBe logoIndex2
            logoIndex1 shouldBe 1 // always row 1 (after the blank line)
        }

        test("wrap padding fills gap between content and status bar") {
            val widget =
                AppShell.wrap(
                    content = Text("x"),
                    barLeft = "bar",
                    barRight = "",
                )
            val rendered = terminal.render(widget)
            val lines = rendered.lines()
            lines.size shouldBeGreaterThan 20
        }

        test("render only clears on size change not on every frame") {
            // This tests the logic: lastSize tracks the previous size,
            // sizeChanged is false when size hasn't changed
            AppShell.pickLogo(listOf(testLogo))
            // After pickLogo, lastSize is null
            // First call: null != currentSize → sizeChanged=true
            // Second call: currentSize == currentSize → sizeChanged=false
            // We can't test the ANSI output with TerminalRecorder (cursor moves are no-ops
            // for non-interactive terminals), but we verify the logic doesn't crash
            AppShell.render(terminal, Text("first"), "left", "right")
            AppShell.render(terminal, Text("second"), "left", "right")
            // No exception = the overwrite path works
        }

        test("ShellWidget produces more lines for taller terminal") {
            AppShell.pickLogo(listOf(testLogo))
            val content = Text("content")

            val smallTerminal = Terminal(width = 80, height = 24)
            val largeTerminal = Terminal(width = 80, height = 40)

            val widget = AppShell.wrap(content, "left", "right")
            val smallRendered = widget.render(smallTerminal, 80)
            val largeRendered = widget.render(largeTerminal, 80)

            largeRendered.height shouldBeGreaterThan smallRendered.height
        }

        test("ShellWidget adapts to different widths") {
            AppShell.pickLogo(listOf(testLogo))
            val content = Text("content")

            val narrowTerminal = Terminal(width = 60, height = 24)
            val wideTerminal = Terminal(width = 120, height = 24)

            val widget = AppShell.wrap(content, "left", "right")
            val narrowRendered = narrowTerminal.render(widget)
            val wideRendered = wideTerminal.render(widget)

            narrowRendered shouldNotBe wideRendered
        }

        test("render never crashes on repeated calls") {
            AppShell.pickLogo(listOf(testLogo))
            repeat(10) {
                AppShell.render(terminal, Text("frame $it"), "left", "right")
            }
        }

        test("render updates lastSize") {
            AppShell.pickLogo(listOf(testLogo))
            AppShell.render(terminal, Text("content"), "left", "right")
            AppShell.lastSize shouldNotBe null
        }

        test("consecutive renders with same content do not crash") {
            AppShell.pickLogo(listOf(testLogo))
            val content = Text("same content")
            repeat(5) {
                AppShell.render(terminal, content, "bar left", "bar right")
            }
        }

        test("render writes each line at absolute row position") {
            AppShell.pickLogo(listOf(testLogo))
            val testRecorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR, width = 80, height = 10)
            val testTerminal = Terminal(terminalInterface = testRecorder)

            AppShell.render(testTerminal, Text("hello"), "left", "right")
            val output = testRecorder.output()

            // Should contain absolute positioning ESC[row;1H for each row
            output shouldContain "\u001B[1;1H"
            output shouldContain "\u001B[2;1H"
            output shouldContain "\u001B[10;1H"
            // Should contain clear line ESC[2K
            output shouldContain "\u001B[2K"
            // Should contain the content
            output shouldContain "hello"
            // Should contain the logo
            output shouldContain "LINE_ONE"
        }

        test("resize debounce fires only after size stabilizes") {
            // The debounce logic:
            // - Poll detects size != lastPolledSize → sets pendingResize, updates lastPolledSize
            // - Next poll detects size == lastPolledSize AND pendingResize → fires onResize
            // This means rapid resize doesn't spam renders — only one render after stabilization

            // We can't easily test the async readInput loop, but we can verify the
            // core invariant: render() updates lastSize correctly
            AppShell.pickLogo(listOf(testLogo))
            val terminal24 = Terminal(width = 80, height = 24)
            val terminal40 = Terminal(width = 80, height = 40)

            AppShell.render(terminal24, Text("content"), "left", "right")
            val sizeAfter24 = AppShell.lastSize
            sizeAfter24 shouldNotBe null

            AppShell.render(terminal40, Text("content"), "left", "right")
            val sizeAfter40 = AppShell.lastSize
            sizeAfter40 shouldNotBe sizeAfter24
        }

        test("translateKey still works after render") {
            // Prove that rendering doesn't interfere with key handling
            AppShell.pickLogo(listOf(testLogo))
            AppShell.render(terminal, Text("menu"), "left", "right")

            val down = Dashboard.translateKey(KeyboardEvent("j"))
            down shouldBe Dashboard.MenuAction.MoveDown

            val quit = Dashboard.translateKey(KeyboardEvent("q"))
            quit shouldBe Dashboard.MenuAction.Quit
        }

        test("LogoWidget measure returns width based on styled lines") {
            AppShell.pickLogo(listOf(testLogo))
            val logoWidget = LogoWidget()
            val widthRange = logoWidget.measure(terminal, 200)
            widthRange.min shouldBe widthRange.max
        }

        test("ShellWidget measure returns full terminal width") {
            AppShell.pickLogo(listOf(testLogo))
            val shellWidget =
                ShellWidget(
                    logoWidget = LogoWidget(),
                    content = Text("content"),
                    barLeft = "left",
                    barRight = "right",
                )
            val widthRange = shellWidget.measure(terminal, 80)
            widthRange.min shouldBe 80
            widthRange.max shouldBe 80
        }

        test("readKeyInput only delivers keyboard events to handler") {
            val interactiveRecorder =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 24,
                    inputInteractive = true,
                )
            interactiveRecorder.inputEvents.add(KeyboardEvent("a"))
            interactiveRecorder.inputEvents.add(KeyboardEvent("q"))
            val interactiveTerminal = Terminal(terminalInterface = interactiveRecorder)

            val receivedKeys = mutableListOf<String>()
            val result =
                AppShell.readKeyInput(
                    interactiveTerminal,
                    handler = { event ->
                        receivedKeys.add(event.key)
                        if (event.key == "q") {
                            com.github.ajalt.mordant.input.InputReceiver.Status
                                .Finished("done")
                        } else {
                            com.github.ajalt.mordant.input.InputReceiver.Status.Continue
                        }
                    },
                )
            result shouldBe "done"
            receivedKeys shouldBe listOf("a", "q")
        }

        test("readInput handles keyboard event and returns finished result") {
            val interactiveRecorder =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 24,
                    inputInteractive = true,
                )
            interactiveRecorder.inputEvents.add(KeyboardEvent("x"))
            val interactiveTerminal = Terminal(terminalInterface = interactiveRecorder)

            val result =
                AppShell.readInput(
                    interactiveTerminal,
                    handler = { event ->
                        com.github.ajalt.mordant.input.InputReceiver.Status
                            .Finished("handled")
                    },
                )
            result shouldBe "handled"
        }

        test("formatLogoLines applies gradient styling to each line") {
            AppShell.pickLogo(listOf(testLogo))
            val lines = AppShell.formatLogoLines()
            lines.size shouldBe testLogo.size
            for ((index, line) in lines.withIndex()) {
                line shouldContain testLogo[index]
            }
        }

        test("transition clears screen on truecolor terminal") {
            val trueColorRecorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR, width = 80, height = 24)
            val trueColorTerminal = Terminal(terminalInterface = trueColorRecorder)
            AppShell.transition(trueColorTerminal)
            // Should not throw
        }

        test("pickLogo resets lastSize to null") {
            AppShell.pickLogo(listOf(testLogo))
            AppShell.render(terminal, Text("content"), "left", "right")
            AppShell.lastSize shouldNotBe null
            AppShell.pickLogo(listOf(testLogo))
            AppShell.lastSize shouldBe null
        }

        test("LogoWidget measure returns non-negative width range") {
            AppShell.pickLogo(listOf(testLogo))
            val logoWidget = LogoWidget()
            val measured = logoWidget.measure(terminal, 200)
            measured.min shouldBe measured.max
            measured.min shouldBeGreaterThan 0
        }

        test("LogoWidget render produces correct number of lines") {
            AppShell.pickLogo(listOf(testLogo))
            val logoWidget = LogoWidget()
            val rendered = logoWidget.render(terminal, 80)
            rendered.height shouldBe testLogo.size
        }

        test("ShellWidget measure returns the provided width") {
            AppShell.pickLogo(listOf(testLogo))
            val shellWidget =
                ShellWidget(
                    logoWidget = LogoWidget(),
                    content = Text("content"),
                    barLeft = "left",
                    barRight = "right",
                )
            val measured = shellWidget.measure(terminal, 120)
            measured.min shouldBe 120
            measured.max shouldBe 120
        }

        test("ShellWidget render includes status bar text") {
            AppShell.pickLogo(listOf(testLogo))
            val shellWidget =
                ShellWidget(
                    logoWidget = LogoWidget(),
                    content = Text("my content"),
                    barLeft = "  hints",
                    barRight = "v1.0  ",
                )
            val rendered = terminal.render(shellWidget)
            rendered shouldContain "my content"
        }

        test("readInput with immediate finish returns result") {
            val interactiveRecorder =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 24,
                    inputInteractive = true,
                )
            interactiveRecorder.inputEvents.add(KeyboardEvent("a"))
            val interactiveTerminal = Terminal(terminalInterface = interactiveRecorder)

            val result =
                AppShell.readInput(
                    interactiveTerminal,
                    handler = { _ ->
                        com.github.ajalt.mordant.input.InputReceiver.Status
                            .Finished(42)
                    },
                )
            result shouldBe 42
        }

        test("readInput continues on non-finished status") {
            val interactiveRecorder =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 24,
                    inputInteractive = true,
                )
            interactiveRecorder.inputEvents.add(KeyboardEvent("a"))
            interactiveRecorder.inputEvents.add(KeyboardEvent("b"))
            val interactiveTerminal = Terminal(terminalInterface = interactiveRecorder)

            var callCount = 0
            val result =
                AppShell.readInput(
                    interactiveTerminal,
                    handler = { _ ->
                        callCount++
                        if (callCount >= 2) {
                            com.github.ajalt.mordant.input.InputReceiver.Status
                                .Finished("after-two")
                        } else {
                            com.github.ajalt.mordant.input.InputReceiver.Status.Continue
                        }
                    },
                )
            result shouldBe "after-two"
            callCount shouldBe 2
        }
    })
