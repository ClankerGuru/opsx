package zone.clanker.opsx.workflow

import zone.clanker.opsx.model.Change
import zone.clanker.opsx.model.ChangeConfig
import zone.clanker.opsx.model.OpsxConfig
import java.io.File

class ChangeReader(
    private val rootDir: File,
    private val config: OpsxConfig,
) {
    fun readAll(): List<Change> {
        val changesDir = File(rootDir, "${config.outputDir}/${config.changesDir}")
        if (!changesDir.exists()) return emptyList()
        return changesDir
            .listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { readChange(it) }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun readChange(dir: File): Change? {
        val configFile = File(dir, ".opsx.yaml")
        val changeConfig = ChangeConfig.parse(configFile)
        return Change(
            name = changeConfig?.name ?: dir.name,
            status = changeConfig?.status ?: "active",
            depends = changeConfig?.depends ?: emptyList(),
            dir = dir,
        )
    }
}
