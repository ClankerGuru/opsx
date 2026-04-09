package zone.clanker.opsx.model

import java.io.File

data class Change(
    val name: String,
    val status: String,
    val depends: List<String>,
    val dir: File,
) {
    val proposalFile: File get() = File(dir, "proposal.md")
    val designFile: File get() = File(dir, "design.md")
    val tasksFile: File get() = File(dir, "tasks.md")
    val configFile: File get() = File(dir, ".opsx.yaml")
}
