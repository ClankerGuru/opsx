/**
 * TUI view for `opsx install` -- confirmation screen showing what will be
 * installed, followed by a live result screen showing each item as it completes.
 * Uses [AppShell.render] for full-screen rendering.
 */
package zone.clanker.opsx.tui.install

import com.github.ajalt.mordant.input.InputReceiver.Status
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextColors.brightGreen
import com.github.ajalt.mordant.rendering.TextColors.brightRed
import com.github.ajalt.mordant.rendering.TextColors.brightWhite
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.table.verticalLayout
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text
import kotlinx.io.files.Path
import zone.clanker.opsx.cli.install.InstallEntry
import zone.clanker.opsx.cli.install.InstallRunner
import zone.clanker.opsx.tui.render.AppShell
import zone.clanker.opsx.tui.render.ConfirmDialog
import zone.clanker.opsx.tui.render.Keys
import zone.clanker.opsx.tui.render.Styles

/**
 * Confirmation and result screens for `opsx install` -- displays what will be
 * installed, runs the install on confirmation, then shows a live report.
 */
object InstallView {
    /** Build the install confirmation widget showing what will be installed. */
    internal fun buildConfirmWidget(sourceDir: Path?): Widget {
        val margin = Styles.margin
        return verticalLayout {
            cell(Text("$margin${bold(brightGreen("opsx"))}  ${brightWhite("install")}"))
            cell(Text(""))
            if (sourceDir == null) {
                cell(Text("$margin${yellow("Could not determine install source.")}"))
                cell(Text("$margin${gray("Run from a distribution directory.")}"))
            } else {
                cell(Text("${margin}Install opsx globally to ${brightWhite("~/.opsx/")}"))
                cell(Text(""))
                cell(Text("${margin}This will:"))
                cell(Text("$margin  \u2022 Copy bin/ to ~/.opsx/bin/ (chmod +x)"))
                cell(Text("$margin  \u2022 Copy lib/ to ~/.opsx/lib/"))
                cell(Text("$margin  \u2022 Add ~/.opsx/bin to PATH in shell rc"))
                cell(Text("$margin  \u2022 Install zsh completions"))
                cell(Text(""))
                cell(Text("$margin${gray("Source: $sourceDir")}"))
            }
        }
    }

    /** Build the result widget showing each [InstallEntry] with status indicators. */
    internal fun buildResultWidget(entries: List<InstallEntry>): Widget {
        val margin = Styles.margin
        val maxPathLen = entries.maxOfOrNull { it.path.length } ?: 0
        return verticalLayout {
            cell(Text("$margin${bold(brightGreen("opsx"))}  ${brightWhite("install")}"))
            cell(Text(""))
            for (entry in entries) {
                val icon =
                    when {
                        !entry.success -> brightRed("\u2717")
                        entry.description == "already configured" ||
                            entry.description.startsWith("skipped") -> gray("\u2014")
                        else -> brightGreen("\u2713")
                    }
                val paddedPath = entry.path.padEnd(maxPathLen + 2)
                val detail =
                    if (entry.success) {
                        gray(entry.description)
                    } else {
                        brightRed(entry.error ?: entry.description)
                    }
                cell(Text("$margin$icon $paddedPath$detail"))
            }
            cell(Text(""))
            val allSucceeded = entries.all { it.success }
            if (allSucceeded) {
                cell(Text("${margin}Restart your shell or: ${brightWhite("source ~/.zshrc")}"))
            }
        }
    }

    /** Semantic action from a key event in the result view. */
    internal sealed interface ResultAction {
        /** Dismiss the result screen. */
        data object Dismiss : ResultAction
    }

    /** Map a [KeyboardEvent] to a [ResultAction], or null for unrecognised keys. */
    internal fun translateResultKey(event: KeyboardEvent): ResultAction? =
        when (event) {
            Keys.ENTER, Keys.LEFT, KeyboardEvent("h"), Keys.ESCAPE, KeyboardEvent("q"),
            KeyboardEvent("Q"),
            -> ResultAction.Dismiss
            else -> null
        }

    /** Display the install confirmation screen; blocks until the user confirms or cancels. */
    fun show(terminal: Terminal) {
        show(terminal, InstallRunner.resolveSourceDir()) { InstallRunner.install(it) }
    }

    /**
     * Testable overload that accepts an explicit [sourceDir] and [runInstall] action
     * instead of reading system properties and calling [InstallRunner] directly.
     */
    internal fun show(
        terminal: Terminal,
        sourceDir: Path?,
        runInstall: (Path) -> List<InstallEntry> = { InstallRunner.install(it) },
    ) {
        if (sourceDir == null) {
            fun renderError() {
                AppShell.render(
                    terminal = terminal,
                    content = buildConfirmWidget(null),
                    barLeft = "  \u2190 back",
                    barRight = "",
                )
            }
            renderError()
            AppShell.readKeyInput(
                terminal,
                handler = { event ->
                    when (translateResultKey(event)) {
                        ResultAction.Dismiss -> Status.Finished(Unit)
                        null -> Status.Continue
                    }
                },
                onResize = { renderError() },
            )
            return
        }

        val confirmed = ConfirmDialog.show(terminal, "Install opsx globally to ~/.opsx?")
        if (!confirmed) return

        val entries = runInstall(sourceDir)
        showResult(terminal, entries)
    }

    /** Display the post-install result screen; blocks until dismissed. */
    internal fun showResult(
        terminal: Terminal,
        entries: List<InstallEntry>,
    ) {
        val succeeded = entries.count { it.success }
        val total = entries.size

        fun renderResult() {
            AppShell.render(
                terminal = terminal,
                content = buildResultWidget(entries),
                barLeft = "  \u21b5 done",
                barRight = "$succeeded/$total installed  ",
            )
        }
        renderResult()
        AppShell.readKeyInput(
            terminal,
            handler = { event ->
                when (translateResultKey(event)) {
                    ResultAction.Dismiss -> Status.Finished(Unit)
                    null -> Status.Continue
                }
            },
            onResize = { renderResult() },
        )
    }
}
