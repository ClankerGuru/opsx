/**
 * The `opsx status` subcommand -- renders either a change ledger (all changes with progress bars)
 * or a per-change task tree (activity events grouped by agent) to the terminal.
 */
package zone.clanker.opsx.cli.status

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextStyles.bold
import kotlinx.io.files.Path
import zone.clanker.opsx.cli.opsxRoot

/**
 * `opsx status` -- displays the change ledger or a per-change activity tree.
 *
 * Without `--change`, prints every tracked change with a status badge and progress bar.
 * With `--change <name>`, prints the activity-log tree for that single change,
 * grouped by agent with start/done/failed glyphs.
 */
class StatusCommand : CliktCommand(name = "status") {
    override fun help(context: Context): String = "Show change ledger or task tree"

    private val change: String? by option("--change", help = "Show tree for a specific change")
    private val archive: Boolean by option("--archive", help = "Include archived changes").flag()
    private val only: String? by option("--only", help = "Filter by status")

    @Suppress("unused")
    private val noTee: Boolean by option("--no-tee", help = "Skip tee to last-status.txt").flag()

    @Suppress("unused")
    private val follow: Boolean by option("--follow", help = "Tail activity log").flag()

    override fun run() {
        val rootDir = opsxRoot()
        val changeName = change

        if (changeName != null) {
            printTree(rootDir, changeName)
        } else {
            printLedger(rootDir)
        }
    }

    private fun printLedger(rootDir: Path) {
        val entries = ChangeScanner.scan(rootDir, includeArchive = archive, onlyStatus = only)

        if (entries.isEmpty()) {
            echo("No changes found. Use /opsx-propose to start one.")
            return
        }

        echo("")
        echo(bold("opsx status"))
        echo("")
        var lastStatus = ""
        for (entry in entries) {
            if (entry.status != lastStatus) {
                if (lastStatus.isNotEmpty()) echo("")
                lastStatus = entry.status
            }
            echo("  ${Style.badge(entry.status)}  ${Style.progressBar(entry.done, entry.total)}  ${entry.name}")
        }
        echo("")
    }

    private fun printTree(
        rootDir: Path,
        changeName: String,
    ) {
        val events = ActivityLog.read(rootDir, changeName)

        echo("")
        echo("${bold("opsx status")} ${gray("→")} ${cyan(changeName)}")
        echo("")

        if (events.isEmpty()) {
            echo(gray("  no activity recorded yet"))
            echo("")
            return
        }

        events.groupBy { it.agent }.forEach { (agent, agentEvents) ->
            val color = Style.agentColors[agent]
            echo("  ${color?.let { it("@$agent") } ?: "@$agent"}")
            agentEvents.forEach { event ->
                val glyph =
                    when (event.state) {
                        State.START -> gray("→")
                        State.DONE -> green("✓")
                        State.FAILED ->
                            com.github.ajalt.mordant.rendering.TextColors
                                .red("✗")
                    }
                val taskLabel = event.task?.let { gray("[$it] ") } ?: ""
                echo("    $glyph $taskLabel${event.desc ?: ""}")
            }
            echo("")
        }
    }
}
