package zone.clanker.opsx.tui.render

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class ConfirmDialogTest :
    FunSpec({

        val recorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 100, height = 40)
        val terminal = Terminal(terminalInterface = recorder)

        // --- buildWidget ---

        test("buildWidget with selectedYes=true shows focused Yes") {
            val widget = ConfirmDialog.buildWidget("Proceed?", "Yes", "No", selectedYes = true)
            val rendered = terminal.render(widget)
            rendered shouldContain "[ Yes ]"
        }

        test("buildWidget with selectedYes=false shows focused No") {
            val widget = ConfirmDialog.buildWidget("Proceed?", "Yes", "No", selectedYes = false)
            val rendered = terminal.render(widget)
            rendered shouldContain "[ No ]"
        }

        test("buildWidget with selectedYes=true does not focus No") {
            val widget = ConfirmDialog.buildWidget("Proceed?", "Yes", "No", selectedYes = true)
            val rendered = terminal.render(widget)
            rendered shouldNotContain "[ No ]"
        }

        test("buildWidget with selectedYes=false does not focus Yes") {
            val widget = ConfirmDialog.buildWidget("Proceed?", "Yes", "No", selectedYes = false)
            val rendered = terminal.render(widget)
            rendered shouldNotContain "[ Yes ]"
        }

        test("buildWidget contains the prompt text") {
            val widget = ConfirmDialog.buildWidget("Are you sure?", "Yes", "No", selectedYes = true)
            val rendered = terminal.render(widget)
            rendered shouldContain "Are you sure?"
        }

        test("buildWidget contains custom affirmative text") {
            val widget = ConfirmDialog.buildWidget("Delete?", "Confirm", "Cancel", selectedYes = true)
            val rendered = terminal.render(widget)
            rendered shouldContain "[ Confirm ]"
        }

        test("buildWidget contains custom negative text") {
            val widget = ConfirmDialog.buildWidget("Delete?", "Confirm", "Cancel", selectedYes = false)
            val rendered = terminal.render(widget)
            rendered shouldContain "[ Cancel ]"
        }

        test("buildWidget contains both button labels") {
            val widget = ConfirmDialog.buildWidget("OK?", "Accept", "Reject", selectedYes = true)
            val rendered = terminal.render(widget)
            rendered shouldContain "Accept"
            rendered shouldContain "Reject"
        }

        // --- translateKey ---

        test("translateKey maps LEFT to ToggleLeft") {
            ConfirmDialog.translateKey(Keys.LEFT) shouldBe DialogAction.ToggleLeft
        }

        test("translateKey maps h to ToggleLeft") {
            ConfirmDialog.translateKey(KeyboardEvent("h")) shouldBe DialogAction.ToggleLeft
        }

        test("translateKey maps RIGHT to ToggleRight") {
            ConfirmDialog.translateKey(Keys.RIGHT) shouldBe DialogAction.ToggleRight
        }

        test("translateKey maps l to ToggleRight") {
            ConfirmDialog.translateKey(KeyboardEvent("l")) shouldBe DialogAction.ToggleRight
        }

        test("translateKey maps TAB to Toggle") {
            ConfirmDialog.translateKey(Keys.TAB) shouldBe DialogAction.Toggle
        }

        test("translateKey maps ENTER to Submit") {
            ConfirmDialog.translateKey(Keys.ENTER) shouldBe DialogAction.Submit
        }

        test("translateKey maps y to SelectYes") {
            ConfirmDialog.translateKey(KeyboardEvent("y")) shouldBe DialogAction.SelectYes
        }

        test("translateKey maps Y to SelectYes") {
            ConfirmDialog.translateKey(KeyboardEvent("Y")) shouldBe DialogAction.SelectYes
        }

        test("translateKey maps n to SelectNo") {
            ConfirmDialog.translateKey(KeyboardEvent("n")) shouldBe DialogAction.SelectNo
        }

        test("translateKey maps N to SelectNo") {
            ConfirmDialog.translateKey(KeyboardEvent("N")) shouldBe DialogAction.SelectNo
        }

        test("translateKey maps q to Cancel") {
            ConfirmDialog.translateKey(KeyboardEvent("q")) shouldBe DialogAction.Cancel
        }

        test("translateKey maps ESCAPE to Cancel") {
            ConfirmDialog.translateKey(Keys.ESCAPE) shouldBe DialogAction.Cancel
        }

        test("translateKey returns null for unrecognised keys") {
            ConfirmDialog.translateKey(KeyboardEvent("x")).shouldBeNull()
            ConfirmDialog.translateKey(KeyboardEvent("j")).shouldBeNull()
            ConfirmDialog.translateKey(Keys.UP).shouldBeNull()
            ConfirmDialog.translateKey(Keys.DOWN).shouldBeNull()
        }

        // --- show interactive tests ---

        test("show with y key returns true") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("y"))
            val term = Terminal(terminalInterface = rec)
            val result = ConfirmDialog.show(term, "Proceed?")
            result shouldBe true
        }

        test("show with Y key returns true") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Y"))
            val term = Terminal(terminalInterface = rec)
            val result = ConfirmDialog.show(term, "Proceed?")
            result shouldBe true
        }

        test("show with n key returns false") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("n"))
            val term = Terminal(terminalInterface = rec)
            val result = ConfirmDialog.show(term, "Proceed?")
            result shouldBe false
        }

        test("show with N key returns false") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("N"))
            val term = Terminal(terminalInterface = rec)
            val result = ConfirmDialog.show(term, "Proceed?")
            result shouldBe false
        }

        test("show with q key returns false") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            val result = ConfirmDialog.show(term, "Proceed?")
            result shouldBe false
        }

        test("show with ESCAPE returns false") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Escape"))
            val term = Terminal(terminalInterface = rec)
            val result = ConfirmDialog.show(term, "Proceed?")
            result shouldBe false
        }

        test("show with Right then Enter returns false (toggled to No)") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("ArrowRight"))
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)
            val result = ConfirmDialog.show(term, "Proceed?")
            result shouldBe false
        }

        test("show with Enter returns true (default yes)") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)
            val result = ConfirmDialog.show(term, "Proceed?")
            result shouldBe true
        }

        test("show with defaultYes=false and Enter returns false") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)
            val result = ConfirmDialog.show(term, "Proceed?", defaultYes = false)
            result shouldBe false
        }

        test("show with defaultYes=false and Left then Enter returns true") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("ArrowLeft"))
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)
            val result = ConfirmDialog.show(term, "Proceed?", defaultYes = false)
            result shouldBe true
        }

        test("show with Tab toggles selection") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            // Default is Yes, Tab toggles to No, Enter submits No
            rec.inputEvents.add(KeyboardEvent("Tab"))
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)
            val result = ConfirmDialog.show(term, "Proceed?")
            result shouldBe false
        }

        test("show with Tab Tab returns to original selection") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            // Default is Yes, Tab -> No, Tab -> Yes, Enter submits Yes
            rec.inputEvents.add(KeyboardEvent("Tab"))
            rec.inputEvents.add(KeyboardEvent("Tab"))
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)
            val result = ConfirmDialog.show(term, "Proceed?")
            result shouldBe true
        }

        test("show with l key toggles to No then Enter returns false") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("l"))
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)
            val result = ConfirmDialog.show(term, "Proceed?")
            result shouldBe false
        }

        test("show with h key keeps Yes then Enter returns true") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("h"))
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)
            val result = ConfirmDialog.show(term, "Proceed?")
            result shouldBe true
        }

        test("show ignores unrecognised keys") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("x"))
            rec.inputEvents.add(KeyboardEvent("j"))
            rec.inputEvents.add(KeyboardEvent("y"))
            val term = Terminal(terminalInterface = rec)
            val result = ConfirmDialog.show(term, "Proceed?")
            result shouldBe true
        }

        test("show renders prompt in output") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("y"))
            val term = Terminal(terminalInterface = rec)
            ConfirmDialog.show(term, "Are you sure?")
            val output = rec.output()
            output shouldContain "Are you sure?"
        }

        test("show renders button labels in output") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("y"))
            val term = Terminal(terminalInterface = rec)
            ConfirmDialog.show(term, "OK?", affirmative = "Accept", negative = "Reject")
            val output = rec.output()
            output shouldContain "Accept"
            output shouldContain "Reject"
        }

        test("show renders status bar hints") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("y"))
            val term = Terminal(terminalInterface = rec)
            ConfirmDialog.show(term, "OK?")
            val output = rec.output()
            output shouldContain "toggle"
            output shouldContain "confirm"
        }

        test("show with custom labels and y returns true regardless of label") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("y"))
            val term = Terminal(terminalInterface = rec)
            val result = ConfirmDialog.show(term, "OK?", affirmative = "Do it", negative = "Nope")
            result shouldBe true
        }

        test("show with Right Right Enter returns false (stays on No)") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("ArrowRight"))
            rec.inputEvents.add(KeyboardEvent("ArrowRight"))
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)
            val result = ConfirmDialog.show(term, "OK?")
            result shouldBe false
        }

        test("show with Left Left Enter returns true (stays on Yes)") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("ArrowLeft"))
            rec.inputEvents.add(KeyboardEvent("ArrowLeft"))
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)
            val result = ConfirmDialog.show(term, "OK?")
            result shouldBe true
        }
    })
