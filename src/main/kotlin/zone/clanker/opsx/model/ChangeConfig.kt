package zone.clanker.opsx.model

import java.io.File

data class ChangeConfig(
    val name: String,
    val status: String = "active",
    val depends: List<String> = emptyList(),
) {
    companion object {
        fun parse(file: File): ChangeConfig? {
            if (!file.exists()) return null
            val lines = file.readLines()
            val scalars = parseScalars(lines)
            val name = scalars["name"] ?: return null
            return ChangeConfig(
                name = name,
                status = scalars["status"] ?: "active",
                depends = parseDepends(lines, scalars["depends"]),
            )
        }

        internal fun parseScalars(lines: List<String>): Map<String, String> {
            val result = mutableMapOf<String, String>()
            lines
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .filter { it.contains(':') }
                .forEach { trimmed ->
                    val colonIndex = trimmed.indexOf(':')
                    val key = trimmed.substring(0, colonIndex).trim()
                    val value = trimmed.substring(colonIndex + 1).trim()
                    if (value.isNotEmpty()) result[key] = value
                }
            return result
        }

        internal fun parseDepends(
            lines: List<String>,
            inlineValue: String?,
        ): List<String> {
            if (inlineValue != null) return parseBracketList(inlineValue)
            return parseDependsBlock(lines)
        }

        private fun parseDependsBlock(lines: List<String>): List<String> {
            val cleaned = lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            val dependsIndex = cleaned.indexOfFirst { it.startsWith("depends:") && it.substringAfter(":").isBlank() }
            if (dependsIndex < 0) return emptyList()
            return cleaned
                .drop(dependsIndex + 1)
                .takeWhile { it.startsWith("- ") }
                .map { it.removePrefix("- ").trim() }
        }

        internal fun parseBracketList(value: String): List<String> {
            val content = value.removePrefix("[").removeSuffix("]").trim()
            if (content.isEmpty()) return emptyList()
            return content.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
