package zone.clanker.opsx.workflow

import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.Change
import java.io.File

class PromptBuilder(
    private val rootDir: File,
) {
    fun srcxContext(maxChars: Int = MAX_CONTEXT_CHARS): String {
        val file = File(rootDir, ".srcx/context.md")
        if (!file.exists()) return ""
        val content = file.readText()
        if (content.length <= maxChars) return content
        return content.take(maxChars) + "\n\n[... truncated at $maxChars chars]"
    }

    fun projectDescription(extension: Opsx.SettingsExtension): String {
        val file = File(rootDir, "${extension.outputDir}/${extension.projectFile}")
        return if (file.exists()) file.readText() else ""
    }

    fun specContent(
        extension: Opsx.SettingsExtension,
        specName: String,
    ): String {
        require(!specName.contains("..") && !specName.contains("/") && !specName.contains("\\")) {
            "Invalid spec name '$specName': must not contain '..', '/', or '\\'"
        }
        val file = File(rootDir, "${extension.outputDir}/${extension.specsDir}/$specName.md")
        return if (file.exists()) file.readText() else ""
    }

    fun changeContext(change: Change): String =
        buildString {
            if (change.proposalFile.exists()) {
                appendLine("## Proposal")
                appendLine()
                appendLine(change.proposalFile.readText())
                appendLine()
            }
            if (change.designFile.exists()) {
                appendLine("## Design")
                appendLine()
                appendLine(change.designFile.readText())
                appendLine()
            }
            if (change.tasksFile.exists()) {
                appendLine("## Tasks")
                appendLine()
                appendLine(change.tasksFile.readText())
                appendLine()
            }
            if (change.feedbackFile.exists()) {
                appendLine("## Feedback")
                appendLine()
                appendLine(change.feedbackFile.readText())
                appendLine()
            }
        }

    fun build(vararg sections: Pair<String, String>): String =
        buildString {
            for ((heading, content) in sections) {
                if (content.isBlank()) continue
                appendLine("# $heading")
                appendLine()
                appendLine(content.trim())
                appendLine()
                appendLine("---")
                appendLine()
            }
        }.trimEnd()

    companion object {
        const val MAX_CONTEXT_CHARS = 50_000
    }
}
