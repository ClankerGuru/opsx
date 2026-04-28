/**
 * Filesystem scanner that discovers opsx change directories under `opsx/changes/`
 * (and optionally `opsx/archive/`), reading each `.opsx.yaml` for status and
 * `tasks.md` for progress counts.
 */
package zone.clanker.opsx.cli.status

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString

/**
 * Scans `opsx/changes/` and `opsx/archive/` for change directories.
 * Pure data -- returns a sorted [ChangeEntry] list, no rendering or IO side effects.
 */
object ChangeScanner {
    /**
     * Walk `opsx/changes/` (and `opsx/archive/` when [includeArchive] is true),
     * filter to [onlyStatus] if provided, and return entries sorted by lifecycle order.
     *
     * @param rootDir workspace root containing the `opsx/` directory
     * @param includeArchive whether to also scan `opsx/archive/`
     * @param onlyStatus if non-null, keep only entries whose status matches this value
     * @return sorted list of [ChangeEntry]; empty if no changes exist
     */
    fun scan(
        rootDir: Path,
        includeArchive: Boolean = false,
        onlyStatus: String? = null,
    ): List<ChangeEntry> {
        val entries = mutableListOf<ChangeEntry>()
        entries += scanDir(Path(rootDir, "opsx/changes"))
        if (includeArchive) entries += scanDir(Path(rootDir, "opsx/archive"))

        val filtered = if (onlyStatus != null) entries.filter { it.status == onlyStatus } else entries
        return filtered.sortedBy { e ->
            Style.lifecycleOrder.indexOf(e.status).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
    }

    private fun scanDir(dir: Path): List<ChangeEntry> {
        if (!SystemFileSystem.exists(dir)) return emptyList()
        val changes =
            SystemFileSystem
                .list(dir)
                .filter {
                    SystemFileSystem.metadataOrNull(it)?.isDirectory == true &&
                        SystemFileSystem.exists(Path(it, ".opsx.yaml"))
                }
        return changes.map { d ->
            val status = readStatus(Path(d, ".opsx.yaml"))
            val (done, total) = countTasks(d)
            ChangeEntry(d.name, status, done, total)
        }
    }

    private fun readStatus(yamlFile: Path): String {
        val text = SystemFileSystem.source(yamlFile).buffered().use { it.readString() }
        val match = Regex("""status:\s*(\S+)""").find(text)
        return match?.groupValues?.get(1) ?: "draft"
    }

    private fun countTasks(changeDir: Path): Pair<Int, Int> {
        val tasksFile = Path(changeDir, "tasks.md")
        if (!SystemFileSystem.exists(tasksFile)) return 0 to 0
        val text = SystemFileSystem.source(tasksFile).buffered().use { it.readString() }
        val total = Regex("""-\s*\[[ x]]""").findAll(text).count()
        val done = Regex("""-\s*\[x]""").findAll(text).count()
        return done to total
    }
}
