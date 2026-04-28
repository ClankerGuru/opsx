package zone.clanker.opsx.tui.install

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.io.files.Path
import zone.clanker.opsx.cli.install.InstallEntry
import zone.clanker.opsx.tui.render.Keys

class InstallViewTest :
    FunSpec({

        val recorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 100, height = 40)
        val terminal = Terminal(terminalInterface = recorder)

        // --- translateResultKey ---

        test("translateResultKey maps ENTER to Dismiss") {
            InstallView.translateResultKey(Keys.ENTER) shouldBe InstallView.ResultAction.Dismiss
        }

        test("translateResultKey maps LEFT to Dismiss") {
            InstallView.translateResultKey(Keys.LEFT) shouldBe InstallView.ResultAction.Dismiss
        }

        test("translateResultKey maps h to Dismiss") {
            InstallView.translateResultKey(KeyboardEvent("h")) shouldBe InstallView.ResultAction.Dismiss
        }

        test("translateResultKey maps ESCAPE to Dismiss") {
            InstallView.translateResultKey(Keys.ESCAPE) shouldBe InstallView.ResultAction.Dismiss
        }

        test("translateResultKey maps q to Dismiss") {
            InstallView.translateResultKey(KeyboardEvent("q")) shouldBe InstallView.ResultAction.Dismiss
        }

        test("translateResultKey maps Q to Dismiss") {
            InstallView.translateResultKey(KeyboardEvent("Q")) shouldBe InstallView.ResultAction.Dismiss
        }

        test("translateResultKey returns null for unrecognised keys") {
            InstallView.translateResultKey(KeyboardEvent("x")).shouldBeNull()
            InstallView.translateResultKey(KeyboardEvent("j")).shouldBeNull()
        }

        // --- buildConfirmWidget with sourceDir ---

        test("buildConfirmWidget with sourceDir contains opsx install header") {
            val widget = InstallView.buildConfirmWidget(Path("/tmp/dist"))
            val rendered = terminal.render(widget)
            rendered shouldContain "opsx"
            rendered shouldContain "install"
        }

        test("buildConfirmWidget with sourceDir shows install target") {
            val widget = InstallView.buildConfirmWidget(Path("/tmp/dist"))
            val rendered = terminal.render(widget)
            rendered shouldContain "~/.opsx/"
        }

        test("buildConfirmWidget with sourceDir shows what will be installed") {
            val widget = InstallView.buildConfirmWidget(Path("/tmp/dist"))
            val rendered = terminal.render(widget)
            rendered shouldContain "Copy bin/"
            rendered shouldContain "Copy lib/"
            rendered shouldContain "PATH"
            rendered shouldContain "completions"
        }

        test("buildConfirmWidget with sourceDir shows This will header") {
            val widget = InstallView.buildConfirmWidget(Path("/tmp/dist"))
            val rendered = terminal.render(widget)
            rendered shouldContain "This will"
        }

        test("buildConfirmWidget with sourceDir shows source path") {
            val widget = InstallView.buildConfirmWidget(Path("/tmp/dist"))
            val rendered = terminal.render(widget)
            rendered shouldContain "/tmp/dist"
        }

        // --- buildConfirmWidget with null sourceDir ---

        test("buildConfirmWidget with null sourceDir shows error message") {
            val widget = InstallView.buildConfirmWidget(null)
            val rendered = terminal.render(widget)
            rendered shouldContain "Could not determine install source"
        }

        test("buildConfirmWidget with null sourceDir shows distribution hint") {
            val widget = InstallView.buildConfirmWidget(null)
            val rendered = terminal.render(widget)
            rendered shouldContain "distribution"
        }

        test("buildConfirmWidget with null sourceDir still has header") {
            val widget = InstallView.buildConfirmWidget(null)
            val rendered = terminal.render(widget)
            rendered shouldContain "opsx"
            rendered shouldContain "install"
        }

        // --- buildResultWidget ---

        test("buildResultWidget renders success entries with checkmark") {
            val entries =
                listOf(
                    InstallEntry(path = "~/.opsx/bin/opsx", description = "copied", success = true),
                    InstallEntry(path = "~/.opsx/lib/app.jar", description = "copied", success = true),
                )
            val widget = InstallView.buildResultWidget(entries)
            val rendered = terminal.render(widget)
            rendered shouldContain "~/.opsx/bin/opsx"
            rendered shouldContain "copied"
            rendered shouldContain "~/.opsx/lib/app.jar"
        }

        test("buildResultWidget renders failure entries with cross") {
            val entries =
                listOf(
                    InstallEntry(
                        path = "~/.opsx/bin/opsx",
                        description = "copy failed",
                        success = false,
                        error = "permission denied",
                    ),
                )
            val widget = InstallView.buildResultWidget(entries)
            val rendered = terminal.render(widget)
            rendered shouldContain "~/.opsx/bin/opsx"
            rendered shouldContain "permission denied"
        }

        test("buildResultWidget renders already-configured entries with dash") {
            val entries =
                listOf(
                    InstallEntry(path = "~/.zshrc", description = "already configured", success = true),
                )
            val widget = InstallView.buildResultWidget(entries)
            val rendered = terminal.render(widget)
            rendered shouldContain "~/.zshrc"
            rendered shouldContain "already configured"
        }

        test("buildResultWidget renders skipped entries with dash") {
            val entries =
                listOf(
                    InstallEntry(
                        path = "~/.zsh/completions/_opsx",
                        description = "skipped (no .zshrc)",
                        success = true,
                    ),
                )
            val widget = InstallView.buildResultWidget(entries)
            val rendered = terminal.render(widget)
            rendered shouldContain "skipped"
        }

        test("buildResultWidget renders mixed entries") {
            val entries =
                listOf(
                    InstallEntry(path = "~/.opsx/bin/opsx", description = "copied", success = true),
                    InstallEntry(path = "~/.zshrc", description = "PATH configured", success = true),
                    InstallEntry(
                        path = "~/.zsh/completions/_opsx",
                        description = "completions",
                        success = false,
                        error = "denied",
                    ),
                )
            val widget = InstallView.buildResultWidget(entries)
            val rendered = terminal.render(widget)
            rendered shouldContain "~/.opsx/bin/opsx"
            rendered shouldContain "~/.zshrc"
            rendered shouldContain "~/.zsh/completions/_opsx"
        }

        test("buildResultWidget contains opsx install header") {
            val entries =
                listOf(
                    InstallEntry(path = "foo", description = "copied", success = true),
                )
            val widget = InstallView.buildResultWidget(entries)
            val rendered = terminal.render(widget)
            rendered shouldContain "opsx"
            rendered shouldContain "install"
        }

        test("buildResultWidget handles empty entries list") {
            val widget = InstallView.buildResultWidget(emptyList())
            val rendered = terminal.render(widget)
            rendered shouldContain "opsx"
            rendered shouldContain "install"
        }

        test("buildResultWidget shows restart hint when all succeeded") {
            val entries =
                listOf(
                    InstallEntry(path = "~/.opsx/bin/opsx", description = "copied", success = true),
                    InstallEntry(path = "~/.zshrc", description = "PATH configured", success = true),
                )
            val widget = InstallView.buildResultWidget(entries)
            val rendered = terminal.render(widget)
            rendered shouldContain "source ~/.zshrc"
        }

        test("buildResultWidget hides restart hint when there are failures") {
            val entries =
                listOf(
                    InstallEntry(path = "~/.opsx/bin/opsx", description = "copied", success = true),
                    InstallEntry(path = "foo", description = "fail", success = false, error = "err"),
                )
            val widget = InstallView.buildResultWidget(entries)
            val rendered = terminal.render(widget)
            rendered.contains("source ~/.zshrc") shouldBe false
        }

        test("buildResultWidget renders failure without error using description") {
            val entries =
                listOf(
                    InstallEntry(
                        path = "~/.opsx/bin/opsx",
                        description = "copy failed",
                        success = false,
                        error = null,
                    ),
                )
            val widget = InstallView.buildResultWidget(entries)
            val rendered = terminal.render(widget)
            rendered shouldContain "copy failed"
        }

        // --- show interactive tests ---

        test("show renders confirm and returns on q key (cancel)") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            InstallView.show(term)
            val output = rec.output()
            output shouldContain "opsx"
            output shouldContain "install"
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
            InstallView.show(term)
        }

        test("show renders confirm and exits on LEFT") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("ArrowLeft"))
            val term = Terminal(terminalInterface = rec)
            InstallView.show(term)
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
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            InstallView.show(term)
        }

        test("show returns on h key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("h"))
            val term = Terminal(terminalInterface = rec)
            InstallView.show(term)
        }

        test("show returns on Q key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Q"))
            val term = Terminal(terminalInterface = rec)
            InstallView.show(term)
        }

        // --- show(terminal, sourceDir, runInstall) internal overload ---

        test("show with non-null sourceDir confirms and runs install on ENTER") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            // Press Enter to confirm install, then q to dismiss result screen
            rec.inputEvents.add(KeyboardEvent("Enter"))
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            val fakeEntries =
                listOf(
                    InstallEntry(path = "~/.opsx/bin/opsx", description = "copied", success = true),
                    InstallEntry(path = "~/.zshrc", description = "PATH configured", success = true),
                )
            InstallView.show(term, Path("/fake/dist")) { fakeEntries }
            val output = rec.output()
            output shouldContain "opsx"
            output shouldContain "install"
            output shouldContain "copied"
        }

        test("show with non-null sourceDir cancels on q without running install") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            var installCalled = false
            InstallView.show(term, Path("/fake/dist")) {
                installCalled = true
                emptyList()
            }
            installCalled shouldBe false
        }

        test("show with non-null sourceDir cancels on ESCAPE without running install") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Escape"))
            val term = Terminal(terminalInterface = rec)
            var installCalled = false
            InstallView.show(term, Path("/fake/dist")) {
                installCalled = true
                emptyList()
            }
            installCalled shouldBe false
        }

        test("show with non-null sourceDir ignores unrecognised keys then confirms") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("x"))
            rec.inputEvents.add(KeyboardEvent("j"))
            rec.inputEvents.add(KeyboardEvent("Enter"))
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            val fakeEntries =
                listOf(
                    InstallEntry(path = "~/.opsx/bin/opsx", description = "copied", success = true),
                )
            InstallView.show(term, Path("/fake/dist")) { fakeEntries }
            val output = rec.output()
            output shouldContain "copied"
        }

        test("show with non-null sourceDir cancels on n key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("n"))
            val term = Terminal(terminalInterface = rec)
            var installCalled = false
            InstallView.show(term, Path("/fake/dist")) {
                installCalled = true
                emptyList()
            }
            installCalled shouldBe false
        }

        test("show with non-null sourceDir shows confirm dialog with install prompt") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            InstallView.show(term, Path("/fake/dist")) { emptyList() }
            val output = rec.output()
            output shouldContain "Install opsx globally to ~/.opsx?"
        }

        test("show with null sourceDir still works through internal overload") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            InstallView.show(term, null)
            val output = rec.output()
            output shouldContain "Could not determine install source"
        }

        // --- showResult (internal, directly testable) ---

        test("showResult renders result screen with success entries and exits on q") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            val entries =
                listOf(
                    InstallEntry(path = "~/.opsx/bin/opsx", description = "copied", success = true),
                    InstallEntry(path = "~/.opsx/lib/app.jar", description = "copied", success = true),
                    InstallEntry(path = "~/.zshrc", description = "PATH configured", success = true),
                    InstallEntry(path = "~/.zsh/completions/_opsx", description = "installed", success = true),
                )
            InstallView.showResult(term, entries)
            val output = rec.output()
            output shouldContain "opsx"
            output shouldContain "install"
            output shouldContain "4/4 installed"
            output shouldContain "source ~/.zshrc"
        }

        test("showResult renders result screen with mixed entries and exits on ENTER") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)
            val entries =
                listOf(
                    InstallEntry(path = "~/.opsx/bin/opsx", description = "copied", success = true),
                    InstallEntry(
                        path = "~/.opsx/lib/app.jar",
                        description = "copy failed",
                        success = false,
                        error = "permission denied",
                    ),
                )
            InstallView.showResult(term, entries)
            val output = rec.output()
            output shouldContain "1/2 installed"
        }

        test("showResult exits on LEFT key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("ArrowLeft"))
            val term = Terminal(terminalInterface = rec)
            val entries =
                listOf(
                    InstallEntry(path = "~/.opsx/bin/opsx", description = "copied", success = true),
                )
            InstallView.showResult(term, entries)
        }

        test("showResult exits on ESCAPE key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Escape"))
            val term = Terminal(terminalInterface = rec)
            val entries =
                listOf(
                    InstallEntry(path = "~/.opsx/bin/opsx", description = "copied", success = true),
                )
            InstallView.showResult(term, entries)
        }

        test("showResult exits on h key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("h"))
            val term = Terminal(terminalInterface = rec)
            val entries =
                listOf(
                    InstallEntry(path = "~/.opsx/bin/opsx", description = "copied", success = true),
                )
            InstallView.showResult(term, entries)
        }

        test("showResult exits on Q key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Q"))
            val term = Terminal(terminalInterface = rec)
            val entries =
                listOf(
                    InstallEntry(path = "~/.opsx/bin/opsx", description = "copied", success = true),
                )
            InstallView.showResult(term, entries)
        }

        test("showResult ignores unrecognised keys then exits on q") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("x"))
            rec.inputEvents.add(KeyboardEvent("j"))
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            val entries =
                listOf(
                    InstallEntry(path = "~/.opsx/bin/opsx", description = "copied", success = true),
                    InstallEntry(path = "~/.zshrc", description = "already configured", success = true),
                )
            InstallView.showResult(term, entries)
        }

        test("showResult renders done bar text") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            val entries =
                listOf(
                    InstallEntry(path = "~/.opsx/bin/opsx", description = "copied", success = true),
                )
            InstallView.showResult(term, entries)
            val output = rec.output()
            output shouldContain "done"
        }

        test("showResult with all failures shows correct count") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            val entries =
                listOf(
                    InstallEntry(
                        path = "bin/",
                        description = "source bin/ not found",
                        success = false,
                        error = "source bin/ directory missing",
                    ),
                    InstallEntry(
                        path = "lib/",
                        description = "source lib/ not found",
                        success = false,
                        error = "source lib/ directory missing",
                    ),
                )
            InstallView.showResult(term, entries)
            val output = rec.output()
            output shouldContain "0/2 installed"
        }
    })
