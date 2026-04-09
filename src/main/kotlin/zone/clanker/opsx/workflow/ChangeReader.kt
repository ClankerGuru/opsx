package zone.clanker.opsx.workflow

import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.Change
import zone.clanker.opsx.model.ChangeConfig
import java.io.File

class ChangeReader(
    private val rootDir: File,
    private val extension: Opsx.SettingsExtension,
) {
    fun readAll(): List<Change> {
        val changesDir = File(rootDir, "${extension.outputDir}/${extension.changesDir}")
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
        val config = ChangeConfig.parse(configFile)
        return Change(
            name = config?.name ?: dir.name,
            status = config?.status ?: "active",
            depends = config?.depends ?: emptyList(),
            dir = dir,
        )
    }
}
