/**
 * ANSI styling helpers shared by the status command and TUI views -- badges, progress bars,
 * agent color map, and lifecycle ordering.
 */
package zone.clanker.opsx.cli.status

import com.github.ajalt.mordant.rendering.TextColors.blue
import com.github.ajalt.mordant.rendering.TextColors.brightBlue
import com.github.ajalt.mordant.rendering.TextColors.brightYellow
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.rendering.TextStyles.bold

/**
 * Terminal styling constants and helpers for the opsx status display.
 *
 * Provides agent-to-color mappings, lifecycle-order sorting, fixed-width status badges,
 * and a block-character progress bar renderer. Honors `NO_COLOR` for badge output.
 */
object Style {
    private val noColor = System.getenv("NO_COLOR")?.isNotEmpty() == true

    /** Mordant color assigned to each agent name for consistent terminal output. */
    val agentColors =
        mapOf(
            "lead" to cyan,
            "scout" to green,
            "forge" to blue,
            "developer" to yellow,
            "qa" to red,
            "architect" to brightYellow,
            "devOps" to brightBlue,
        )

    /** Canonical ordering of change lifecycle states, used to sort the ledger display. */
    val lifecycleOrder =
        listOf(
            "active",
            "in-progress",
            "draft",
            "completed",
            "verified",
            "archived",
            "deleted",
        )

    private val badges =
        mapOf(
            "active" to "[   active   ]",
            "in-progress" to "[in-progress ]",
            "draft" to "[   draft    ]",
            "completed" to "[ completed  ]",
            "verified" to "[  verified  ]",
            "archived" to "[  archived  ]",
            "deleted" to "[  deleted   ]",
        )

    private val badgeStyles =
        mapOf(
            "active" to cyan,
            "in-progress" to yellow,
            "draft" to blue,
            "completed" to green,
            "verified" to (bold + green),
            "archived" to gray,
            "deleted" to red,
        )

    /**
     * Return a fixed-width colored badge for a lifecycle [status] (e.g. `[   active   ]`).
     * Falls back to the draft badge if the status is unknown. Respects `NO_COLOR`.
     */
    fun badge(status: String): String {
        val text = badges[status] ?: badges["draft"]!!
        val style = badgeStyles[status] ?: blue
        return if (noColor) text else style(text)
    }

    /**
     * Render a block-character progress bar with percentage and fraction.
     *
     * @param done number of completed items
     * @param total total items; if zero, renders an empty bar at 0%
     * @param width character width of the bar (default 10)
     * @return formatted string like `████░░░░░░  40%  (4/10)`
     */
    fun progressBar(
        done: Int,
        total: Int,
        width: Int = 10,
    ): String {
        if (total <= 0) return "${"░".repeat(width)}   0%  (0/0)"
        val pct = (done * PERCENT) / total
        val filled = (done * width) / total
        val bar = "${"█".repeat(filled)}${"░".repeat(width - filled)}"
        return "$bar  ${pct.toString().padStart(PAD_WIDTH)}%  ($done/$total)"
    }

    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3600L
    private const val PAD_WIDTH = 3
    private const val PERCENT = 100

    /** Format a duration in [seconds] as a human-friendly string (e.g. `2m 30s`, `1h 5m`). */
    fun elapsed(seconds: Long): String =
        when {
            seconds < SECONDS_PER_MINUTE -> "${seconds}s"
            seconds < SECONDS_PER_HOUR -> "${seconds / SECONDS_PER_MINUTE}m ${seconds % SECONDS_PER_MINUTE}s"
            else -> "${seconds / SECONDS_PER_HOUR}h ${(seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE}m"
        }
}
