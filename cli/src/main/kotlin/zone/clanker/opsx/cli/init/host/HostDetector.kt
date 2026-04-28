/**
 * Probes the system PATH for host CLI binaries so the init command can
 * report whether external registration commands will succeed.
 */
package zone.clanker.opsx.cli.init.host

/**
 * Detects whether a [Host]'s external CLI binary is available on the system PATH.
 */
object HostDetector {
    /**
     * Return `true` if the [host] has no external CLI requirement, or if its CLI
     * binary is found via `which`. Returns `false` on probe failure.
     */
    fun canRunExternal(host: Host): Boolean {
        val cli = host.externalCli ?: return true
        return runCatching {
            ProcessBuilder("which", cli).start().waitFor() == 0
        }.getOrDefault(false)
    }
}
