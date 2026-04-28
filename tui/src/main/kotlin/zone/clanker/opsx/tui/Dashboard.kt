/**
 * Main menu TUI for opsx -- full-screen dashboard with ASCII logo, navigable menu,
 * and a pinned status bar. Uses Mordant's [com.github.ajalt.mordant.animation.Animation]
 * with a [com.github.ajalt.mordant.widgets.Viewport] widget for full-screen layout.
 */
package zone.clanker.opsx.tui

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.InputReceiver.Status
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.input.isCtrlC
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.SelectList
import kotlinx.io.files.Path
import zone.clanker.opsx.cli.BuildConfig
import zone.clanker.opsx.tui.init.InitView
import zone.clanker.opsx.tui.install.InstallView
import zone.clanker.opsx.tui.nuke.NukeView
import zone.clanker.opsx.tui.render.AppShell
import zone.clanker.opsx.tui.render.Styles
import zone.clanker.opsx.tui.status.StatusView
import zone.clanker.opsx.tui.update.UpdateView

/** Top-level TUI dashboard — full-screen with logo, menu, and status bar. */
@Suppress("TooManyFunctions")
object Dashboard {
    private val version: String = BuildConfig.VERSION

    internal data class MenuItem(
        val key: String,
        val label: String,
        val desc: String,
    )

    internal val menuItems =
        listOf(
            MenuItem("status", "status", "View changes and progress"),
            MenuItem("init", "init", "Configure workspace hosts"),
            MenuItem("install", "install", "Install opsx globally"),
            MenuItem("update", "update", "Check for updates"),
            MenuItem("nuke", "nuke", "Uninstall opsx"),
        )

    @Suppress("MaxLineLength")
    internal val logos =
        listOf(
            listOf(
                " ██████╗ ██████╗ ███████╗██╗  ██╗",
                "██╔═══██╗██╔══██╗██╔════╝╚██╗██╔╝",
                "██║   ██║██████╔╝███████╗ ╚███╔╝ ",
                "██║   ██║██╔═══╝ ╚════██║ ██╔██╗ ",
                "╚██████╔╝██║     ███████║██╔╝ ██╗",
                " ╚═════╝ ╚═╝     ╚══════╝╚═╝  ╚═╝",
            ),
        )

    /** Semantic action produced by [translateKey] and [translateMouse]. */
    internal sealed interface MenuAction {
        data object MoveDown : MenuAction

        data object MoveUp : MenuAction

        data object Select : MenuAction

        data object Quit : MenuAction

        /** Mouse click on a menu item at [index]. */
        data class ClickItem(
            val index: Int,
        ) : MenuAction
    }

    internal data class MenuState(
        val cursor: Int,
    )

    /** Non-interactive message for when the TUI is launched in a non-terminal context. */
    internal const val NON_INTERACTIVE_MSG = "Run the built binary directly:\n  ./app/build/install/app-shadow/bin/opsx"

    fun run() {
        start(Terminal())
    }

    /**
     * Launch the dashboard on the given [terminal].
     * Extracted from [run] so tests can inject a [TerminalRecorder] terminal.
     *
     * @param loop the main event loop; defaults to [mainLoop].
     *   Tests can supply a no-op to verify the full-screen enter/exit logic.
     */
    internal fun start(
        terminal: Terminal,
        loop: (Terminal) -> Unit = ::mainLoop,
    ) {
        if (!terminal.terminalInfo.interactive) {
            terminal.println(NON_INTERACTIVE_MSG)
            return
        }
        enterDashboard(terminal)
        runCatching { loop(terminal) }.also { leaveDashboard(terminal) }.getOrThrow()
    }

    /** Prepare full-screen mode: pick a logo and switch to the alternate screen. */
    internal fun enterDashboard(terminal: Terminal) {
        AppShell.pickLogo(logos)
        Styles.enterFullScreen(terminal)
    }

    /** Exit full-screen mode: restore the main screen buffer. */
    internal fun leaveDashboard(terminal: Terminal) {
        Styles.leaveFullScreen(terminal)
    }

    /** Compute the menu start row based on logo height and top padding. */
    internal fun menuStartRow(): Int = 1 + AppShell.currentLogo().size + 1

    /** Render the dashboard menu at the given cursor position. */
    internal fun renderMenu(
        terminal: Terminal,
        cursor: Int,
    ) {
        val maxLabel = menuItems.maxOf { it.label.length }
        AppShell.render(
            terminal = terminal,
            content = buildMenuWidget(formatMenuEntries(maxLabel), cursor),
            barLeft = "  \u21B5 select  \u2191/k up  \u2193/j down  q quit",
            barRight = "opsx v$version  ",
        )
    }

    private fun mainLoop(terminal: Terminal) {
        val menuStartRow = menuStartRow()
        var cursor = 0
        while (true) {
            renderMenu(terminal, cursor)
            val selection =
                readMenuSelection(terminal, cursor, menuStartRow) { renderMenu(terminal, it) }
            selection ?: return
            cursor = selection.first
            dispatchMenuItem(selection.second, terminal)
        }
    }

    /**
     * Result of processing a [MenuAction] against the current cursor state.
     * Navigation actions produce a new cursor; Select produces the selection; Quit exits.
     */
    internal sealed interface ActionResult {
        data class Navigate(
            val cursor: Int,
        ) : ActionResult

        data class Selected(
            val cursor: Int,
            val key: String,
        ) : ActionResult

        data object Exit : ActionResult
    }

    /**
     * Apply a [MenuAction] (or null for no-op) to the current [cursor] position.
     * Returns an [ActionResult] describing what happened.
     */
    internal fun applyAction(
        action: MenuAction?,
        cursor: Int,
    ): ActionResult? =
        when (action) {
            null -> null
            MenuAction.MoveDown -> ActionResult.Navigate((cursor + 1).coerceAtMost(menuItems.size - 1))
            MenuAction.MoveUp -> ActionResult.Navigate((cursor - 1).coerceAtLeast(0))
            MenuAction.Select -> ActionResult.Selected(cursor, menuItems[cursor].key)
            MenuAction.Quit -> ActionResult.Exit
            is MenuAction.ClickItem -> ActionResult.Selected(action.index, menuItems[action.index].key)
        }

    /**
     * Convert an [ActionResult] into either an updated cursor (continue) or a final selection (finish).
     * Returns a [Status] for the raw-mode event loop.
     */
    internal fun actionToStatus(result: ActionResult?): Status<Pair<Int, String>?> =
        when (result) {
            null -> Status.Continue
            is ActionResult.Navigate -> Status.Continue
            is ActionResult.Selected -> Status.Finished(result.cursor to result.key)
            is ActionResult.Exit -> Status.Finished(null)
        }

    /**
     * Process a single menu input event, returning a [Status] for the event loop and
     * updating [cursorHolder] when navigating. Calls [renderMenu] on cursor changes.
     */
    internal fun processMenuInput(
        event: InputEvent,
        menuStartRow: Int,
        cursorHolder: IntArray,
        renderMenu: (Int) -> Unit,
    ): Status<Pair<Int, String>?> {
        val result = applyAction(handleMenuEvent(event, menuStartRow), cursorHolder[0])
        if (result is ActionResult.Navigate) {
            cursorHolder[0] = result.cursor
            renderMenu(cursorHolder[0])
        }
        return actionToStatus(result)
    }

    private fun readMenuSelection(
        terminal: Terminal,
        cur: Int,
        menuStartRow: Int,
        renderMenu: (Int) -> Unit,
    ): Pair<Int, String>? {
        val cursorHolder = intArrayOf(cur)
        return AppShell.readInput(
            terminal = terminal,
            handler = { event -> processMenuInput(event, menuStartRow, cursorHolder, renderMenu) },
            onResize = { renderMenu(cursorHolder[0]) },
        )
    }

    /**
     * Translate an [InputEvent] into a [MenuAction], or `null` for unrecognised input.
     * Handles keyboard (arrows, vim keys, q) and mouse (click to select, scroll to navigate).
     */
    internal fun handleMenuEvent(
        event: InputEvent,
        menuStartRow: Int = 0,
    ): MenuAction? =
        when (event) {
            is KeyboardEvent -> translateKey(event)
            is MouseEvent -> translateMouse(event, menuStartRow)
        }

    /**
     * Map a [MouseEvent] to a [MenuAction]:
     * - Scroll wheel up/down → MoveUp/MoveDown
     * - Left click on a menu row → ClickItem(index)
     */
    internal fun translateMouse(
        event: MouseEvent,
        menuStartRow: Int,
    ): MenuAction? =
        when {
            event.wheelUp -> MenuAction.MoveUp
            event.wheelDown -> MenuAction.MoveDown
            event.left -> {
                val index = event.y - menuStartRow
                if (index in menuItems.indices) MenuAction.ClickItem(index) else null
            }
            else -> null
        }

    /**
     * Map a [KeyboardEvent] to a semantic [MenuAction]:
     * `MoveUp`, `MoveDown`, `Select`, `Quit`, or `null` for unrecognised keys.
     */
    internal fun translateKey(event: KeyboardEvent): MenuAction? =
        when {
            event.key == "ArrowDown" || event.key == "j" -> MenuAction.MoveDown
            event.key == "ArrowUp" || event.key == "k" -> MenuAction.MoveUp
            event.key == "Enter" || event.key == "l" -> MenuAction.Select
            event.key == "q" || event.key == "Q" || event.key == "Escape" -> MenuAction.Quit
            event.isCtrlC -> MenuAction.Quit
            else -> null
        }

    internal fun dispatchMenuItem(
        key: String,
        terminal: Terminal,
    ): Unit =
        when (key) {
            "status" -> StatusView.show(terminal, Path("."))
            "init" -> InitView.show(terminal)
            "update" -> UpdateView.show(terminal)
            "install" -> InstallView.show(terminal)
            "nuke" -> NukeView.show(terminal)
            else -> Unit
        }

    /** Format menu items into [SelectList.Entry] instances with padded labels and descriptions. */
    internal fun formatMenuEntries(maxLabel: Int): List<SelectList.Entry> =
        menuItems.map { item ->
            SelectList.Entry("${item.label.padEnd(maxLabel)}   ${Styles.descStyle(item.desc)}")
        }

    /** Build a [SelectList] widget from the menu entries. */
    internal fun buildMenuWidget(
        entries: List<SelectList.Entry>,
        cursorIndex: Int,
    ): Widget {
        val margin = Styles.margin
        return SelectList(
            entries = entries,
            cursorIndex = cursorIndex,
            cursorMarker = "$margin\u276F ",
            selectedMarker = "",
            unselectedMarker = "$margin  ",
        )
    }
}
