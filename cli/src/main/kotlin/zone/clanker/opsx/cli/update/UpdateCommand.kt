/**
 * The `opsx update` subcommand -- delegates to [UpdateRunner] for the actual
 * update check, download, and installation logic. This command is a thin CLI
 * wrapper that translates [UpdateCheck] and [UpdateResult] into terminal output.
 */
package zone.clanker.opsx.cli.update

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextStyles
import zone.clanker.opsx.cli.ExitCode

/**
 * `opsx update` -- self-update the opsx binary from GitHub releases.
 *
 * Delegates to [UpdateRunner.checkLatest] for version comparison and
 * [UpdateRunner.downloadAndInstall] for the actual update. Use `--check`
 * for a dry-run version comparison, `--force` to update even when current.
 */
class UpdateCommand : CliktCommand(name = "update") {
    override fun help(context: Context): String = "Self-update the opsx binary"

    /** Force update even if the installed version matches the latest release. */
    private val force: Boolean by option("--force", help = "Force update even if current").flag()

    /** Print version delta and exit without downloading. */
    private val check: Boolean by option("--check", help = "Print version delta and exit").flag()

    override fun run() {
        val current = zone.clanker.opsx.cli.BuildConfig.VERSION

        echo("")
        echo(TextStyles.bold("opsx update"))
        echo("")

        when (val checkResult = UpdateRunner.checkLatest(current)) {
            is UpdateCheck.UpToDate -> {
                if (force) {
                    performForceUpdate(current, checkResult)
                } else {
                    echo("  ${green("\u2713")} up to date ${gray("(${checkResult.version})")}")
                    echo("")
                }
            }
            is UpdateCheck.Failed -> {
                echo(red("  ${checkResult.error}"))
                throw ProgramResult(ExitCode.SELF_UPDATE_NETWORK_FAILURE)
            }
            is UpdateCheck.Available -> {
                if (check) return printVersionDelta(current, checkResult.latest)
                echo("  updating ${gray(current)} ${gray("\u2192")} ${green(checkResult.latest)}")
                performInstall(checkResult.release)
            }
        }
    }

    /**
     * Force-update when already up to date: re-fetch the release and install anyway.
     * Falls back to reporting "up to date" if the re-check itself fails.
     */
    private fun performForceUpdate(
        current: String,
        upToDate: UpdateCheck.UpToDate,
    ) {
        val recheck = UpdateRunner.checkLatest(current)
        val release =
            when (recheck) {
                is UpdateCheck.Available -> recheck.release
                is UpdateCheck.UpToDate -> {
                    echo("  ${green("\u2713")} up to date ${gray("(${upToDate.version})")}")
                    echo("")
                    return
                }
                is UpdateCheck.Failed -> {
                    echo(red("  ${recheck.error}"))
                    throw ProgramResult(ExitCode.SELF_UPDATE_NETWORK_FAILURE)
                }
            }
        echo("  force-updating ${gray(current)}")
        performInstall(release)
    }

    /** Download and install a release, printing progress and handling failure. */
    private fun performInstall(release: ReleaseInfo) {
        when (val installResult = UpdateRunner.downloadAndInstall(release, onStep = ::echoStep)) {
            is UpdateResult.Success -> {
                echo("  ${green("\u2713")} updated to ${installResult.version}")
                echo("")
            }
            is UpdateResult.Failed -> {
                echo(red("  ${installResult.error}"))
                throw ProgramResult(ExitCode.FAILURE)
            }
        }
    }

    /** Echo a human-readable line for each [UpdateStep]. */
    private fun echoStep(step: UpdateStep) {
        when (step) {
            is UpdateStep.Checking -> echo("  ${gray("checking ${step.repo}...")}")
            is UpdateStep.Downloading -> echo("  ${gray("downloading ${step.assetName}...")}")
            is UpdateStep.VerifyingChecksum -> echo("  ${gray("verifying checksum...")}")
            is UpdateStep.Extracting -> echo("  ${gray("extracting...")}")
            is UpdateStep.Done -> echo("  ${green("\u2713")} checksum verified")
            is UpdateStep.Error -> echo(red("  ${step.message}"))
        }
    }

    /** Print the version comparison without performing an update. */
    private fun printVersionDelta(
        current: String,
        latest: String,
    ) {
        if (current == latest) {
            echo("  ${green("\u2713")} up to date ${gray("($current)")}")
        } else {
            echo("  ${gray("current")}  $current")
            echo("  ${green("latest")}   $latest")
        }
        echo("")
    }
}
