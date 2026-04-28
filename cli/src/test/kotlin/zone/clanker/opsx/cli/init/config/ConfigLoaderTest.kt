package zone.clanker.opsx.cli.init.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory

class ConfigLoaderTest :
    FunSpec({

        lateinit var rootPath: Path

        beforeEach {
            rootPath = Path(SystemTemporaryDirectory, "opsx-cfg-${System.nanoTime()}")
            SystemFileSystem.createDirectories(rootPath)
        }

        afterEach { java.io.File(rootPath.toString()).deleteRecursively() }

        test("load returns null when file missing") {
            ConfigLoader.load(rootPath).shouldBeNull()
        }

        test("save then load round-trips") {
            val config = OpsxConfig(hosts = listOf("claude", "opencode"))
            ConfigLoader.save(rootPath, config)
            val loaded = ConfigLoader.load(rootPath)
            loaded?.hosts shouldBe listOf("claude", "opencode")
            loaded?.version shouldBe 1
        }

        test("save is idempotent") {
            val config = OpsxConfig(hosts = listOf("claude"))
            ConfigLoader.save(rootPath, config)
            ConfigLoader.save(rootPath, config)
            ConfigLoader.load(rootPath)?.hosts shouldBe listOf("claude")
        }

        test("defaults are populated") {
            ConfigLoader.save(rootPath, OpsxConfig())
            val loaded = ConfigLoader.load(rootPath)!!
            loaded.changesDir shouldBe "opsx/changes"
            loaded.archiveDir shouldBe "opsx/archive"
            loaded.specsDir shouldBe "opsx/specs"
        }
    })
