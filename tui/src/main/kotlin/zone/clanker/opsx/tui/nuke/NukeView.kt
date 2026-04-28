/**
 * TUI view for `opsx nuke` -- confirmation screen showing what will be removed,
 * followed by a live result screen showing each item as it is deleted.
 * Uses [AppShell.render] for full-screen rendering.
 */
package zone.clanker.opsx.tui.nuke

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
import kotlinx.io.files.Path
import zone.clanker.opsx.cli.nuke.NukeEntry
import zone.clanker.opsx.cli.nuke.NukeRunner
import zone.clanker.opsx.tui.render.AppShell
import zone.clanker.opsx.tui.render.ConfirmDialog
import zone.clanker.opsx.tui.render.Keys
import zone.clanker.opsx.tui.render.Styles

/**
 * Confirmation and result screens for `opsx nuke` -- displays what will be
 * removed, runs the nuke on confirmation, then shows a live report.
 */
object NukeView {
    /** Semantic action from a key event in the nuke confirmation view. */
    internal sealed interface NukeAction {
        /** User confirmed the nuke operation. */
        data object Confirm : NukeAction

        /** User cancelled the nuke operation. */
        data object Cancel : NukeAction
    }

    /** Map a [KeyboardEvent] to a [NukeAction], or null for unrecognised keys. */
    internal fun translateNukeKey(event: KeyboardEvent): NukeAction? =
        when (event) {
            KeyboardEvent("y"), KeyboardEvent("Y") -> NukeAction.Confirm
            KeyboardEvent("n"), KeyboardEvent("N"), Keys.ESCAPE, KeyboardEvent("q"),
            KeyboardEvent("Q"),
            -> NukeAction.Cancel
            else -> null
        }

    /** Build the nuke confirmation widget listing what will be removed. */
    internal fun buildConfirmWidget(): Widget {
        val margin = Styles.margin
        return verticalLayout {
            cell(Text("$margin${bold(brightGreen("opsx"))}  ${brightRed("nuke")}"))
            cell(Text(""))
            cell(Text("${margin}This will remove:"))
            cell(Text(""))
            cell(Text("${margin}${brightWhite("Project files:")}"))
            cell(Text("$margin  \u2022 Remove opsx skills and agents from all hosts"))
            cell(Text("$margin  \u2022 .opsx/cache/"))
            cell(Text("$margin  \u2022 .opsx/config.json"))
            cell(Text("$margin  \u2022 Marker blocks from CLAUDE.md, copilot-instructions.md, AGENTS.md"))
            cell(Text("$margin  \u2022 User-created skills and agents are preserved"))
            cell(Text(""))
            cell(Text("${margin}${brightWhite("Global install:")}"))
            cell(Text("$margin  \u2022 ~/.opsx/"))
            cell(Text("$margin  \u2022 PATH block from ~/.zshrc, ~/.bashrc, ~/.profile"))
            cell(Text("$margin  \u2022 ~/.zsh/completions/_opsx"))
        }
    }

    /** Build the result widget showing each [NukeEntry] with status indicators. */
    internal fun buildResultWidget(entries: List<NukeEntry>): Widget {
        val margin = Styles.margin
        val maxPathLen = entries.maxOfOrNull { it.path.length } ?: 0
        return verticalLayout {
            cell(Text("$margin${bold(brightGreen("opsx"))}  ${brightRed("nuke")}"))
            cell(Text(""))
            for (entry in entries) {
                val icon =
                    when {
                        !entry.success -> brightRed("\u2717")
                        entry.description == "not found" || entry.description == "no PATH block" -> gray("\u2014")
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

    /** Display the nuke confirmation screen; blocks until the user confirms or cancels. */
    fun show(terminal: Terminal) {
        AppShell.render(
            terminal = terminal,
            content = buildConfirmWidget(),
            barLeft = "  y confirm  n cancel",
            barRight = "",
        )

        val confirmed =
            ConfirmDialog.show(
                terminal = terminal,
                prompt = "Remove all opsx files from this project?",
            )

        if (!confirmed) return

        val entries = NukeRunner.nukeProject(Path(".")) + NukeRunner.nukeGlobal()
        showResult(terminal, entries)
    }

    /** Display the post-nuke result screen; blocks until dismissed. */
    private fun showResult(
        terminal: Terminal,
        entries: List<NukeEntry>,
    ) {
        val succeeded = entries.count { it.success }
        val total = entries.size

        fun renderResult() {
            AppShell.render(
                terminal = terminal,
                content = buildResultWidget(entries),
                barLeft = "  \u21b5 done",
                barRight = "$succeeded/$total cleaned  ",
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
