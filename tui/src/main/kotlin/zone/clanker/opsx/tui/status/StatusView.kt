/**
 * Interactive TUI status view -- navigable change ledger with drill-down into
 * per-change activity trees. Uses [AppShell.render] for full-screen rendering.
 */
package zone.clanker.opsx.tui.status

import com.github.ajalt.mordant.input.InputReceiver.Status
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextColors.brightCyan
import com.github.ajalt.mordant.rendering.TextColors.brightGreen
import com.github.ajalt.mordant.rendering.TextColors.brightWhite
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.table.verticalLayout
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text
import kotlinx.io.files.Path
import zone.clanker.opsx.cli.status.ActivityEvent
import zone.clanker.opsx.cli.status.ActivityLog
import zone.clanker.opsx.cli.status.ChangeEntry
import zone.clanker.opsx.cli.status.ChangeScanner
import zone.clanker.opsx.cli.status.State
import zone.clanker.opsx.cli.status.Style
import zone.clanker.opsx.tui.render.AppShell
import zone.clanker.opsx.tui.render.Keys
import zone.clanker.opsx.tui.render.Styles

/**
 * Full-screen interactive change ledger with drill-down.
 *
 * The top-level view lists all changes with badges and progress bars.
 * Pressing Enter on a change opens a tree view showing activity events
 * grouped by agent. Supports j/k navigation, r to refresh, and h/Escape to go back.
 */
object StatusView {
    internal data class LedgerState(
        val cursor: Int,
        val entries: List<ChangeEntry>,
    )

    /** Semantic action from a key event in the ledger view. */
    internal sealed interface LedgerAction {
        data object MoveDown : LedgerAction

        data object MoveUp : LedgerAction

        data object Open : LedgerAction

        data object Refresh : LedgerAction

        data object Back : LedgerAction
    }

    /** Map a [KeyboardEvent] to a [LedgerAction], or null for unrecognised keys. */
    internal fun translateLedgerKey(event: KeyboardEvent): LedgerAction? =
        when (event) {
            Keys.DOWN, KeyboardEvent("j") -> LedgerAction.MoveDown
            Keys.UP, KeyboardEvent("k") -> LedgerAction.MoveUp
            Keys.ENTER, KeyboardEvent("l"), Keys.RIGHT -> LedgerAction.Open
            KeyboardEvent("r") -> LedgerAction.Refresh
            KeyboardEvent("q"), KeyboardEvent("Q"), Keys.ESCAPE, KeyboardEvent("h"), Keys.LEFT ->
                LedgerAction.Back
            else -> null
        }

    /** Compute the new cursor position after a navigation action. */
    internal fun applyLedgerNav(
        action: LedgerAction,
        cursor: Int,
        size: Int,
    ): Int =
        when (action) {
            LedgerAction.MoveDown -> (cursor + 1).coerceAtMost(size - 1)
            LedgerAction.MoveUp -> (cursor - 1).coerceAtLeast(0)
            else -> cursor
        }

    /** Semantic action from a key event in the tree detail view. */
    internal sealed interface TreeAction {
        data object Back : TreeAction
    }

    /** Map a [KeyboardEvent] to a [TreeAction], or null for unrecognised keys. */
    internal fun translateTreeKey(event: KeyboardEvent): TreeAction? =
        when (event) {
            Keys.LEFT, KeyboardEvent("h"), Keys.ESCAPE, KeyboardEvent("q") -> TreeAction.Back
            else -> null
        }

    /** Semantic action from a key event in the message screen. */
    internal sealed interface MessageAction {
        data object Dismiss : MessageAction
    }

    /** Map a [KeyboardEvent] to a [MessageAction], or null for unrecognised keys. */
    internal fun translateMessageKey(event: KeyboardEvent): MessageAction? =
        when (event) {
            Keys.LEFT, KeyboardEvent("h"), Keys.ESCAPE, KeyboardEvent("q"), Keys.ENTER ->
                MessageAction.Dismiss
            else -> null
        }

    /** Refresh ledger state from disk, returning the updated cursor clamped to the entry list. */
    internal fun refreshEntries(
        rootDir: Path,
        currentCursor: Int,
    ): Pair<List<ChangeEntry>, Int> {
        val fresh = ChangeScanner.scan(rootDir)
        val cursor = if (fresh.isEmpty()) 0 else currentCursor.coerceIn(0, fresh.size - 1)
        return fresh to cursor
    }

    /** Effect produced by [processLedgerEvent] — describes what the event loop should do. */
    internal sealed interface LedgerEffect {
        /** Update the cursor position and re-render. */
        data class Navigate(
            val newCursor: Int,
        ) : LedgerEffect

        /** Open the detail view for the entry at [cursor]. */
        data class OpenDetail(
            val cursor: Int,
        ) : LedgerEffect

        /** Reload entries from disk. */
        data object Reload : LedgerEffect

        /** Exit the ledger view. */
        data object Exit : LedgerEffect
    }

    /**
     * Process a [LedgerAction] against the current state, producing a [LedgerEffect]
     * that the interactive event loop should apply. Returns null for unrecognised input.
     */
    internal fun processLedgerEvent(
        action: LedgerAction?,
        cursor: Int,
        size: Int,
    ): LedgerEffect? =
        when (action) {
            LedgerAction.MoveDown, LedgerAction.MoveUp -> {
                LedgerEffect.Navigate(applyLedgerNav(action, cursor, size))
            }
            LedgerAction.Open -> LedgerEffect.OpenDetail(cursor)
            LedgerAction.Refresh -> LedgerEffect.Reload
            LedgerAction.Back -> LedgerEffect.Exit
            null -> null
        }

    /** Mutable holder for ledger state updated during the interactive loop. */
    private class LedgerHolder(
        var entries: List<ChangeEntry>,
        var cursor: Int,
    )

    /** Display the interactive change ledger; blocks until the user navigates back or quits. */
    fun show(
        terminal: Terminal,
        rootDir: Path,
    ) {
        val (initialEntries, initialCursor) = refreshEntries(rootDir, 0)
        if (initialEntries.isEmpty()) {
            messageScreen(terminal, "No changes found. Use /opsx-propose to start one.")
            return
        }
        val holder = LedgerHolder(initialEntries, initialCursor)
        val margin = Styles.margin

        fun renderLedger() {
            val state = LedgerState(holder.cursor, holder.entries)
            AppShell.render(
                terminal = terminal,
                content = buildLedgerWidget(state, margin),
                barLeft = "  \u21b5 open  \u2191/k up  \u2193/j down  r refresh  \u2190 back",
                barRight = "${state.cursor + 1}/${state.entries.size}  ",
            )
        }
        renderLedger()

        AppShell.readKeyInput(
            terminal,
            handler = { event ->
                val effect = processLedgerEvent(translateLedgerKey(event), holder.cursor, holder.entries.size)
                applyLedgerEffect(effect, holder, terminal, rootDir, ::renderLedger)
            },
            onResize = { renderLedger() },
        )
    }

    private fun applyLedgerEffect(
        effect: LedgerEffect?,
        holder: LedgerHolder,
        terminal: Terminal,
        rootDir: Path,
        renderLedger: () -> Unit,
    ): Status<Unit> =
        when (effect) {
            is LedgerEffect.Navigate -> {
                holder.cursor = effect.newCursor
                renderLedger()
                Status.Continue
            }
            is LedgerEffect.OpenDetail -> {
                treeView(terminal, rootDir, holder.entries[effect.cursor])
                val (freshEntries, freshCursor) = refreshEntries(rootDir, holder.cursor)
                holder.entries = freshEntries
                holder.cursor = freshCursor
                renderLedger()
                Status.Continue
            }
            LedgerEffect.Reload -> {
                val (freshEntries, freshCursor) = refreshEntries(rootDir, holder.cursor)
                holder.entries = freshEntries
                holder.cursor = freshCursor
                renderLedger()
                Status.Continue
            }
            LedgerEffect.Exit -> Status.Finished(Unit)
            null -> Status.Continue
        }

    internal fun buildLedgerWidget(
        state: LedgerState,
        margin: String,
    ): Widget =
        verticalLayout {
            val header =
                "$margin${bold(brightGreen("opsx"))}  " +
                    "${brightWhite("status")}  ${gray("${state.entries.size} changes")}"
            cell(Text(header))
            cell(Text(""))
            for ((i, entry) in state.entries.withIndex()) {
                val pointer = if (i == state.cursor) brightCyan("\u276f") else " "
                val badge = Style.badge(entry.status)
                val bar = Style.progressBar(entry.done, entry.total)
                val name = if (i == state.cursor) brightWhite(entry.name) else entry.name
                cell(Text("$margin$pointer $badge  $bar  $name"))
            }
        }

    private fun treeView(
        terminal: Terminal,
        rootDir: Path,
        entry: ChangeEntry,
    ) {
        val events = ActivityLog.read(rootDir, entry.name)
        val margin = Styles.margin

        fun renderTree() {
            AppShell.render(
                terminal = terminal,
                content = buildTreeWidget(margin, entry, events),
                barLeft = "  \u2190 h back",
                barRight = "${entry.done}/${entry.total} tasks  ",
            )
        }
        renderTree()
        AppShell.readKeyInput(
            terminal,
            handler = { event ->
                when (translateTreeKey(event)) {
                    null -> Status.Continue
                    else -> Status.Finished(Unit)
                }
            },
            onResize = { renderTree() },
        )
    }

    internal fun buildTreeWidget(
        margin: String,
        entry: ChangeEntry,
        events: List<ActivityEvent>,
    ): Widget {
        val header =
            "$margin${bold(brightGreen("opsx"))}  " +
                "${brightWhite("status")}  ${gray("\u2192")}  ${cyan(entry.name)}"
        val badge = Style.badge(entry.status)
        val bar = Style.progressBar(entry.done, entry.total)

        return verticalLayout {
            cell(Text(header))
            cell(Text(""))
            cell(Text("$margin$badge  $bar"))
            cell(Text(""))

            if (events.isEmpty()) {
                cell(Text("$margin${gray("no activity recorded yet")}"))
            } else {
                for (line in formatAgentEventLines(margin, events)) {
                    cell(Text(line))
                }
            }
        }
    }

    /** Format grouped activity events into styled lines, one per agent section. */
    private fun formatAgentEventLines(
        margin: String,
        events: List<ActivityEvent>,
    ): List<String> =
        buildList {
            events.groupBy { it.agent }.forEach { (agent, agentEvents) ->
                val color = Style.agentColors[agent]
                add("$margin${color?.let { it("@$agent") } ?: "@$agent"}")
                for (event in agentEvents) {
                    val glyph =
                        when (event.state) {
                            State.START -> gray("\u2192")
                            State.DONE -> green("\u2713")
                            State.FAILED -> red("\u2717")
                        }
                    val taskLabel = event.task?.let { gray("[$it] ") } ?: ""
                    add("$margin  $glyph $taskLabel${event.desc ?: ""}")
                }
                add("")
            }
        }

    /** Build the message screen widget shown when there are no changes. */
    internal fun buildMessageWidget(
        message: String,
        margin: String,
    ): Widget =
        verticalLayout {
            cell(Text("$margin${bold(brightGreen("opsx"))}  ${brightWhite("status")}"))
            cell(Text(""))
            cell(Text("$margin$message"))
        }

    private fun messageScreen(
        terminal: Terminal,
        message: String,
    ) {
        val margin = Styles.margin

        fun renderMessage() {
            AppShell.render(
                terminal = terminal,
                content = buildMessageWidget(message, margin),
                barLeft = "  \u2190 back",
                barRight = "",
            )
        }
        renderMessage()
        AppShell.readKeyInput(
            terminal,
            handler = { event ->
                when (translateMessageKey(event)) {
                    null -> Status.Continue
                    else -> Status.Finished(Unit)
                }
            },
            onResize = { renderMessage() },
        )
    }
}
