/**
 * Executes the nuke operation -- deletes only opsx-owned skills and agents
 * from the project (preserving user-created ones) and optionally the global install.
 *
 * Each deletion is reported as a [NukeEntry] so callers (CLI and TUI)
 * can render progress as items are processed.
 */
package zone.clanker.opsx.cli.nuke

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import zone.clanker.opsx.cli.init.config.MarkerBlock
import zone.clanker.opsx.cli.init.config.ResourceTree
import zone.clanker.opsx.cli.init.host.FilenameConvention
import zone.clanker.opsx.cli.init.host.Host

/**
 * Result of one nuke operation (file/directory deletion or marker removal).
 *
 * @property path display path of the item (relative for project, absolute for global)
 * @property description human-readable summary of what happened
 * @property success whether the operation completed without error
 * @property error error message if [success] is false
 */
data class NukeEntry(
    val path: String,
    val description: String,
    val success: Boolean,
    val error: String? = null,
)

/** Sentinel regex for the PATH block injected into shell rc files. */
private val RC_BLOCK_PATTERN =
    Regex("""# >>> opsx >>>.*?# <<< opsx <<<\n?""", RegexOption.DOT_MATCHES_ALL)

/**
 * Executes the nuke operation -- deletes only opsx-owned skills and agents
 * from all host directories, preserving user-created content.
 */
object NukeRunner {
    /** Nuke only opsx-owned files from the project at [rootDir]. */
    fun nukeProject(rootDir: Path): List<NukeEntry> = nukeProject(rootDir, ResourceTree())

    /** Testable overload that accepts an explicit [ResourceTree]. */
    internal fun nukeProject(
        rootDir: Path,
        resources: ResourceTree,
    ): List<NukeEntry> {
        val ourSkillNames = resources.listNames("skills").filter { !it.startsWith("_") }
        val ourAgentNames = resources.listNames("agents")

        return buildList {
            for (host in Host.entries) {
                addAll(nukeHostSkills(rootDir, host, ourSkillNames))
                addAll(nukeHostAgents(rootDir, host, ourAgentNames))
                addAll(nukeHostPluginJson(rootDir, host))
            }
            addAll(stripMarkers(rootDir))
            addAll(deleteConfig(rootDir))
            addAll(deleteCache(rootDir))
        }
    }

    /** Delete opsx-owned skill entries for a single [host]. */
    internal fun nukeHostSkills(
        rootDir: Path,
        host: Host,
        skillNames: List<String>,
    ): List<NukeEntry> {
        val skillsDir = Path(rootDir, host.skillsDir)
        if (!SystemFileSystem.exists(skillsDir)) return emptyList()

        val entries = mutableListOf<NukeEntry>()
        var deletedCount = 0

        for (name in skillNames) {
            val target =
                if (host == Host.OPENCODE) {
                    Path(skillsDir, "$name.md")
                } else {
                    Path(skillsDir, name)
                }
            if (!SystemFileSystem.exists(target)) continue

            val relativePath = "${host.skillsDir}/$name"
            val result =
                runCatching {
                    val javaFile = java.io.File(target.toString())
                    if (javaFile.isDirectory) {
                        require(javaFile.deleteRecursively()) { "failed to delete $relativePath" }
                    } else {
                        SystemFileSystem.delete(target, mustExist = false)
                    }
                }
            result.fold(
                onSuccess = { deletedCount++ },
                onFailure = { error ->
                    entries +=
                        NukeEntry(
                            path = relativePath,
                            description = "remove skill",
                            success = false,
                            error = error.message,
                        )
                },
            )
        }

        if (deletedCount > 0) {
            entries.add(
                0,
                NukeEntry(
                    path = host.skillsDir,
                    description = "$deletedCount skills removed",
                    success = true,
                ),
            )
        }

        cleanEmptyParents(skillsDir, rootDir)
        return entries
    }

    /** Delete opsx-owned agent entries for a single [host]. */
    internal fun nukeHostAgents(
        rootDir: Path,
        host: Host,
        agentNames: List<String>,
    ): List<NukeEntry> {
        val agentDir = host.agentDir ?: return emptyList()
        val agentDirPath = Path(rootDir, agentDir)
        if (!SystemFileSystem.exists(agentDirPath)) return emptyList()

        val entries = mutableListOf<NukeEntry>()
        var deletedCount = 0

        for (resourceName in agentNames) {
            val diskName =
                when (host.filenameConvention) {
                    FilenameConvention.AGENT_MD -> {
                        val base = resourceName.removeSuffix(".md")
                        "$base.agent.md"
                    }
                    FilenameConvention.PLAIN_MD -> resourceName
                }
            val target = Path(agentDirPath, diskName)
            if (!SystemFileSystem.exists(target)) continue

            val relativePath = "$agentDir/$diskName"
            val result = runCatching { SystemFileSystem.delete(target, mustExist = false) }
            result.fold(
                onSuccess = { deletedCount++ },
                onFailure = { error ->
                    entries +=
                        NukeEntry(
                            path = relativePath,
                            description = "remove agent",
                            success = false,
                            error = error.message,
                        )
                },
            )
        }

        if (deletedCount > 0) {
            entries.add(
                0,
                NukeEntry(
                    path = agentDir,
                    description = "$deletedCount agents removed",
                    success = true,
                ),
            )
        }

        cleanEmptyParents(agentDirPath, rootDir)
        return entries
    }

    /**
     * Delete the plugin.json for hosts that have one (Codex).
     * Copilot plugin.json lives under .opsx/cache/ which is wiped separately.
     */
    internal fun nukeHostPluginJson(
        rootDir: Path,
        host: Host,
    ): List<NukeEntry> {
        if (host != Host.CODEX) return emptyList()
        val pluginJson = Path(rootDir, ".codex-plugin/plugin.json")
        if (!SystemFileSystem.exists(pluginJson)) return emptyList()
        return listOf(deleteFileEntry(rootDir, ".codex-plugin/plugin.json"))
    }

    /** Strip opsx marker blocks from all instruction files. */
    internal fun stripMarkers(rootDir: Path): List<NukeEntry> =
        listOf(
            stripMarkerEntry(rootDir, "CLAUDE.md"),
            stripMarkerEntry(rootDir, ".github/copilot-instructions.md"),
            stripMarkerEntry(rootDir, "AGENTS.md"),
        )

    /** Delete .opsx/config.json. */
    internal fun deleteConfig(rootDir: Path): List<NukeEntry> = listOf(deleteFileEntry(rootDir, ".opsx/config.json"))

    /** Delete .opsx/cache/ (the staging directory is entirely ours). */
    internal fun deleteCache(rootDir: Path): List<NukeEntry> = listOf(deleteDirectoryEntry(rootDir, ".opsx/cache"))

    /** Nuke the global opsx install at ~/.opsx, PATH blocks, and completions. */
    fun nukeGlobal(keepRc: Boolean = false): List<NukeEntry> {
        val home = System.getProperty("user.home")
        val opsxHome = System.getenv("OPSX_HOME") ?: "$home/.opsx"
        return nukeGlobal(homeDir = home, opsxHome = opsxHome, keepRc = keepRc)
    }

    /**
     * Testable overload that accepts explicit paths instead of reading
     * system properties and environment variables.
     */
    internal fun nukeGlobal(
        homeDir: String,
        opsxHome: String,
        keepRc: Boolean = false,
    ): List<NukeEntry> =
        buildList {
            add(deleteAbsoluteDirectory(opsxHome, homeDir))
            if (!keepRc) {
                add(stripRcBlock(Path(homeDir, ".zshrc"), "~/.zshrc"))
                add(stripRcBlock(Path(homeDir, ".bashrc"), "~/.bashrc"))
                add(stripRcBlock(Path(homeDir, ".profile"), "~/.profile"))
            }
            add(
                deleteAbsoluteFile(
                    Path(homeDir, ".zsh/completions/_opsx"),
                    "~/.zsh/completions/_opsx",
                ),
            )
        }
}

/**
 * Walk up from [directory] toward [rootDir], deleting each directory that is empty.
 * Stops at [rootDir] (never deletes the root itself).
 */
internal fun cleanEmptyParents(
    directory: Path,
    rootDir: Path,
) {
    val rootString = rootDir.toString()
    generateSequence(directory) { it.parent }
        .takeWhile { it.toString() != rootString }
        .forEach { current ->
            if (!SystemFileSystem.exists(current)) return@forEach
            val javaDir = java.io.File(current.toString())
            val children = javaDir.listFiles()
            if (children == null || children.isNotEmpty()) return
            javaDir.delete()
        }
}

/** Delete a directory relative to [rootDir]. */
private fun deleteDirectoryEntry(
    rootDir: Path,
    relativePath: String,
): NukeEntry {
    val dirPath = Path(rootDir, relativePath)
    if (!SystemFileSystem.exists(dirPath)) {
        return NukeEntry(path = relativePath, description = "not found", success = true)
    }
    val javaFile = java.io.File(dirPath.toString())
    val deleted = javaFile.deleteRecursively()
    return NukeEntry(
        path = relativePath,
        description = "removed",
        success = deleted,
        error = if (deleted) null else "failed to delete $relativePath",
    )
}

/** Delete a single file relative to [rootDir]. */
private fun deleteFileEntry(
    rootDir: Path,
    relativePath: String,
): NukeEntry {
    val filePath = Path(rootDir, relativePath)
    if (!SystemFileSystem.exists(filePath)) {
        return NukeEntry(path = relativePath, description = "not found", success = true)
    }
    return runCatching { SystemFileSystem.delete(filePath, mustExist = false) }.fold(
        onSuccess = {
            NukeEntry(path = relativePath, description = "removed", success = true)
        },
        onFailure = {
            NukeEntry(
                path = relativePath,
                description = "removed",
                success = false,
                error = it.message,
            )
        },
    )
}

/** Strip the opsx marker block from a markdown file relative to [rootDir]. */
private fun stripMarkerEntry(
    rootDir: Path,
    relativePath: String,
): NukeEntry {
    val filePath = Path(rootDir, relativePath)
    if (!SystemFileSystem.exists(filePath)) {
        return NukeEntry(path = relativePath, description = "not found", success = true)
    }
    return runCatching { MarkerBlock.remove(filePath) }.fold(
        onSuccess = {
            NukeEntry(path = relativePath, description = "marker stripped", success = true)
        },
        onFailure = {
            NukeEntry(
                path = relativePath,
                description = "strip marker",
                success = false,
                error = it.message,
            )
        },
    )
}

/** Delete an absolute directory path (for global nuke). */
private fun deleteAbsoluteDirectory(
    absolutePath: String,
    homeDir: String = System.getProperty("user.home"),
): NukeEntry {
    val displayPath = absolutePath.replace(homeDir, "~")
    val dirPath = Path(absolutePath)
    if (!SystemFileSystem.exists(dirPath)) {
        return NukeEntry(path = displayPath, description = "not found", success = true)
    }
    val javaFile = java.io.File(absolutePath)
    val deleted = javaFile.deleteRecursively()
    return NukeEntry(
        path = displayPath,
        description = "removed",
        success = deleted,
        error = if (deleted) null else "failed to delete $displayPath",
    )
}

/** Delete a single file at an absolute path (for global nuke). */
private fun deleteAbsoluteFile(
    filePath: Path,
    displayPath: String,
): NukeEntry {
    if (!SystemFileSystem.exists(filePath)) {
        return NukeEntry(
            path = displayPath,
            description = "not found",
            success = true,
        )
    }
    return runCatching { SystemFileSystem.delete(filePath, mustExist = false) }.fold(
        onSuccess = {
            NukeEntry(path = displayPath, description = "removed", success = true)
        },
        onFailure = {
            NukeEntry(
                path = displayPath,
                description = "removed",
                success = false,
                error = it.message,
            )
        },
    )
}

/** Strip the `# >>> opsx >>> ... # <<< opsx <<<` PATH block from a shell rc file. */
private fun stripRcBlock(
    filePath: Path,
    displayPath: String,
): NukeEntry {
    if (!SystemFileSystem.exists(filePath)) {
        return NukeEntry(
            path = displayPath,
            description = "not found",
            success = true,
        )
    }
    return runCatching {
        val content = SystemFileSystem.source(filePath).buffered().use { it.readString() }
        if (!content.contains("# >>> opsx >>>")) {
            return NukeEntry(
                path = displayPath,
                description = "no PATH block",
                success = true,
            )
        }
        val updated = content.replace(RC_BLOCK_PATTERN, "")
        SystemFileSystem.sink(filePath).buffered().use { it.writeString(updated) }
    }.fold(
        onSuccess = {
            NukeEntry(
                path = displayPath,
                description = "PATH block stripped",
                success = true,
            )
        },
        onFailure = {
            NukeEntry(
                path = displayPath,
                description = "strip PATH block",
                success = false,
                error = it.message,
            )
        },
    )
}
