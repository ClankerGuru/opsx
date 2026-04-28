/**
 * Insertion and removal of opsx-managed content blocks inside host instruction files
 * (e.g. `CLAUDE.md`, `.github/copilot-instructions.md`).
 *
 * Blocks are delimited by HTML comment markers so they can be replaced idempotently
 * without disturbing user-authored content in the same file.
 */
package zone.clanker.opsx.cli.init.config

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

/**
 * Manages `<!-- >>> opsx >>> -->` / `<!-- <<< opsx <<< -->` delimited blocks
 * inside markdown instruction files. Provides idempotent upsert and clean removal.
 */
internal object MarkerBlock {
    private const val OPEN = "<!-- >>> opsx >>> -->"
    private const val CLOSE = "<!-- <<< opsx <<< -->"
    private const val LEGACY_OPEN = "<!-- OPSX:AUTO -->"
    private const val LEGACY_CLOSE = "<!-- /OPSX:AUTO -->"

    /**
     * Insert or replace the opsx marker block in [file] with [content].
     * Creates the file and parent directories if they do not exist.
     */
    fun upsert(
        file: Path,
        content: String,
    ) {
        val existing =
            if (SystemFileSystem.exists(file)) {
                SystemFileSystem.source(file).buffered().use { it.readString() }
            } else {
                ""
            }
        val stripped = strip(existing)
        val block = "$OPEN\n$content\n$CLOSE"
        val joined = if (stripped.isBlank()) block else "$stripped\n\n$block"
        file.parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.sink(file).buffered().use { it.writeString("$joined\n") }
    }

    /** Remove both current and legacy marker blocks from [text] and return the remaining content, trimmed. */
    fun strip(text: String): String {
        var result = stripBlock(text, OPEN, CLOSE)
        result = stripBlock(result, LEGACY_OPEN, LEGACY_CLOSE)
        return result.trim()
    }

    private fun stripBlock(
        text: String,
        open: String,
        close: String,
    ): String {
        val openIdx = text.indexOf(open)
        if (openIdx < 0) return text
        val closeIdx = text.indexOf(close, openIdx)
        if (closeIdx < 0) return text
        return text.substring(0, openIdx) + text.substring(closeIdx + close.length)
    }

    /** Strip the marker block from [file]; delete the file entirely if nothing else remains. */
    fun remove(file: Path) {
        if (!SystemFileSystem.exists(file)) return
        val stripped = strip(SystemFileSystem.source(file).buffered().use { it.readString() })
        if (stripped.isBlank()) {
            SystemFileSystem.delete(file, mustExist = false)
        } else {
            SystemFileSystem.sink(file).buffered().use { it.writeString("$stripped\n") }
        }
    }
}
