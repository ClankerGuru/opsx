/**
 * TUI view for `opsx update` -- checks for updates via [UpdateRunner], shows
 * version comparison, and optionally downloads and installs the update.
 * Uses [AppShell.render] for full-screen rendering.
 */
package zone.clanker.opsx.tui.update

import com.github.ajalt.mordant.input.InputReceiver.Status
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextColors.brightGreen
import com.github.ajalt.mordant.rendering.TextColors.brightRed
import com.github.ajalt.mordant.rendering.TextColors.brightWhite
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.table.verticalLayout
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text
import zone.clanker.opsx.cli.BuildConfig
import zone.clanker.opsx.cli.update.UpdateCheck
import zone.clanker.opsx.cli.update.UpdateResult
import zone.clanker.opsx.cli.update.UpdateRunner
import zone.clanker.opsx.cli.update.UpdateStep
import zone.clanker.opsx.tui.render.AppShell
import zone.clanker.opsx.tui.render.ConfirmDialog
import zone.clanker.opsx.tui.render.Keys
import zone.clanker.opsx.tui.render.Styles

/**
 * TUI screen for `opsx update` -- checks for the latest release, displays
 * version comparison, and offers to download and install when an update
 * is available. Blocks until the user navigates back.
 */
object UpdateView {
    /** Build the "checking for updates" widget shown while the network call is in flight. */
    internal fun buildCheckingWidget(currentVersion: String): Widget {
        val margin = Styles.margin
        return verticalLayout {
            cell(Text("$margin${bold(brightGreen("opsx"))}  ${brightWhite("update")}"))
            cell(Text(""))
            cell(Text("$margin${gray("current")}   $currentVersion"))
            cell(Text(""))
            cell(Text("$margin${gray("\u2192 checking latest release...")}"))
        }
    }

    /** Build the "up to date" widget when no update is available. */
    internal fun buildUpToDateWidget(version: String): Widget {
        val margin = Styles.margin
        return verticalLayout {
            cell(Text("$margin${bold(brightGreen("opsx"))}  ${brightWhite("update")}"))
            cell(Text(""))
            cell(Text("$margin${gray("current")}   $version"))
            cell(Text(""))
            cell(Text("$margin${brightGreen("\u2713")} up to date"))
        }
    }

    /** Build the "update available" widget showing the version diff and confirm prompt. */
    internal fun buildAvailableWidget(
        current: String,
        latest: String,
    ): Widget {
        val margin = Styles.margin
        return verticalLayout {
            cell(Text("$margin${bold(brightGreen("opsx"))}  ${brightWhite("update")}"))
            cell(Text(""))
            cell(Text("$margin${gray("current")}   $current"))
            cell(Text("$margin${brightGreen("latest")}    $latest"))
            cell(Text(""))
            cell(Text("$margin${brightWhite("\u2190 update available!")}"))
            cell(Text(""))
            val hint =
                "$margin${gray("Press")} ${brightWhite("\u21b5")} " +
                    "${gray("to update, or")} ${brightWhite("q")} ${gray("to go back")}"
            cell(Text(hint))
        }
    }

    /** Build the progress widget showing download steps as they complete. */
    internal fun buildProgressWidget(
        current: String,
        latest: String,
        steps: List<UpdateStep>,
    ): Widget {
        val margin = Styles.margin
        return verticalLayout {
            cell(Text("$margin${bold(brightGreen("opsx"))}  ${brightWhite("update")}"))
            cell(Text(""))
            cell(Text("$margin${gray("current")}   $current"))
            cell(Text("$margin${brightGreen("latest")}    $latest"))
            cell(Text(""))
            for (step in steps) {
                cell(Text("$margin${renderStep(step)}"))
            }
        }
    }

    /** Build the error widget shown when the update check fails. */
    internal fun buildErrorWidget(errorMessage: String): Widget {
        val margin = Styles.margin
        return verticalLayout {
            cell(Text("$margin${bold(brightGreen("opsx"))}  ${brightWhite("update")}"))
            cell(Text(""))
            cell(Text("$margin${brightRed("\u2717")} $errorMessage"))
        }
    }

    /**
     * Display the update check screen; blocks until the user navigates back.
     *
     * 1. Shows "checking..." state
     * 2. Calls [UpdateRunner.checkLatest]
     * 3. Renders the result
     * 4. If available and user presses Enter, downloads and installs
     */
    fun show(terminal: Terminal) {
        val currentVersion = BuildConfig.VERSION

        renderChecking(terminal, currentVersion)
        val checkResult = UpdateRunner.checkLatest(currentVersion)

        when (checkResult) {
            is UpdateCheck.UpToDate -> showUpToDate(terminal, checkResult.version)
            is UpdateCheck.Failed -> showError(terminal, checkResult.error)
            is UpdateCheck.Available -> showAvailable(terminal, checkResult)
        }
    }

    /** Render the "checking..." state and flush to the terminal. */
    internal fun renderChecking(
        terminal: Terminal,
        currentVersion: String,
    ) {
        AppShell.render(
            terminal = terminal,
            content = buildCheckingWidget(currentVersion),
            barLeft = "  \u21b5 done  \u2190 back",
            barRight = "",
        )
    }

    /** Show the "up to date" screen and wait for dismissal. */
    internal fun showUpToDate(
        terminal: Terminal,
        version: String,
    ) {
        fun render() {
            AppShell.render(
                terminal = terminal,
                content = buildUpToDateWidget(version),
                barLeft = "  \u21b5 done  \u2190 back",
                barRight = "",
            )
        }
        render()
        waitForDismiss(terminal, ::render)
    }

    /** Show the error screen and wait for dismissal. */
    internal fun showError(
        terminal: Terminal,
        errorMessage: String,
    ) {
        fun render() {
            AppShell.render(
                terminal = terminal,
                content = buildErrorWidget(errorMessage),
                barLeft = "  \u21b5 done  \u2190 back",
                barRight = "",
            )
        }
        render()
        waitForDismiss(terminal, ::render)
    }

    /** Show the "update available" screen; if user confirms, download and install. */
    internal fun showAvailable(
        terminal: Terminal,
        available: UpdateCheck.Available,
    ) {
        val confirmed =
            ConfirmDialog.show(
                terminal,
                "Update opsx from ${available.current} to ${available.latest}?",
            )
        if (!confirmed) return

        performUpdate(terminal, available)
    }

    /** Download and install the update, rendering progress steps as they occur. */
    internal fun performUpdate(
        terminal: Terminal,
        available: UpdateCheck.Available,
    ) {
        val steps = mutableListOf<UpdateStep>()

        fun renderProgress() {
            AppShell.render(
                terminal = terminal,
                content = buildProgressWidget(available.current, available.latest, steps),
                barLeft = "  updating...",
                barRight = "${available.current} \u2192 ${available.latest}  ",
            )
        }

        val result =
            UpdateRunner.downloadAndInstall(available.release) { step ->
                steps.add(step)
                renderProgress()
            }

        when (result) {
            is UpdateResult.Success -> {
                steps.add(UpdateStep.Done(result.version))
                renderProgress()
            }
            is UpdateResult.Failed -> {
                steps.add(UpdateStep.Error(result.error))
                renderProgress()
            }
        }

        val barLeft =
            when (result) {
                is UpdateResult.Success -> "  \u21b5 done"
                is UpdateResult.Failed -> "  \u21b5 done  (update failed)"
            }

        fun renderFinal() {
            AppShell.render(
                terminal = terminal,
                content = buildProgressWidget(available.current, available.latest, steps),
                barLeft = barLeft,
                barRight = "${available.current} \u2192 ${available.latest}  ",
            )
        }
        renderFinal()
        waitForDismiss(terminal, ::renderFinal)
    }

    /** Block until the user presses a dismiss key (Enter, Left, Escape, q, Q, h). */
    private fun waitForDismiss(
        terminal: Terminal,
        onResize: () -> Unit,
    ) {
        AppShell.readKeyInput(
            terminal,
            handler = { event ->
                when (event) {
                    Keys.ENTER, Keys.LEFT, Keys.ESCAPE,
                    KeyboardEvent("h"), KeyboardEvent("q"), KeyboardEvent("Q"),
                    -> Status.Finished(Unit)
                    else -> Status.Continue
                }
            },
            onResize = onResize,
        )
    }

    /** Render a single [UpdateStep] as a styled string. */
    private fun renderStep(step: UpdateStep): String =
        when (step) {
            is UpdateStep.Checking ->
                "${gray("\u2192")} ${gray("checking ${step.repo}...")}"
            is UpdateStep.Downloading ->
                "${gray("\u2192")} ${gray("downloading ${step.assetName}...")}"
            is UpdateStep.VerifyingChecksum ->
                "${gray("\u2192")} ${gray("verifying checksum...")}"
            is UpdateStep.Extracting ->
                "${gray("\u2192")} ${gray("extracting...")}"
            is UpdateStep.Done ->
                "${brightGreen("\u2713")} updated to ${step.version}"
            is UpdateStep.Error ->
                "${brightRed("\u2717")} ${step.message}"
        }
}
