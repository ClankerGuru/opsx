/**
 * Paged getting-started guide shown to new users on first launch.
 *
 * Walks through the opsx workflow (propose, apply, verify, archive) across five pages,
 * rendered as markdown in the TUI with a numbered step indicator and arrow-key navigation.
 * Uses [AppShell.render] for full-screen rendering.
 */
package zone.clanker.opsx.tui.onboarding

import com.github.ajalt.mordant.input.InputReceiver.Status
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.TextColors.brightCyan
import com.github.ajalt.mordant.rendering.TextColors.brightGreen
import com.github.ajalt.mordant.rendering.TextColors.brightWhite
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.TextStyles.dim
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.table.verticalLayout
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text
import zone.clanker.opsx.tui.render.AppShell
import zone.clanker.opsx.tui.render.Keys
import zone.clanker.opsx.tui.render.Styles

/**
 * Five-page onboarding guide for new opsx users.
 *
 * Each page explains one phase of the opsx workflow with code examples
 * rendered via Mordant's Markdown support. Navigation is arrow keys or h/l,
 * Enter advances, q/Escape exits early.
 */
object OnboardingView {
    internal data class Page(
        val title: String,
        val body: String,
    )

    internal val pages =
        listOf(
            Page(
                "Welcome to opsx",
                """
                |opsx manages workspace lifecycle for AI agents across
                |**Claude**, **Copilot**, **Codex**, and **OpenCode**.
                |
                |It tracks every change through a structured flow:
                |
                |  **propose** → **apply** → **verify** → **archive**
                |
                |Your workspace has no changes yet.
                |The next pages walk you through the flow.
                """.trimMargin(),
            ),
            Page(
                "Propose a change",
                """
                |Tell your AI agent what you want to build:
                |
                |```
                |/opsx-propose "Add retry logic to the HTTP client"
                |```
                |
                |The agent creates `opsx/changes/<name>/` with three files:
                |
                | File | Purpose |
                |---|---|
                | `proposal.md` | What and why |
                | `design.md` | How — architecture, files, tests |
                | `tasks.md` | Atomic checklist with dependencies |
                |
                |Each task has a unique ID, description, and `depends:` edges.
                |The agent won't start until you approve the plan.
                """.trimMargin(),
            ),
            Page(
                "Apply the change",
                """
                |The agent executes the task list:
                |
                |```
                |/opsx-apply add-retry-logic
                |```
                |
                |Watch progress live:
                |
                |```
                |opsx status --change add-retry-logic
                |```
                |
                |Activity is logged as the agent works:
                |
                |```
                |→ @developer [a1b2c3] implementing retry
                |✓ @developer [a1b2c3] retry added
                |→ @qa        [d4e5f6] writing tests
                |✓ @qa        [d4e5f6] tests pass
                |```
                """.trimMargin(),
            ),
            Page(
                "Verify and ship",
                """
                |Once all tasks are done:
                |
                |```
                |/opsx-verify add-retry-logic
                |```
                |
                |The agent runs tests, checks coverage, confirms the
                |design was followed. The ledger updates:
                |
                |```
                |[  verified  ]  ██████████  100%  (8/8)  add-retry-logic
                |```
                |
                |Create the PR and archive the change. Done.
                |The full history stays in `opsx/archive/` for reference.
                """.trimMargin(),
            ),
            Page(
                "Quick reference",
                """
                | Command | What it does |
                |---|---|
                | `opsx init` | Set up hosts for this workspace |
                | `opsx status` | Ledger of all changes |
                | `opsx status --change <name>` | Task tree for one change |
                | `opsx list` | One-per-line (scriptable) |
                | `opsx log` | Append an activity event |
                | `opsx update` | Self-update the binary |
                | `opsx install` | Install globally to ~/.opsx |
                | `opsx nuke` | Uninstall everything |
                |
                |Skills are slash commands your AI agent knows:
                |`/opsx-propose`, `/opsx-apply`, `/opsx-verify`, `/opsx-archive`
                """.trimMargin(),
            ),
        )

    /** Semantic action from a key event in the onboarding guide. */
    internal sealed interface GuideAction {
        data object Next : GuideAction

        data object Prev : GuideAction

        data object Advance : GuideAction

        data object Quit : GuideAction
    }

    /** Map a [KeyboardEvent] to a [GuideAction], or null for unrecognised keys. */
    internal fun translateGuideKey(event: KeyboardEvent): GuideAction? =
        when (event) {
            Keys.RIGHT, KeyboardEvent("l"), Keys.SPACE -> GuideAction.Next
            Keys.LEFT, KeyboardEvent("h") -> GuideAction.Prev
            Keys.ENTER -> GuideAction.Advance
            KeyboardEvent("q"), KeyboardEvent("Q"), Keys.ESCAPE -> GuideAction.Quit
            else -> null
        }

    /**
     * Compute the new page index after applying a [GuideAction].
     * Returns the new page index, or null if the action should finish the guide.
     */
    internal fun applyGuideAction(
        action: GuideAction,
        current: Int,
        total: Int,
    ): Int? =
        when (action) {
            GuideAction.Next -> if (current < total - 1) current + 1 else current
            GuideAction.Prev -> if (current > 0) current - 1 else current
            GuideAction.Advance -> if (current < total - 1) current + 1 else null
            GuideAction.Quit -> null
        }

    /** Display the paged guide; blocks until the user finishes or quits. */
    fun show(terminal: Terminal) {
        var current = 0
        val total = pages.size

        fun renderGuide() {
            val barLeft =
                buildString {
                    append("  ")
                    if (current > 0) append("\u2190 h prev    ")
                    if (current < total - 1) append("\u2192 l next    ") else append("\u21b5  done    ")
                    append("q  quit")
                }
            AppShell.render(
                terminal = terminal,
                content = buildPageWidget(terminal, current, total),
                barLeft = barLeft,
                barRight = "${current + 1} / $total  ",
            )
        }
        renderGuide()
        AppShell.readKeyInput(
            terminal,
            handler = { event ->
                val action =
                    translateGuideKey(event)
                        ?: return@readKeyInput Status.Continue
                val next =
                    applyGuideAction(action, current, total)
                        ?: return@readKeyInput Status.Finished(Unit)
                current = next
                renderGuide()
                Status.Continue
            },
            onResize = { renderGuide() },
        )
    }

    internal fun buildPageWidget(
        terminal: Terminal,
        current: Int,
        total: Int,
    ): Widget {
        val margin = Styles.margin

        val stepper =
            (0 until total).joinToString("  ") { i ->
                if (i == current) {
                    bold(brightWhite(" ${i + 1} "))
                } else {
                    dim(gray(" ${i + 1} "))
                }
            }

        val page = pages[current]
        val rendered = terminal.render(Markdown(page.body))

        return verticalLayout {
            cell(
                Text(
                    "$margin${bold(brightGreen("opsx"))}  ${brightWhite("guide")}  $stepper",
                ),
            )
            cell(Text(""))
            cell(Text("$margin${bold(brightCyan(page.title))}"))
            rendered.lines().forEach { line -> cell(Text("$margin$line")) }
        }
    }
}
