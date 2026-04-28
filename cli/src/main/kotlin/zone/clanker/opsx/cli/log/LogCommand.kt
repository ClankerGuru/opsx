/**
 * The `opsx log` subcommand -- appends a single [ActivityEvent] to a change's `activity.log`.
 * Used by agent hooks and Gradle tasks to record progress as work happens.
 */
package zone.clanker.opsx.cli.log

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import zone.clanker.opsx.cli.ExitCode
import zone.clanker.opsx.cli.opsxRoot
import zone.clanker.opsx.cli.status.ActivityEvent
import zone.clanker.opsx.cli.status.ActivityLog
import zone.clanker.opsx.cli.status.State
import java.time.Instant

/**
 * `opsx log` -- append one activity event to a change's `activity.log`.
 *
 * Requires `--agent` and `--state` (start|done|failed). The change is resolved
 * from `--change` or the `OPSX_ACTIVE_CHANGE` environment variable.
 */
class LogCommand : CliktCommand(name = "log") {
    override fun help(context: Context): String = "Append an activity event"

    private val change: String? by option("--change", help = "Change name (default: \$OPSX_ACTIVE_CHANGE)")
    private val agent: String by option("--agent", help = "Agent name").required()
    private val state: String by option("--state", help = "Event state")
        .choice("start", "done", "failed")
        .required()
    private val task: String? by option("--task", help = "Task ID")
    private val description: String? by argument("description").optional()

    override fun run() {
        val changeName = change ?: System.getenv("OPSX_ACTIVE_CHANGE")
        if (changeName.isNullOrBlank()) {
            echo("error: --change required (or set OPSX_ACTIVE_CHANGE)", err = true)
            throw ProgramResult(ExitCode.USAGE_ERROR)
        }

        val rootDir = opsxRoot()
        val changeDir = Path(rootDir, "opsx/changes/$changeName")
        if (!SystemFileSystem.exists(changeDir)) {
            echo("error: change not found: $changeName", err = true)
            throw ProgramResult(ExitCode.CHANGE_NOT_FOUND)
        }

        val eventState =
            when (state) {
                "start" -> State.START
                "done" -> State.DONE
                "failed" -> State.FAILED
                else -> State.START
            }

        val event =
            ActivityEvent(
                ts = Instant.now().toString(),
                agent = agent,
                state = eventState,
                task = task,
                desc = description,
            )

        ActivityLog.append(rootDir, changeName, event)

        val glyph =
            when (eventState) {
                State.START -> gray("→")
                State.DONE -> green("✓")
                State.FAILED -> red("✗")
            }
        val taskLabel = task?.let { gray("[$it] ") } ?: ""
        echo("$glyph ${gray("@$agent")} $taskLabel${description ?: ""}")
    }
}
