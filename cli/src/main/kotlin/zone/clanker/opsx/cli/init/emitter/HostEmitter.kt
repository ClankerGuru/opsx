/**
 * Contract and result types for host-specific file emitters.
 *
 * Each [HostEmitter] implementation writes skills, agents, and instruction-file markers
 * for a single [Host]. The [EmitPlan] and [UnmakePlan] data classes capture what was
 * written or deleted so the caller can report results without side effects.
 */
package zone.clanker.opsx.cli.init.emitter

import kotlinx.io.files.Path
import zone.clanker.opsx.cli.init.config.Manifest
import zone.clanker.opsx.cli.init.config.ResourceTree
import zone.clanker.opsx.cli.init.host.Host

/**
 * Sealed interface for per-host file emitters.
 *
 * Implementations write skills/agents into the host-specific directory layout
 * and optionally inject a marker block into the host's instruction file.
 */
sealed interface HostEmitter {
    /** The host this emitter targets. */
    val host: Host

    /**
     * Emit all skills and agents for this host into the workspace at [rootDir].
     *
     * @param rootDir workspace root
     * @param manifest parsed `manifest.json` containing agent/skill metadata
     * @param resources classpath resource tree for reading embedded content
     * @return plan describing every file written, deleted, and external command to run
     */
    fun emit(
        rootDir: Path,
        manifest: Manifest,
        resources: ResourceTree,
    ): EmitPlan

    /**
     * Remove all previously emitted files for this host from the workspace at [rootDir].
     *
     * @param rootDir workspace root
     * @param manifest parsed `manifest.json` (used to know what was emitted)
     * @return plan describing every file deleted and external command to run
     */
    fun unmake(
        rootDir: Path,
        manifest: Manifest,
    ): UnmakePlan
}

/**
 * Describes the outcome of a host emission -- what was written, deleted, and what
 * external commands should be run afterward.
 *
 * @property writtenFiles paths that were created or overwritten
 * @property deletedFiles paths that were removed
 * @property externalCommands shell commands that need to run after file emission
 * @property warnings non-fatal issues encountered during emission
 */
data class EmitPlan(
    val writtenFiles: List<Path>,
    val deletedFiles: List<Path>,
    val externalCommands: List<ExternalCommand>,
    val warnings: List<String>,
)

/**
 * Describes the outcome of removing a host's emitted files.
 *
 * @property deletedFiles paths that were removed
 * @property externalCommands shell commands that need to run after removal
 * @property warnings non-fatal issues encountered during unmake
 */
data class UnmakePlan(
    val deletedFiles: List<Path>,
    val externalCommands: List<ExternalCommand>,
    val warnings: List<String>,
)

/**
 * A shell command that should be executed after emission or unmake.
 *
 * @property description human-readable explanation of what the command does
 * @property argv the command and its arguments
 * @property optional if true, failure of this command is non-fatal
 */
data class ExternalCommand(
    val description: String,
    val argv: List<String>,
    val optional: Boolean,
)
