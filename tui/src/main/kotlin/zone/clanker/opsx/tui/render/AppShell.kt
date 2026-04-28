/**
 * Shared full-screen layout shell for the opsx TUI.
 *
 * Every view uses [AppShell.wrap] to compose the standard layout:
 * logo at the top, content in the middle, status bar pinned at the bottom.
 * This ensures consistent chrome across all screens and prevents layout
 * corruption when navigating between views.
 */
package zone.clanker.opsx.tui.render

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.InputReceiver.Status
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.rendering.Lines
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.rendering.WidthRange
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text
import kotlin.time.Duration.Companion.milliseconds

/**
 * Assembles the full-screen layout: logo, content, padding, status bar.
 *
 * The logo is chosen once per session and stored here. All views call [wrap]
 * to get a consistently-sized [ShellWidget] that fills the terminal.
 */
object AppShell {
    private var sessionLogo: List<String> = emptyList()

    /** Pick a random logo for this session. Call once at startup. */
    fun pickLogo(logos: List<List<String>>) {
        sessionLogo = logos.random()
        lastSize = null
    }

    /** The current session logo (for testing). */
    internal fun currentLogo(): List<String> = sessionLogo

    /**
     * Clear the screen and reset cursor to home position.
     * Call this between view transitions to prevent glitch frames.
     */
    fun transition(terminal: Terminal) {
        terminal.cursor.move {
            setPosition(0, 0)
            clearScreen()
        }
    }

    /**
     * Clear the screen and render [content] wrapped in the standard layout.
     * This is the primary render method for full-screen views.
     * Call on every state change and on idle (for resize).
     */
    internal var lastSize: com.github.ajalt.mordant.rendering.Size? = null

    /**
     * Render [content] wrapped in the standard layout.
     * Renders the widget to a string, then writes each line at an absolute
     * row position using raw ANSI. Each line is padded to full width and
     * erased before writing, so resize works without flash.
     */
    fun render(
        terminal: Terminal,
        content: Widget,
        barLeft: String,
        barRight: String,
    ) {
        terminal.updateSize()
        val height = terminal.size.height
        val width = terminal.size.width
        lastSize = terminal.size

        val rendered = terminal.render(wrap(content, barLeft, barRight))
        val lines = rendered.lines()

        val buffer = StringBuilder()
        for (row in 0 until height) {
            buffer.append("\u001B[${row + 1};1H\u001B[2K")
            if (row < lines.size) {
                buffer.append(lines[row])
            }
        }
        terminal.rawPrint(buffer.toString())
    }

    /**
     * Wrap [content] in the standard opsx layout.
     *
     * Returns a [ShellWidget] that measures the content, logo, and status bar,
     * then pads vertically to fill the terminal height. No need to pass
     * `contentLines` — the widget measures itself.
     *
     * @param content the view-specific widget to display in the middle
     * @param barLeft status bar left text (key hints)
     * @param barRight status bar right text (version, counts, etc.)
     */
    fun wrap(
        content: Widget,
        barLeft: String,
        barRight: String,
    ): Widget =
        ShellWidget(
            logoWidget = LogoWidget(),
            content = content,
            barLeft = barLeft,
            barRight = barRight,
        )

    private val RESIZE_POLL_TIMEOUT = 100.milliseconds

    /**
     * Enter raw mode, read events with a 100ms timeout.
     * On input, calls [handler]. On timeout, checks if terminal size changed
     * and only calls [onResize] if it did — avoids hammering the terminal.
     * Blocks until [handler] returns [Status.Finished].
     */
    fun <T> readInput(
        terminal: Terminal,
        handler: (InputEvent) -> Status<T>,
        onResize: () -> Unit = {},
    ): T {
        val rawMode = terminal.enterRawMode(MouseTracking.Off)
        return runCatching {
            pollUntilFinished(rawMode, terminal, handler, onResize)
        }.also { rawMode.close() }.getOrThrow()
    }

    private fun <T> pollUntilFinished(
        rawMode: com.github.ajalt.mordant.input.RawModeScope,
        terminal: Terminal,
        handler: (InputEvent) -> Status<T>,
        onResize: () -> Unit,
    ): T {
        var lastPolledSize = terminal.size
        var pendingResize = false
        while (true) {
            val event = rawMode.readEventOrNull(RESIZE_POLL_TIMEOUT)
            if (event == null) {
                terminal.updateSize()
                val currentSize = terminal.size
                if (currentSize != lastPolledSize) {
                    lastPolledSize = currentSize
                    pendingResize = true
                } else if (pendingResize) {
                    pendingResize = false
                    onResize()
                }
                continue
            }
            val status = handler(event)
            if (status is Status.Finished) return status.result
        }
    }

    /**
     * Simplified [readInput] for views that only handle keyboard events.
     * Ignores mouse events. Calls [onResize] only when terminal size changes.
     */
    fun <T> readKeyInput(
        terminal: Terminal,
        handler: (KeyboardEvent) -> Status<T>,
        onResize: () -> Unit = {},
    ): T =
        readInput(
            terminal = terminal,
            handler = { event ->
                when (event) {
                    is KeyboardEvent -> handler(event)
                    else -> Status.Continue
                }
            },
            onResize = onResize,
        )

    internal fun formatLogoLines(): List<String> {
        val margin = Styles.margin
        return sessionLogo.mapIndexed { index, line ->
            "$margin${Styles.gradientColor(index, sessionLogo.size)(line)}"
        }
    }
}

/**
 * A [Widget] that renders the logo with gradient styling.
 * Implements [measure] and [render] properly so the shell can calculate its height.
 */
internal class LogoWidget : Widget {
    private val styledLines = AppShell.formatLogoLines()

    override fun measure(
        t: Terminal,
        width: Int,
    ): WidthRange {
        val maxWidth = styledLines.maxOfOrNull { it.length } ?: 0
        return WidthRange(maxWidth, maxWidth)
    }

    override fun render(
        t: Terminal,
        width: Int,
    ): Lines {
        val widgets = styledLines.map { Text(it) }
        val allLines = widgets.flatMap { it.render(t, width).lines }
        return Lines(allLines)
    }
}

/**
 * Full-screen layout widget: blank + logo + blank + content + padding + status bar.
 *
 * Uses [Widget.render] on sub-widgets to measure actual heights, then pads
 * dynamically to fill the terminal. No pre-calculated `contentLines` needed.
 */
internal class ShellWidget(
    private val logoWidget: LogoWidget,
    private val content: Widget,
    private val barLeft: String,
    private val barRight: String,
) : Widget {
    override fun measure(
        t: Terminal,
        width: Int,
    ): WidthRange = WidthRange(width, width)

    override fun render(
        t: Terminal,
        width: Int,
    ): Lines {
        val height = t.size.height
        val blankLine = Text(" ").render(t, width)
        val logoRendered = logoWidget.render(t, width)
        val contentRendered = content.render(t, width)

        val fill = maxOf(0, width - barLeft.length - barRight.length)
        val barText = Styles.barStyle("$barLeft${" ".repeat(fill)}$barRight")
        val barRendered = Text(barText).render(t, width)

        val usedHeight = 1 + logoRendered.height + 1 + contentRendered.height + 1 + barRendered.height
        val paddingCount = maxOf(0, height - usedHeight)

        val allLines =
            buildList {
                addAll(blankLine.lines)
                addAll(logoRendered.lines)
                addAll(blankLine.lines)
                addAll(contentRendered.lines)
                addAll(blankLine.lines)
                repeat(paddingCount) { addAll(blankLine.lines) }
                addAll(barRendered.lines)
            }

        return Lines(allLines)
    }
}
