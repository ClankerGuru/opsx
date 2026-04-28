/**
 * The `opsx install` subcommand -- delegates to [InstallRunner] to copy the
 * distribution to `~/.opsx`, configure PATH, and install completions.
 * Prints each [InstallEntry] with status indicators as it completes.
 */
package zone.clanker.opsx.cli.install

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.yellow

/**
 * `opsx install` -- copies the distribution to `~/.opsx` (or `$OPSX_HOME`),
 * adds a PATH block to the shell rc, and drops a zsh completion stub.
 */
class InstallCommand : CliktCommand(name = "install") {
    override fun help(context: Context): String = "Install opsx globally to ~/.opsx/bin"

    override fun run() {
        val sourceDir = InstallRunner.resolveSourceDir()
        if (sourceDir == null) {
            echo(yellow("could not determine install source -- run from a distribution"))
            return
        }

        val entries = InstallRunner.install(sourceDir)
        entries.forEach(::printEntry)

        val total = entries.size
        val succeeded = entries.count { it.success }
        echo("")
        if (succeeded == total) {
            echo(green("done") + gray(" -- restart your shell or: source ~/.zshrc"))
        } else {
            echo(red("completed with errors") + gray(" -- $succeeded/$total installed"))
        }
    }

    private fun printEntry(entry: InstallEntry) {
        if (entry.success) {
            echo("${green("\u2713")} ${entry.path}  ${gray(entry.description)}")
        } else {
            echo("${red("\u2717")} ${entry.path}  ${red(entry.error ?: entry.description)}")
        }
    }
}
