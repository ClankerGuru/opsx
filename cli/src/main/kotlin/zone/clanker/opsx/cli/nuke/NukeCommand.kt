/**
 * The `opsx nuke` subcommand -- removes opsx-generated project files and
 * optionally the global opsx installation. Delegates to [NukeRunner] and
 * prints each [NukeEntry] as it completes.
 */
package zone.clanker.opsx.cli.nuke

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import kotlinx.io.files.Path

/**
 * `opsx nuke` -- full project and global cleanup.
 * Use `--keep-rc` to preserve the PATH block in shell rc files.
 */
class NukeCommand : CliktCommand(name = "nuke") {
    override fun help(context: Context): String = "Uninstall opsx — remove project and global files"

    private val keepRc: Boolean by option("--keep-rc", help = "Leave PATH block in shell rc").flag()

    override fun run() {
        val projectEntries = NukeRunner.nukeProject(Path("."))
        projectEntries.forEach(::printEntry)

        echo("")

        val globalEntries = NukeRunner.nukeGlobal(keepRc = keepRc)
        globalEntries.forEach(::printEntry)

        val total = projectEntries.size + globalEntries.size
        val succeeded = (projectEntries + globalEntries).count { it.success }
        echo("")
        echo(green("done") + gray(" — $succeeded/$total cleaned"))
    }

    private fun printEntry(entry: NukeEntry) {
        if (entry.success) {
            echo("${green("\u2713")} ${entry.path}  ${gray(entry.description)}")
        } else {
            echo("${red("\u2717")} ${entry.path}  ${red(entry.error ?: entry.description)}")
        }
    }
}
