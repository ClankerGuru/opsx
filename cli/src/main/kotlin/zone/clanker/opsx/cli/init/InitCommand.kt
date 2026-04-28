/**
 * The `opsx init` subcommand -- bootstraps an opsx workspace by writing config,
 * emitting skills/agents for each selected host, and cleaning up legacy paths.
 */
package zone.clanker.opsx.cli.init

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.yellow
import zone.clanker.opsx.cli.ExitCode
import zone.clanker.opsx.cli.init.host.Host
import zone.clanker.opsx.cli.opsxRoot

/**
 * `opsx init` -- initialises an opsx workspace for one or more AI coding hosts.
 *
 * Delegates the heavy lifting to [InitRunner], then prints a summary showing
 * how many skills/agents were emitted per host. Exits with [ExitCode.HOST_EMISSION_FAILED]
 * if any host emitter reported an error.
 */
class InitCommand : CliktCommand(name = "init") {
    override fun help(context: Context): String = "Initialise an opsx workspace"

    private val hostIds: List<String> by option("--host", help = "Host to emit (repeatable)")
        .multiple()

    @Suppress("unused")
    private val noInteractive: Boolean by option("--no-interactive", help = "Skip interactive prompts")
        .flag()

    override fun run() {
        val rootDir = opsxRoot()
        val selectedHosts =
            if (hostIds.isNotEmpty()) {
                hostIds.mapNotNull { Host.fromId(it) }
            } else {
                Host.entries.toList()
            }

        val result = InitRunner.run(rootDir, selectedHosts)

        echo("")
        val hostNames = result.hosts.joinToString(", ") { cyan(it.host.id) }
        echo("  ${gray("configured")} $hostNames")
        echo("  ${green("✓")} ${gray("config")}  .opsx/config.json")
        echo("")

        for (hr in result.hosts) {
            echo("  ${hr.host.id}")
            if (hr.error != null) {
                echo("    ${red("✗")} ${hr.error}")
            } else if (hr.plan != null) {
                val sc =
                    hr.plan.writtenFiles.count { path ->
                        val pathString = path.toString()
                        pathString.contains("skills") || pathString.contains("command")
                    }
                val ac =
                    hr.plan.writtenFiles.count { path ->
                        val pathString = path.toString()
                        pathString.contains("agents") || pathString.contains("agent")
                    }
                echo("    ${green("✓")} $sc skills  ${gray("→")} ${hr.host.skillsDir}")
                if (ac > 0) echo("    ${green("✓")} $ac agents  ${gray("→")} ${hr.host.agentDir ?: ""}")
            }
        }
        echo("")

        if (result.cleanedPaths.isNotEmpty()) {
            echo("  ${gray("cleaned up ${result.cleanedPaths.size} legacy paths")}")
        }

        echo("  ${if (result.anyFailed) yellow("done with errors") else green("done")}")

        if (result.anyFailed) throw ProgramResult(ExitCode.HOST_EMISSION_FAILED)
    }
}
