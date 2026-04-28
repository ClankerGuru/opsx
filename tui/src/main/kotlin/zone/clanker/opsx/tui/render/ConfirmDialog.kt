/**
 * Reusable gum-style confirmation dialog for the opsx TUI.
 *
 * Renders two side-by-side buttons (affirmative / negative) with the focused
 * button highlighted.  Uses [AppShell.render] for full-screen layout and
 * [AppShell.readKeyInput] for input with resize support.
 *
 * Key bindings match Charmbracelet's `gum confirm`:
 * - Left, Right, Tab, h, l -- toggle focus between buttons
 * - Enter -- submit the currently focused button
 * - y, Y -- immediately return true
 * - n, N, q, Escape -- immediately return false
 */
package zone.clanker.opsx.tui.render

import com.github.ajalt.mordant.input.InputReceiver.Status
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.table.verticalLayout
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text

/** Semantic action produced by [ConfirmDialog.translateKey]. */
internal sealed interface DialogAction {
    /** Move focus to the affirmative (left) button. */
    data object ToggleLeft : DialogAction

    /** Move focus to the negative (right) button. */
    data object ToggleRight : DialogAction

    /** Toggle focus between the two buttons. */
    data object Toggle : DialogAction

    /** Submit whichever button is currently focused. */
    data object Submit : DialogAction

    /** Immediately select "yes" and return. */
    data object SelectYes : DialogAction

    /** Immediately select "no" and return. */
    data object SelectNo : DialogAction

    /** Cancel the dialog (equivalent to selecting "no"). */
    data object Cancel : DialogAction
}

/**
 * A gum-style two-button confirmation dialog.
 *
 * Call [show] for the full interactive experience or use [buildWidget] and
 * [translateKey] directly in tests.
 */
object ConfirmDialog {
    private val focusedStyle = Styles.barStyle
    private val unfocusedStyle = TextColors.gray + TextStyles.dim

    /** Show a confirmation dialog.  Returns `true` for yes, `false` for no/cancel. */
    fun show(
        terminal: Terminal,
        prompt: String,
        affirmative: String = "Yes",
        negative: String = "No",
        defaultYes: Boolean = true,
    ): Boolean {
        var selectedYes = defaultYes

        fun render() {
            AppShell.render(
                terminal = terminal,
                content = buildWidget(prompt, affirmative, negative, selectedYes),
                barLeft = "  \u2190/h  \u2192/l toggle  \u21b5 confirm  y yes  n no",
                barRight = "",
            )
        }

        render()

        return AppShell.readKeyInput(
            terminal,
            handler = { event ->
                when (translateKey(event)) {
                    DialogAction.ToggleLeft -> {
                        selectedYes = true
                        render()
                        Status.Continue
                    }
                    DialogAction.ToggleRight -> {
                        selectedYes = false
                        render()
                        Status.Continue
                    }
                    DialogAction.Toggle -> {
                        selectedYes = !selectedYes
                        render()
                        Status.Continue
                    }
                    DialogAction.Submit -> Status.Finished(selectedYes)
                    DialogAction.SelectYes -> Status.Finished(true)
                    DialogAction.SelectNo, DialogAction.Cancel -> Status.Finished(false)
                    null -> Status.Continue
                }
            },
            onResize = { render() },
        )
    }

    /** Build the dialog widget for the given state. */
    internal fun buildWidget(
        prompt: String,
        affirmative: String,
        negative: String,
        selectedYes: Boolean,
    ): Widget {
        val margin = Styles.margin
        val yesText =
            if (selectedYes) {
                focusedStyle("  [ $affirmative ]  ")
            } else {
                unfocusedStyle("    $affirmative    ")
            }
        val noText =
            if (!selectedYes) {
                focusedStyle("  [ $negative ]  ")
            } else {
                unfocusedStyle("    $negative    ")
            }
        return verticalLayout {
            cell(Text("$margin$prompt"))
            cell(Text(""))
            cell(Text("$margin$yesText    $noText"))
        }
    }

    /** Translate a key event to a dialog action, or `null` for unrecognised keys. */
    internal fun translateKey(event: KeyboardEvent): DialogAction? =
        when (event) {
            Keys.LEFT, KeyboardEvent("h") -> DialogAction.ToggleLeft
            Keys.RIGHT, KeyboardEvent("l") -> DialogAction.ToggleRight
            Keys.TAB -> DialogAction.Toggle
            Keys.ENTER -> DialogAction.Submit
            KeyboardEvent("y"), KeyboardEvent("Y") -> DialogAction.SelectYes
            KeyboardEvent("n"), KeyboardEvent("N") -> DialogAction.SelectNo
            KeyboardEvent("q"), Keys.ESCAPE -> DialogAction.Cancel
            else -> null
        }
}
