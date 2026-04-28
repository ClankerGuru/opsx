/**
 * Root Clikt command that wires global options (`--root`)
 * and registers every subcommand (init, status, list, log, install, update, nuke, completion).
 */
package zone.clanker.opsx.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import kotlinx.io.files.Path
import zone.clanker.opsx.cli.completion.CompletionCommand
import zone.clanker.opsx.cli.init.InitCommand
import zone.clanker.opsx.cli.install.InstallCommand
import zone.clanker.opsx.cli.list.ListCommand
import zone.clanker.opsx.cli.log.LogCommand
import zone.clanker.opsx.cli.nuke.NukeCommand
import zone.clanker.opsx.cli.status.StatusCommand
import zone.clanker.opsx.cli.update.UpdateCommand

/**
 * Root CLI entry point for opsx.
 *
 * This is a [NoOpCliktCommand] -- it does nothing itself but delegates to subcommands
 * and exposes the selected workspace root to subcommands.
 */
class OpsxCommand : NoOpCliktCommand(name = "opsx") {
    override fun help(context: Context): String = "Workspace lifecycle for AI agents"

    init {
        val version = BuildConfig.VERSION
        versionOption(version = version, names = setOf("--version", "-V"))
        subcommands(
            InitCommand(),
            UpdateCommand(),
            InstallCommand(),
            NukeCommand(),
            StatusCommand(),
            ListCommand(),
            LogCommand(),
            CompletionCommand(),
        )
    }

    val root: String? by option("--root", help = "Override project root")
}

/** Resolve the workspace root selected on the root `opsx` command. */
internal fun CliktCommand.opsxRoot(): Path {
    val rootCommand = currentContext.findRoot().command as? OpsxCommand
    return Path(rootCommand?.root ?: ".")
}
