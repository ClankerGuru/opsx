/**
 * Interactive TUI view for `opsx init` -- host selector with checkbox toggles,
 * cursor navigation, and inline result display after applying.
 * Uses [AppShell.render] for full-screen rendering and [AppShell.readKeyInput] for input.
 */
package zone.clanker.opsx.tui.init

import com.github.ajalt.mordant.input.InputReceiver.Status
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextColors.brightCyan
import com.github.ajalt.mordant.rendering.TextColors.brightGreen
import com.github.ajalt.mordant.rendering.TextColors.brightWhite
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.table.verticalLayout
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text
import kotlinx.io.files.Path
import zone.clanker.opsx.cli.init.HostResult
import zone.clanker.opsx.cli.init.InitResult
import zone.clanker.opsx.cli.init.InitRunner
import zone.clanker.opsx.cli.init.host.Host
import zone.clanker.opsx.cli.init.host.HostDetector
import zone.clanker.opsx.tui.render.AppShell
import zone.clanker.opsx.tui.render.ConfirmDialog
import zone.clanker.opsx.tui.render.Keys
import zone.clanker.opsx.tui.render.Styles

/**
 * Interactive host selector for `opsx init`.
 *
 * Displays all [Host] entries as a checkbox list with cursor navigation (j/k/arrows),
 * space to toggle selection, and Enter to apply. Hosts detected on PATH are pre-selected.
 * After applying, shows a results screen with per-host emission details.
 */
object InitView {
    /** Display labels for each [Host] entry shown in the selector. */
    internal val hostLabels: Map<Host, String> =
        mapOf(
            Host.CLAUDE to "Claude Code CLI",
            Host.COPILOT to "GitHub Copilot CLI",
            Host.CODEX to "OpenAI Codex CLI",
            Host.OPENCODE to "OpenCode CLI",
        )

    /** State of the interactive host selector. */
    internal data class SelectorState(
        val cursor: Int,
        val selected: Set<Host>,
        val detected: Set<Host>,
    )

    /** Semantic action from a key event in the selector view. */
    internal sealed interface SelectorAction {
        /** Move cursor down one row. */
        data object MoveDown : SelectorAction

        /** Move cursor up one row. */
        data object MoveUp : SelectorAction

        /** Toggle selection of the host at the cursor. */
        data object Toggle : SelectorAction

        /** Apply the current selection and run init. */
        data object Apply : SelectorAction

        /** Navigate back without applying. */
        data object Back : SelectorAction
    }

    /** Map a [KeyboardEvent] to a [SelectorAction], or null for unrecognised keys. */
    internal fun translateSelectorKey(event: KeyboardEvent): SelectorAction? =
        when (event) {
            Keys.DOWN, KeyboardEvent("j") -> SelectorAction.MoveDown
            Keys.UP, KeyboardEvent("k") -> SelectorAction.MoveUp
            Keys.SPACE -> SelectorAction.Toggle
            Keys.ENTER -> SelectorAction.Apply
            Keys.LEFT, KeyboardEvent("h"), Keys.ESCAPE, KeyboardEvent("q"), KeyboardEvent("Q") ->
                SelectorAction.Back
            else -> null
        }

    /** Compute the new cursor position after a navigation action. */
    internal fun applySelectorNav(
        action: SelectorAction,
        cursor: Int,
        size: Int,
    ): Int =
        when (action) {
            SelectorAction.MoveDown -> (cursor + 1).coerceAtMost(size - 1)
            SelectorAction.MoveUp -> (cursor - 1).coerceAtLeast(0)
            else -> cursor
        }

    /** Toggle a host's selection state, returning the updated set. */
    internal fun toggleHost(
        selected: Set<Host>,
        host: Host,
    ): Set<Host> = if (host in selected) selected - host else selected + host

    /** Effect produced by processing a selector event. */
    internal sealed interface SelectorEffect {
        /** Update cursor position and re-render. */
        data class Navigate(
            val newCursor: Int,
        ) : SelectorEffect

        /** Toggle selection of the host at [cursor] and re-render. */
        data class ToggleAt(
            val cursor: Int,
        ) : SelectorEffect

        /** Apply the current selection. */
        data object ApplySelection : SelectorEffect

        /** Exit the selector without applying. */
        data object Exit : SelectorEffect
    }

    /**
     * Process a [SelectorAction] against the current state, producing a [SelectorEffect]
     * that the interactive event loop should apply. Returns null for unrecognised input.
     */
    internal fun processSelectorEvent(
        action: SelectorAction?,
        cursor: Int,
        size: Int,
    ): SelectorEffect? =
        when (action) {
            SelectorAction.MoveDown, SelectorAction.MoveUp ->
                SelectorEffect.Navigate(applySelectorNav(action, cursor, size))
            SelectorAction.Toggle -> SelectorEffect.ToggleAt(cursor)
            SelectorAction.Apply -> SelectorEffect.ApplySelection
            SelectorAction.Back -> SelectorEffect.Exit
            null -> null
        }

    /** Detect which hosts are available on the system PATH. */
    internal fun detectHosts(): Set<Host> = Host.entries.filterTo(mutableSetOf()) { HostDetector.canRunExternal(it) }

    /** Build the host selector widget with cursor, checkboxes, and detection hints. */
    internal fun buildSelectorWidget(
        state: SelectorState,
        margin: String,
    ): Widget =
        verticalLayout {
            cell(Text("$margin${bold(brightGreen("opsx"))}  ${brightWhite("init")}"))
            cell(Text(""))
            cell(Text("$margin${brightWhite("Select hosts to configure:")}"))
            cell(Text(""))
            for ((index, host) in Host.entries.withIndex()) {
                val pointer = if (index == state.cursor) brightCyan("\u276f") else " "
                val checkbox = if (host in state.selected) green("\u25cf") else gray("\u25cb")
                val label = hostLabels[host] ?: host.id
                val detected = host !in state.detected
                val hint = if (detected) gray(" (not found on PATH)") else ""
                val hostId = if (index == state.cursor) brightWhite(host.id) else host.id
                cell(Text("$margin$pointer $checkbox  $hostId${gray("  $label")}$hint"))
            }
            cell(Text(""))
            cell(Text("${margin}All skills and agents will be installed for selected hosts."))
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

    /** Build the result widget showing what was emitted per host. */
    internal fun buildResultWidget(
        result: InitResult,
        margin: String,
    ): Widget =
        verticalLayout {
            cell(
                Text(
                    "$margin${bold(brightGreen("opsx"))}  ${brightWhite("init")}  " +
                        "${gray("\u2192")}  ${brightCyan("complete")}",
                ),
            )
            cell(Text(""))
            cell(Text("$margin${green("\u2713")} .opsx/config.json"))
            cell(Text(""))
            for (line in formatHostResultLines(result, margin)) {
                cell(Text(line))
            }
            val cleanedCount = result.cleanedPaths.size
            cell(Text("${margin}Cleaned up $cleanedCount legacy paths."))
            if (result.anyFailed) {
                cell(Text("$margin${red("\u2717")} some hosts failed"))
            } else {
                cell(Text("$margin${green("\u2713")} done"))
            }
        }

    /** Format per-host result lines for the result widget. */
    private fun formatHostResultLines(
        result: InitResult,
        margin: String,
    ): List<String> =
        buildList {
            for (hostResult in result.hosts) {
                addAll(formatSingleHostResult(hostResult, margin))
                add("")
            }
        }

    private fun formatSingleHostResult(
        hostResult: HostResult,
        margin: String,
    ): List<String> =
        buildList {
            val hostId = hostResult.host.id
            if (hostResult.error != null) {
                add("$margin${red("\u2717")} $hostId: ${hostResult.error}")
                return@buildList
            }
            add("$margin${brightWhite(hostId)}")
            val plan = hostResult.plan ?: return@buildList
            val skillCount = plan.writtenFiles.count { it.toString().contains("skills") }
            val agentCount = plan.writtenFiles.count { it.toString().contains("agents") }
            add("$margin  ${green("\u2713")} $skillCount skills  ${gray("\u2192 ${hostResult.host.skillsDir}/")}")
            val agentDir = hostResult.host.agentDir
            if (agentDir != null) {
                add("$margin  ${green("\u2713")} $agentCount agents  ${gray("\u2192 $agentDir/")}")
            }
            val marker = hostResult.host.instructionFile
            if (marker != null) {
                add("$margin  ${green("\u2713")} marker     ${gray("\u2192 $marker")}")
            }
            for (cmd in plan.externalCommands) {
                val glyph = if (cmd.optional) yellow("\u2192") else green("\u2192")
                add("$margin  $glyph ${cmd.description}")
            }
        }

    /** Mutable holder for selector state updated during the interactive loop. */
    private class SelectorHolder(
        var state: SelectorState,
    )

    /** Display the interactive host selector; blocks until the user navigates back or applies. */
    fun show(terminal: Terminal) {
        val detected = detectHosts()
        val holder = SelectorHolder(SelectorState(cursor = 0, selected = detected.toSet(), detected = detected))
        val margin = Styles.margin

        fun renderSelector() {
            renderSelectorWidget(terminal, holder.state, margin)
        }
        renderSelector()

        val applied =
            AppShell.readKeyInput(
                terminal,
                handler = { event ->
                    val effect =
                        processSelectorEvent(translateSelectorKey(event), holder.state.cursor, Host.entries.size)
                    applySelectorEffect(effect, holder, ::renderSelector)
                },
                onResize = { renderSelector() },
            )

        if (!applied) return

        val selectedHosts = Host.entries.filter { it in holder.state.selected }
        val hostNames = selectedHosts.joinToString(", ") { it.id }
        val confirmed = ConfirmDialog.show(terminal, "Install skills and agents for $hostNames?")
        if (!confirmed) return

        val result = InitRunner.run(Path("."), selectedHosts)
        showResult(terminal, result, selectedHosts.size)
    }

    private fun renderSelectorWidget(
        terminal: Terminal,
        state: SelectorState,
        margin: String,
    ) {
        val selectedCount = state.selected.size
        val totalCount = Host.entries.size
        AppShell.render(
            terminal = terminal,
            content = buildSelectorWidget(state, margin),
            barLeft = "  space toggle  \u21b5 apply  \u2190 back",
            barRight = "$selectedCount/$totalCount selected  ",
        )
    }

    private fun applySelectorEffect(
        effect: SelectorEffect?,
        holder: SelectorHolder,
        renderSelector: () -> Unit,
    ): Status<Boolean> =
        when (effect) {
            is SelectorEffect.Navigate -> {
                holder.state = holder.state.copy(cursor = effect.newCursor)
                renderSelector()
                Status.Continue
            }
            is SelectorEffect.ToggleAt -> {
                val host = Host.entries[effect.cursor]
                holder.state = holder.state.copy(selected = toggleHost(holder.state.selected, host))
                renderSelector()
                Status.Continue
            }
            SelectorEffect.ApplySelection -> Status.Finished(true)
            SelectorEffect.Exit -> Status.Finished(false)
            null -> Status.Continue
        }

    /** Display the result screen after init completes; blocks until dismissed. */
    private fun showResult(
        terminal: Terminal,
        result: InitResult,
        hostCount: Int,
    ) {
        val margin = Styles.margin

        fun renderResult() {
            AppShell.render(
                terminal = terminal,
                content = buildResultWidget(result, margin),
                barLeft = "  \u21b5 done",
                barRight = "$hostCount hosts  ",
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
