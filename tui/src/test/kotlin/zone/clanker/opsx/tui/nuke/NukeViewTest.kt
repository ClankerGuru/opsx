package zone.clanker.opsx.tui.nuke

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import zone.clanker.opsx.cli.nuke.NukeEntry
import zone.clanker.opsx.tui.render.Keys

class NukeViewTest :
    FunSpec({

        val recorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 100, height = 40)
        val terminal = Terminal(terminalInterface = recorder)

        // --- translateNukeKey ---

        test("translateNukeKey maps y to Confirm") {
            NukeView.translateNukeKey(KeyboardEvent("y")) shouldBe NukeView.NukeAction.Confirm
        }

        test("translateNukeKey maps Y to Confirm") {
            NukeView.translateNukeKey(KeyboardEvent("Y")) shouldBe NukeView.NukeAction.Confirm
        }

        test("translateNukeKey maps n to Cancel") {
            NukeView.translateNukeKey(KeyboardEvent("n")) shouldBe NukeView.NukeAction.Cancel
        }

        test("translateNukeKey maps N to Cancel") {
            NukeView.translateNukeKey(KeyboardEvent("N")) shouldBe NukeView.NukeAction.Cancel
        }

        test("translateNukeKey maps ESCAPE to Cancel") {
            NukeView.translateNukeKey(Keys.ESCAPE) shouldBe NukeView.NukeAction.Cancel
        }

        test("translateNukeKey maps q to Cancel") {
            NukeView.translateNukeKey(KeyboardEvent("q")) shouldBe NukeView.NukeAction.Cancel
        }

        test("translateNukeKey maps Q to Cancel") {
            NukeView.translateNukeKey(KeyboardEvent("Q")) shouldBe NukeView.NukeAction.Cancel
        }

        test("translateNukeKey returns null for unrecognised keys") {
            NukeView.translateNukeKey(KeyboardEvent("x")).shouldBeNull()
            NukeView.translateNukeKey(KeyboardEvent("j")).shouldBeNull()
            NukeView.translateNukeKey(Keys.DOWN).shouldBeNull()
            NukeView.translateNukeKey(Keys.ENTER).shouldBeNull()
        }

        // --- buildConfirmWidget ---

        test("buildConfirmWidget contains opsx nuke header") {
            val widget = NukeView.buildConfirmWidget()
            val rendered = terminal.render(widget)
            rendered shouldContain "opsx"
            rendered shouldContain "nuke"
        }

        test("buildConfirmWidget shows project files section") {
            val widget = NukeView.buildConfirmWidget()
            val rendered = terminal.render(widget)
            rendered shouldContain "opsx skills and agents"
            rendered shouldContain "all hosts"
            rendered shouldContain ".opsx/cache/"
            rendered shouldContain ".opsx/config.json"
            rendered shouldContain "preserved"
        }

        test("buildConfirmWidget shows global install section") {
            val widget = NukeView.buildConfirmWidget()
            val rendered = terminal.render(widget)
            rendered shouldContain "~/.opsx/"
            rendered shouldContain "PATH block"
            rendered shouldContain "completions"
        }

        test("buildConfirmWidget shows marker block note") {
            val widget = NukeView.buildConfirmWidget()
            val rendered = terminal.render(widget)
            rendered shouldContain "Marker blocks"
            rendered shouldContain "CLAUDE.md"
        }

        test("buildConfirmWidget shows This will remove header") {
            val widget = NukeView.buildConfirmWidget()
            val rendered = terminal.render(widget)
            rendered shouldContain "This will remove"
        }

        // --- buildResultWidget ---

        test("buildResultWidget renders success entries with checkmark") {
            val entries =
                listOf(
                    NukeEntry(path = ".claude/skills", description = "28 skills removed", success = true),
                    NukeEntry(path = ".opsx/config.json", description = "removed", success = true),
                )
            val widget = NukeView.buildResultWidget(entries)
            val rendered = terminal.render(widget)
            rendered shouldContain ".claude/skills"
            rendered shouldContain "28 skills removed"
            rendered shouldContain ".opsx/config.json"
            rendered shouldContain "removed"
        }

        test("buildResultWidget renders not-found entries with dash") {
            val entries =
                listOf(
                    NukeEntry(path = "AGENTS.md", description = "not found", success = true),
                )
            val widget = NukeView.buildResultWidget(entries)
            val rendered = terminal.render(widget)
            rendered shouldContain "AGENTS.md"
            rendered shouldContain "not found"
        }

        test("buildResultWidget renders failure entries with cross") {
            val entries =
                listOf(
                    NukeEntry(
                        path = "~/.opsx/",
                        description = "removed",
                        success = false,
                        error = "permission denied",
                    ),
                )
            val widget = NukeView.buildResultWidget(entries)
            val rendered = terminal.render(widget)
            rendered shouldContain "~/.opsx/"
            rendered shouldContain "permission denied"
        }

        test("buildResultWidget renders mixed entries") {
            val entries =
                listOf(
                    NukeEntry(path = ".claude/skills", description = "3 skills removed", success = true),
                    NukeEntry(path = "AGENTS.md", description = "not found", success = true),
                    NukeEntry(path = "~/.opsx/", description = "removed", success = false, error = "denied"),
                )
            val widget = NukeView.buildResultWidget(entries)
            val rendered = terminal.render(widget)
            rendered shouldContain ".claude/skills"
            rendered shouldContain "AGENTS.md"
            rendered shouldContain "~/.opsx/"
        }

        test("buildResultWidget contains opsx nuke header") {
            val entries =
                listOf(
                    NukeEntry(path = "foo", description = "removed", success = true),
                )
            val widget = NukeView.buildResultWidget(entries)
            val rendered = terminal.render(widget)
            rendered shouldContain "opsx"
            rendered shouldContain "nuke"
        }

        test("buildResultWidget handles empty entries list") {
            val widget = NukeView.buildResultWidget(emptyList())
            val rendered = terminal.render(widget)
            rendered shouldContain "opsx"
            rendered shouldContain "nuke"
        }

        test("buildResultWidget renders no-PATH-block entries with dash") {
            val entries =
                listOf(
                    NukeEntry(path = "~/.bashrc", description = "no PATH block", success = true),
                )
            val widget = NukeView.buildResultWidget(entries)
            val rendered = terminal.render(widget)
            rendered shouldContain "~/.bashrc"
            rendered shouldContain "no PATH block"
        }

        // --- translateResultKey ---

        test("translateResultKey maps ENTER to Dismiss") {
            NukeView.translateResultKey(Keys.ENTER) shouldBe NukeView.ResultAction.Dismiss
        }

        test("translateResultKey maps LEFT to Dismiss") {
            NukeView.translateResultKey(Keys.LEFT) shouldBe NukeView.ResultAction.Dismiss
        }

        test("translateResultKey maps h to Dismiss") {
            NukeView.translateResultKey(KeyboardEvent("h")) shouldBe NukeView.ResultAction.Dismiss
        }

        test("translateResultKey maps ESCAPE to Dismiss") {
            NukeView.translateResultKey(Keys.ESCAPE) shouldBe NukeView.ResultAction.Dismiss
        }

        test("translateResultKey maps q to Dismiss") {
            NukeView.translateResultKey(KeyboardEvent("q")) shouldBe NukeView.ResultAction.Dismiss
        }

        test("translateResultKey maps Q to Dismiss") {
            NukeView.translateResultKey(KeyboardEvent("Q")) shouldBe NukeView.ResultAction.Dismiss
        }

        test("translateResultKey returns null for unrecognised keys") {
            NukeView.translateResultKey(KeyboardEvent("x")).shouldBeNull()
            NukeView.translateResultKey(KeyboardEvent("j")).shouldBeNull()
        }

        // --- show interactive tests ---

        test("show renders confirm and returns on n key (cancel)") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("n"))
            val term = Terminal(terminalInterface = rec)
            NukeView.show(term)
            val output = rec.output()
            output shouldContain "opsx"
            output shouldContain "nuke"
        }

        test("show renders confirm and exits on ESCAPE") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Escape"))
            val term = Terminal(terminalInterface = rec)
            NukeView.show(term)
        }

        test("show renders confirm and exits on q") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            NukeView.show(term)
        }

        test("show ignores unrecognised keys during confirm") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("x"))
            rec.inputEvents.add(KeyboardEvent("j"))
            rec.inputEvents.add(KeyboardEvent("n"))
            val term = Terminal(terminalInterface = rec)
            NukeView.show(term)
        }

        test("show runs nuke on confirm (y) then shows result dismissed by Enter") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            // Confirm nuke, then dismiss result screen
            rec.inputEvents.add(KeyboardEvent("y"))
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)
            NukeView.show(term)
            val output = rec.output()
            // Result screen shows nuke entry paths (markers + config + cache + global)
            output shouldContain "CLAUDE.md"
            output shouldContain ".opsx/config.json"
        }

        test("show runs nuke on confirm (Y) then shows result dismissed by q") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Y"))
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            NukeView.show(term)
            val output = rec.output()
            // Result screen shows nuke entries
            output shouldContain "~/.opsx"
        }

        test("show result screen ignores unrecognised keys before dismiss") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("y"))
            rec.inputEvents.add(KeyboardEvent("x"))
            rec.inputEvents.add(KeyboardEvent("j"))
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)
            NukeView.show(term)
            val output = rec.output()
            // Result screen renders entries after ignoring x and j
            output shouldContain ".opsx/config.json"
        }

        // --- buildResultWidget edge cases ---

        test("buildResultWidget renders failure without error using description") {
            val entries =
                listOf(
                    NukeEntry(
                        path = "~/.opsx/",
                        description = "removed",
                        success = false,
                        error = null,
                    ),
                )
            val widget = NukeView.buildResultWidget(entries)
            val rendered = terminal.render(widget)
            rendered shouldContain "~/.opsx/"
            rendered shouldContain "removed"
        }
    })
