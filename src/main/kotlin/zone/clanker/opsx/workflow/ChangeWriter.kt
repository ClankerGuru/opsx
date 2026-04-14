package zone.clanker.opsx.workflow

import zone.clanker.opsx.model.ChangeConfig
import zone.clanker.opsx.model.ChangeStatus
import zone.clanker.opsx.model.OpsxConfig
import java.io.File

class ChangeWriter(
    private val rootDir: File,
    private val config: OpsxConfig,
) {
    fun createChangeDir(changeName: String): File {
        requireSafeName(changeName)
        val changesDir = File(rootDir, "${config.outputDir}/${config.changesDir}")
        val changeDir = File(changesDir, changeName)
        changeDir.mkdirs()
        return changeDir
    }

    companion object {
        /** Reject names that could escape the changes directory via path traversal. */
        internal fun requireSafeName(name: String) {
            require(!name.contains("..") && !name.contains("/") && !name.contains("\\")) {
                "Invalid change name '$name': must not contain '..', '/', or '\\'"
            }
        }
    }

    fun writeConfig(
        changeDir: File,
        changeName: String,
        status: ChangeStatus,
    ) {
        val changeConfig = ChangeConfig(name = changeName, status = status.value)
        ChangeConfig.write(File(changeDir, ".opsx.yaml"), changeConfig)
    }

    fun writeProposal(
        changeDir: File,
        content: String,
    ) {
        File(changeDir, "proposal.md").writeText(content)
    }

    fun writeDesignSkeleton(
        changeDir: File,
        changeName: String,
    ) {
        val file = File(changeDir, "design.md")
        if (!file.exists()) {
            file.writeText("# Design: $changeName\n\n<!-- Fill in the design details -->\n")
        }
    }

    fun writeTasksSkeleton(
        changeDir: File,
        changeName: String,
    ) {
        val file = File(changeDir, "tasks.md")
        if (!file.exists()) {
            file.writeText("# Tasks: $changeName\n\n- [ ] TODO\n")
        }
    }

    fun updateStatus(
        changeDir: File,
        status: ChangeStatus,
    ) {
        val configFile = File(changeDir, ".opsx.yaml")
        ChangeConfig.updateStatus(configFile, status.value)
    }

    fun appendFeedback(
        changeDir: File,
        content: String,
    ) {
        val file = File(changeDir, "feedback.md")
        if (file.exists()) {
            file.appendText("\n$content")
        } else {
            file.writeText(content)
        }
    }
}
