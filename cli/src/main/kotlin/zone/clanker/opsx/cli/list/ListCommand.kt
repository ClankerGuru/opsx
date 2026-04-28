/**
 * The `opsx list` subcommand -- prints one line per change with badge, progress bar, and name.
 * Designed for scriptable output; use `opsx status` for the richer interactive view.
 */
package zone.clanker.opsx.cli.list

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.gray
import zone.clanker.opsx.cli.opsxRoot
import zone.clanker.opsx.cli.status.ChangeScanner
import zone.clanker.opsx.cli.status.Style

/**
 * `opsx list` -- one-per-line change summary.
 *
 * Each line shows the status badge, progress bar, and change name.
 * Supports `--archive` to include archived changes and `--only` to filter by status.
 */
class ListCommand : CliktCommand(name = "list") {
    override fun help(context: Context): String = "List changes one-per-line"

    private val archive: Boolean by option("--archive", help = "Include archived changes").flag()
    private val only: String? by option("--only", help = "Filter by status")

    override fun run() {
        val entries = ChangeScanner.scan(opsxRoot(), includeArchive = archive, onlyStatus = only)

        if (entries.isEmpty()) {
            echo(gray("no changes found"))
            return
        }

        for (entry in entries) {
            echo("${Style.badge(entry.status)}  ${Style.progressBar(entry.done, entry.total)}  ${entry.name}")
        }
    }
}
