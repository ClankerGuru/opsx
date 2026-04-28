/**
 * Shared styling constants, key bindings, and color utilities for the opsx TUI.
 *
 * All rendering is handled by Mordant's [com.github.ajalt.mordant.animation.Animation]
 * pattern -- this file provides reusable theme pieces consumed by every view.
 */
package zone.clanker.opsx.tui.render

import com.github.ajalt.colormath.model.RGB
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.rendering.TextStyles.bold

/**
 * Reusable [KeyboardEvent] constants for key matching.
 *
 * Mordant's own [com.github.ajalt.mordant.input.InteractiveSelectListBuilder] uses
 * this same pattern — comparing `event == Keys.DOWN` instead of `event.key == "ArrowDown"`.
 */
object Keys {
    val UP = KeyboardEvent("ArrowUp")
    val DOWN = KeyboardEvent("ArrowDown")
    val LEFT = KeyboardEvent("ArrowLeft")
    val RIGHT = KeyboardEvent("ArrowRight")
    val ENTER = KeyboardEvent("Enter")
    val ESCAPE = KeyboardEvent("Escape")
    val BACKSPACE = KeyboardEvent("Backspace")
    val TAB = KeyboardEvent("Tab")
    val SPACE = KeyboardEvent(" ")
}

object Styles {
    const val PAD = 6
    const val BAR_FILL = 40
    val margin: String = " ".repeat(PAD)

    /**
     * Enter full-screen mode: switches to the alternate screen buffer and hides the cursor.
     * Call [leaveFullScreen] to restore the normal terminal.
     */
    fun enterFullScreen(terminal: com.github.ajalt.mordant.terminal.Terminal) {
        terminal.cursor.hide(showOnExit = true)
        terminal.rawPrint("\u001B[?1049h")
        terminal.cursor.move { clearScreen() }
    }

    /**
     * Leave full-screen mode: restores the main screen buffer and shows the cursor.
     */
    fun leaveFullScreen(terminal: com.github.ajalt.mordant.terminal.Terminal) {
        terminal.rawPrint("\u001B[?1049l")
        terminal.cursor.show()
    }

    val barStyle: TextStyle =
        TextStyle(
            color = TextColors.brightWhite,
            bgColor = RGB("#005f00"),
            bold = true,
        )

    /** Style for menu item descriptions — readable but clearly secondary. */
    val descStyle: TextStyle = TextColors.rgb("#88c0d0") + TextStyles.dim

    /** Return a gradient [TextStyle] for logo line [index] out of [total] lines. */
    fun gradientColor(
        index: Int,
        total: Int,
    ): TextStyle {
        val ratio = if (total <= 1) 0.0 else index.toDouble() / (total - 1)
        val red = ((100 + (0 - 100) * ratio) / 255.0).coerceIn(0.0, 1.0)
        val green = ((255 + (200 - 255) * ratio) / 255.0).coerceIn(0.0, 1.0)
        val blue = ((100 + (180 - 100) * ratio) / 255.0).coerceIn(0.0, 1.0)
        return bold + TextColors.rgb(red, green, blue)
    }
}
