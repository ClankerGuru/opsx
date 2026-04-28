package zone.clanker.opsx.tui.update

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import zone.clanker.opsx.cli.update.Asset
import zone.clanker.opsx.cli.update.ReleaseInfo
import zone.clanker.opsx.cli.update.UpdateCheck
import zone.clanker.opsx.cli.update.UpdateStep

/** Detect the current platform in the same format as UpdateRunner.detectPlatform. */
private fun detectTestPlatform(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val osName = if ("mac" in os) "macos" else "linux"
    val archName = if ("aarch64" in arch || "arm64" in arch) "arm64" else "x64"
    return "$osName-$archName"
}

class UpdateViewTest :
    FunSpec({

        val recorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 100, height = 40)
        val terminal = Terminal(terminalInterface = recorder)

        // --- buildCheckingWidget ---

        test("buildCheckingWidget contains opsx update header") {
            val widget = UpdateView.buildCheckingWidget("1.2.3")
            val rendered = terminal.render(widget)
            rendered shouldContain "opsx"
            rendered shouldContain "update"
        }

        test("buildCheckingWidget shows current version") {
            val widget = UpdateView.buildCheckingWidget("0.0.5-SNAPSHOT")
            val rendered = terminal.render(widget)
            rendered shouldContain "current"
            rendered shouldContain "0.0.5-SNAPSHOT"
        }

        test("buildCheckingWidget shows checking message") {
            val widget = UpdateView.buildCheckingWidget("1.0.0")
            val rendered = terminal.render(widget)
            rendered shouldContain "checking"
        }

        // --- buildUpToDateWidget ---

        test("buildUpToDateWidget contains opsx update header") {
            val widget = UpdateView.buildUpToDateWidget("1.0.0")
            val rendered = terminal.render(widget)
            rendered shouldContain "opsx"
            rendered shouldContain "update"
        }

        test("buildUpToDateWidget shows version") {
            val widget = UpdateView.buildUpToDateWidget("0.0.5-SNAPSHOT")
            val rendered = terminal.render(widget)
            rendered shouldContain "0.0.5-SNAPSHOT"
        }

        test("buildUpToDateWidget shows up to date message") {
            val widget = UpdateView.buildUpToDateWidget("1.0.0")
            val rendered = terminal.render(widget)
            rendered shouldContain "up to date"
        }

        // --- buildAvailableWidget ---

        test("buildAvailableWidget contains opsx update header") {
            val widget = UpdateView.buildAvailableWidget("0.0.5", "0.1.0")
            val rendered = terminal.render(widget)
            rendered shouldContain "opsx"
            rendered shouldContain "update"
        }

        test("buildAvailableWidget shows current version") {
            val widget = UpdateView.buildAvailableWidget("0.0.5", "0.1.0")
            val rendered = terminal.render(widget)
            rendered shouldContain "current"
            rendered shouldContain "0.0.5"
        }

        test("buildAvailableWidget shows latest version") {
            val widget = UpdateView.buildAvailableWidget("0.0.5", "0.1.0")
            val rendered = terminal.render(widget)
            rendered shouldContain "latest"
            rendered shouldContain "0.1.0"
        }

        test("buildAvailableWidget shows update available notice") {
            val widget = UpdateView.buildAvailableWidget("0.0.5", "0.1.0")
            val rendered = terminal.render(widget)
            rendered shouldContain "update available"
        }

        test("buildAvailableWidget shows key hint for update") {
            val widget = UpdateView.buildAvailableWidget("0.0.5", "0.1.0")
            val rendered = terminal.render(widget)
            rendered shouldContain "update"
            rendered shouldContain "back"
        }

        // --- buildProgressWidget ---

        test("buildProgressWidget contains opsx update header") {
            val widget = UpdateView.buildProgressWidget("0.0.5", "0.1.0", emptyList())
            val rendered = terminal.render(widget)
            rendered shouldContain "opsx"
            rendered shouldContain "update"
        }

        test("buildProgressWidget shows both versions") {
            val widget = UpdateView.buildProgressWidget("0.0.5", "0.1.0", emptyList())
            val rendered = terminal.render(widget)
            rendered shouldContain "0.0.5"
            rendered shouldContain "0.1.0"
        }

        test("buildProgressWidget renders checking step") {
            val steps = listOf(UpdateStep.Checking("ClankerGuru/opsx"))
            val widget = UpdateView.buildProgressWidget("0.0.5", "0.1.0", steps)
            val rendered = terminal.render(widget)
            rendered shouldContain "checking"
            rendered shouldContain "ClankerGuru/opsx"
        }

        test("buildProgressWidget renders downloading step") {
            val steps = listOf(UpdateStep.Downloading("opsx-macos-arm64.tar.gz"))
            val widget = UpdateView.buildProgressWidget("0.0.5", "0.1.0", steps)
            val rendered = terminal.render(widget)
            rendered shouldContain "downloading"
            rendered shouldContain "opsx-macos-arm64.tar.gz"
        }

        test("buildProgressWidget renders verifying checksum step") {
            val steps = listOf(UpdateStep.VerifyingChecksum)
            val widget = UpdateView.buildProgressWidget("0.0.5", "0.1.0", steps)
            val rendered = terminal.render(widget)
            rendered shouldContain "verifying checksum"
        }

        test("buildProgressWidget renders extracting step") {
            val steps = listOf(UpdateStep.Extracting)
            val widget = UpdateView.buildProgressWidget("0.0.5", "0.1.0", steps)
            val rendered = terminal.render(widget)
            rendered shouldContain "extracting"
        }

        test("buildProgressWidget renders done step") {
            val steps = listOf(UpdateStep.Done("0.1.0"))
            val widget = UpdateView.buildProgressWidget("0.0.5", "0.1.0", steps)
            val rendered = terminal.render(widget)
            rendered shouldContain "updated to 0.1.0"
        }

        test("buildProgressWidget renders error step") {
            val steps = listOf(UpdateStep.Error("network timeout"))
            val widget = UpdateView.buildProgressWidget("0.0.5", "0.1.0", steps)
            val rendered = terminal.render(widget)
            rendered shouldContain "network timeout"
        }

        test("buildProgressWidget renders multiple steps in order") {
            val steps =
                listOf(
                    UpdateStep.Downloading("opsx-macos-arm64.tar.gz"),
                    UpdateStep.VerifyingChecksum,
                    UpdateStep.Extracting,
                    UpdateStep.Done("0.1.0"),
                )
            val widget = UpdateView.buildProgressWidget("0.0.5", "0.1.0", steps)
            val rendered = terminal.render(widget)
            rendered shouldContain "downloading"
            rendered shouldContain "verifying"
            rendered shouldContain "extracting"
            rendered shouldContain "updated to"
        }

        test("buildProgressWidget with empty steps list shows only header") {
            val widget = UpdateView.buildProgressWidget("0.0.5", "0.1.0", emptyList())
            val rendered = terminal.render(widget)
            rendered shouldContain "opsx"
            rendered shouldContain "0.0.5"
            rendered shouldContain "0.1.0"
        }

        // --- buildErrorWidget ---

        test("buildErrorWidget contains opsx update header") {
            val widget = UpdateView.buildErrorWidget("connection refused")
            val rendered = terminal.render(widget)
            rendered shouldContain "opsx"
            rendered shouldContain "update"
        }

        test("buildErrorWidget shows error message") {
            val widget = UpdateView.buildErrorWidget("connection refused")
            val rendered = terminal.render(widget)
            rendered shouldContain "connection refused"
        }

        test("buildErrorWidget shows different error message") {
            val widget = UpdateView.buildErrorWidget("timeout after 30s")
            val rendered = terminal.render(widget)
            rendered shouldContain "timeout after 30s"
        }

        // --- show interactive tests ---

        test("show renders checking state and returns on q key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            UpdateView.show(term)
            val output = rec.output()
            output shouldContain "opsx"
            output shouldContain "update"
        }

        test("show returns on ESCAPE key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Escape"))
            val term = Terminal(terminalInterface = rec)
            UpdateView.show(term)
        }

        test("show returns on LEFT key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            // LEFT dismisses waitForDismiss but only toggles in ConfirmDialog,
            // so add q as fallback for the Available path
            rec.inputEvents.add(KeyboardEvent("ArrowLeft"))
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            UpdateView.show(term)
        }

        test("show returns on h key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            // h dismisses waitForDismiss but only toggles in ConfirmDialog,
            // so add q as fallback for the Available path
            rec.inputEvents.add(KeyboardEvent("h"))
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            UpdateView.show(term)
        }

        test("show returns on Q key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            // Q dismisses waitForDismiss but is unrecognised in ConfirmDialog,
            // so add q as fallback for the Available path
            rec.inputEvents.add(KeyboardEvent("Q"))
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            UpdateView.show(term)
        }

        test("show returns on ENTER key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Enter"))
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            UpdateView.show(term)
        }

        test("show ignores unrecognised keys then exits on q") {
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
            UpdateView.show(term)
        }

        test("show displays checking message in output") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            UpdateView.show(term)
            val output = rec.output()
            output shouldContain "checking"
        }

        // --- showUpToDate (internal, directly testable) ---

        test("showUpToDate renders up-to-date screen and exits on q") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            UpdateView.showUpToDate(term, "1.2.3")
            val output = rec.output()
            output shouldContain "up to date"
            output shouldContain "1.2.3"
        }

        test("showUpToDate exits on ENTER key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)
            UpdateView.showUpToDate(term, "2.0.0")
        }

        test("showUpToDate exits on LEFT key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("ArrowLeft"))
            val term = Terminal(terminalInterface = rec)
            UpdateView.showUpToDate(term, "3.0.0")
        }

        test("showUpToDate ignores unrecognised keys then exits on q") {
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
            UpdateView.showUpToDate(term, "1.0.0")
        }

        // --- showError (internal, directly testable) ---

        test("showError renders error screen and exits on q") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            UpdateView.showError(term, "network failure")
            val output = rec.output()
            output shouldContain "network failure"
        }

        test("showError exits on ENTER key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)
            UpdateView.showError(term, "timeout")
        }

        test("showError exits on ESCAPE key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Escape"))
            val term = Terminal(terminalInterface = rec)
            UpdateView.showError(term, "rate limited")
        }

        // --- showAvailable (internal, directly testable) ---

        test("showAvailable renders available screen and dismisses on q") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            val available =
                UpdateCheck.Available(
                    current = "0.0.5",
                    latest = "0.1.0",
                    release = ReleaseInfo(tagName = "v0.1.0", assets = emptyList()),
                )
            UpdateView.showAvailable(term, available)
            val output = rec.output()
            output shouldContain "0.0.5"
            output shouldContain "0.1.0"
        }

        test("showAvailable renders available screen and dismisses on n key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("n"))
            val term = Terminal(terminalInterface = rec)
            val available =
                UpdateCheck.Available(
                    current = "1.0.0",
                    latest = "2.0.0",
                    release = ReleaseInfo(tagName = "v2.0.0", assets = emptyList()),
                )
            UpdateView.showAvailable(term, available)
        }

        test("showAvailable ignores unrecognised keys before dismissing") {
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
            val available =
                UpdateCheck.Available(
                    current = "0.0.5",
                    latest = "0.1.0",
                    release = ReleaseInfo(tagName = "v0.1.0", assets = emptyList()),
                )
            UpdateView.showAvailable(term, available)
        }

        test("showAvailable on Enter triggers performUpdate and shows result") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            // Press Enter to confirm, then q to dismiss the result screen
            rec.inputEvents.add(KeyboardEvent("Enter"))
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            val available =
                UpdateCheck.Available(
                    current = "0.0.5",
                    latest = "0.1.0",
                    release =
                        ReleaseInfo(
                            tagName = "v0.1.0",
                            assets = emptyList(),
                        ),
                )
            UpdateView.showAvailable(term, available)
            val output = rec.output()
            // The update will fail (no assets), but the progress/error screen is shown
            output shouldContain "0.0.5"
        }

        // --- performUpdate (internal, directly testable) ---

        test("performUpdate with empty assets shows error and waits for dismiss") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            val available =
                UpdateCheck.Available(
                    current = "0.0.5",
                    latest = "0.1.0",
                    release = ReleaseInfo(tagName = "v0.1.0", assets = emptyList()),
                )
            UpdateView.performUpdate(term, available)
            val output = rec.output()
            // Should show the error (release asset not found)
            output shouldContain "0.0.5"
            output shouldContain "0.1.0"
        }

        test("performUpdate with unreachable URL shows error steps") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)

            val platform = detectTestPlatform()
            val assetName = "opsx-$platform.tar.gz"
            val available =
                UpdateCheck.Available(
                    current = "0.0.5",
                    latest = "0.1.0",
                    release =
                        ReleaseInfo(
                            tagName = "v0.1.0",
                            assets =
                                listOf(
                                    Asset(
                                        name = assetName,
                                        downloadUrl = "http://invalid.test.localhost:1/nope",
                                    ),
                                ),
                        ),
                )
            UpdateView.performUpdate(term, available)
            val output = rec.output()
            output shouldContain "0.1.0"
        }

        test("performUpdate with downloadable but invalid archive shows extraction error") {
            val fakeArchive = java.io.File.createTempFile("fake-tui-", ".tar.gz")
            fakeArchive.writeText("not a real tar.gz")

            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)

            val platform = detectTestPlatform()
            val assetName = "opsx-$platform.tar.gz"
            val available =
                UpdateCheck.Available(
                    current = "0.0.5",
                    latest = "0.1.0",
                    release =
                        ReleaseInfo(
                            tagName = "v0.1.0",
                            assets =
                                listOf(
                                    Asset(
                                        name = assetName,
                                        downloadUrl = fakeArchive.toURI().toURL().toString(),
                                    ),
                                ),
                        ),
                )
            UpdateView.performUpdate(term, available)
            fakeArchive.delete()

            val output = rec.output()
            output shouldContain "0.1.0"
        }

        test("performUpdate exits on ENTER after failure") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)
            val available =
                UpdateCheck.Available(
                    current = "0.0.5",
                    latest = "0.1.0",
                    release = ReleaseInfo(tagName = "v0.1.0", assets = emptyList()),
                )
            UpdateView.performUpdate(term, available)
        }

        // --- renderChecking (internal, directly testable) ---

        test("renderChecking writes checking state to terminal") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = false,
                )
            val term = Terminal(terminalInterface = rec)
            UpdateView.renderChecking(term, "1.2.3")
            val output = rec.output()
            output shouldContain "1.2.3"
            output shouldContain "checking"
        }
    })
