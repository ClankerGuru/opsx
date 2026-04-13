package zone.clanker.opsx.model

import java.io.File

data class ChangeConfig(
    val name: String,
    val status: String = "active",
    val depends: List<String> = emptyList(),
    val verify: String = "",
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
                verify = scalars["verify"] ?: "",
            )
        }

        internal fun parseScalars(lines: List<String>): Map<String, String> {
            val result = mutableMapOf<String, String>()
            lines
                .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith(" ") && !it.startsWith("\t") }
                .filter { it.contains(':') }
                .forEach { line ->
                    val colonIndex = line.indexOf(':')
                    val key = line.substring(0, colonIndex).trim()
                    val value = line.substring(colonIndex + 1).trim()
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

        fun write(
            file: File,
            config: ChangeConfig,
        ) {
            val sb = StringBuilder()
            sb.appendLine("name: ${config.name}")
            sb.appendLine("status: ${config.status}")
            if (config.verify.isNotEmpty()) {
                sb.appendLine("verify: ${config.verify}")
            }
            if (config.depends.isNotEmpty()) {
                sb.appendLine("depends: [${config.depends.joinToString(", ")}]")
            }
            file.writeText(sb.toString())
        }

        fun updateStatus(
            file: File,
            newStatus: String,
        ) {
            if (!file.exists()) {
                file.writeText("status: $newStatus\n")
                return
            }
            val lines = file.readLines()
            val updated = mutableListOf<String>()
            var statusFound = false
            for (line in lines) {
                if (!statusFound && line.startsWith("status:")) {
                    updated.add("status: $newStatus")
                    statusFound = true
                } else {
                    updated.add(line)
                }
            }
            if (!statusFound) {
                updated.add("status: $newStatus")
            }
            file.writeText(updated.joinToString("\n") + "\n")
        }

        internal fun parseBracketList(value: String): List<String> {
            val content = value.removePrefix("[").removeSuffix("]").trim()
            if (content.isEmpty()) return emptyList()
            return content.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
