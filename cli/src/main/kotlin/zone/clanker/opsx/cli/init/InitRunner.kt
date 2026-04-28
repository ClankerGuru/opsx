/**
 * Orchestration logic for `opsx init` -- writes `.opsx/config.json`, loads the embedded
 * manifest, emits skills/agents for each selected host, and cleans up dead legacy paths.
 * All state is returned as data classes; no terminal output happens here.
 */
package zone.clanker.opsx.cli.init

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import zone.clanker.opsx.cli.init.config.ConfigLoader
import zone.clanker.opsx.cli.init.config.Manifest
import zone.clanker.opsx.cli.init.config.OpsxConfig
import zone.clanker.opsx.cli.init.config.ResourceTree
import zone.clanker.opsx.cli.init.emitter.ClaudeEmitter
import zone.clanker.opsx.cli.init.emitter.CodexEmitter
import zone.clanker.opsx.cli.init.emitter.CopilotEmitter
import zone.clanker.opsx.cli.init.emitter.EmitPlan
import zone.clanker.opsx.cli.init.emitter.HostEmitter
import zone.clanker.opsx.cli.init.emitter.OpenCodeEmitter
import zone.clanker.opsx.cli.init.host.Host
import zone.clanker.opsx.cli.init.host.HostDetector

/**
 * Result of emitting files for one host.
 *
 * @property host the target host that was processed
 * @property plan the files written/deleted if emission succeeded, or null on failure
 * @property error error message if emission failed, or null on success
 * @property canRunExternal whether the host's external CLI binary is available on PATH
 */
data class HostResult(
    val host: Host,
    val plan: EmitPlan?,
    val error: String?,
    val canRunExternal: Boolean,
)

/**
 * Aggregate result of a full `opsx init` run across all selected hosts.
 *
 * @property hosts per-host emission results
 * @property cleanedPaths legacy directory paths that were removed during cleanup
 * @property anyFailed true if at least one host emitter reported an error
 */
data class InitResult(
    val hosts: List<HostResult>,
    val cleanedPaths: List<String>,
    val anyFailed: Boolean,
)

/**
 * Runs the init flow -- config persistence, manifest loading, host emission, and dead-path cleanup.
 * Pure data -- returns [InitResult], no rendering.
 */
object InitRunner {
    /**
     * Execute the full init sequence for the given [selectedHosts].
     *
     * @param rootDir workspace root (the directory containing `.opsx/`)
     * @param selectedHosts hosts to emit; defaults to all hosts when `--host` is omitted
     * @return aggregate [InitResult] with per-host outcomes and cleanup info
     */
    fun run(
        rootDir: Path,
        selectedHosts: List<Host>,
    ): InitResult {
        // Write config
        val existing = ConfigLoader.load(rootDir)
        val config = (existing ?: OpsxConfig()).copy(hosts = selectedHosts.map { it.id })
        ConfigLoader.save(rootDir, config)

        // Load content
        val resources = ResourceTree()
        val manifest = Manifest.parse(resources.readText("manifest.json"))

        // Emit each host
        val results =
            selectedHosts.map { host ->
                val plan = runCatching { emitterFor(host).emit(rootDir, manifest, resources) }
                HostResult(
                    host = host,
                    plan = plan.getOrNull(),
                    error = plan.exceptionOrNull()?.message,
                    canRunExternal = HostDetector.canRunExternal(host),
                )
            }

        // Dead path cleanup
        val cleaned = cleanupDeadPaths(rootDir)

        return InitResult(
            hosts = results,
            cleanedPaths = cleaned,
            anyFailed = results.any { it.error != null },
        )
    }

    private fun emitterFor(host: Host): HostEmitter =
        when (host) {
            Host.CLAUDE -> ClaudeEmitter
            Host.COPILOT -> CopilotEmitter
            Host.CODEX -> CodexEmitter
            Host.OPENCODE -> OpenCodeEmitter
        }

    private fun cleanupDeadPaths(rootDir: Path): List<String> {
        val cleaned = mutableListOf<String>()
        listOf(".opencode/skills", ".codex/skills")
            .map { it to Path(rootDir, it) }
            .filter { (_, f) -> SystemFileSystem.exists(f) }
            .forEach { (name, f) ->
                // kotlinx-io has no deleteRecursively — bridge to java.io.File
                java.io.File(f.toString()).deleteRecursively()
                cleaned += name
            }
        return cleaned
    }
}
