package zone.clanker.opsx.cli.init.emitter

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import zone.clanker.opsx.cli.init.config.Manifest
import zone.clanker.opsx.cli.init.config.ResourceTree

class CopilotEmitterTest :
    FunSpec({

        lateinit var rootPath: Path

        beforeEach {
            rootPath = Path(SystemTemporaryDirectory, "emit-cop-${System.nanoTime()}")
            SystemFileSystem.createDirectories(rootPath)
        }

        afterEach { java.io.File(rootPath.toString()).deleteRecursively() }

        test("emit writes skills and agents to github directory") {
            val resources = ResourceTree()
            val manifest = Manifest.parse(resources.readText("manifest.json"))
            val plan = CopilotEmitter.emit(rootPath, manifest, resources)
            plan.writtenFiles.shouldNotBeEmpty()
            plan.externalCommands shouldBe emptyList()
            SystemFileSystem.exists(Path(rootPath, ".github/skills")) shouldBe true
            SystemFileSystem.exists(Path(rootPath, ".github/agents")) shouldBe true
        }

        test("unmake strips marker and returns empty plan") {
            val resources = ResourceTree()
            val manifest = Manifest.parse(resources.readText("manifest.json"))
            CopilotEmitter.emit(rootPath, manifest, resources)

            val plan = CopilotEmitter.unmake(rootPath, manifest)

            plan.deletedFiles shouldBe emptyList()
            plan.externalCommands shouldBe emptyList()
        }

        test("ExternalCommand data class holds description argv and optional fields") {
            val cmd =
                ExternalCommand(
                    description = "register plugin",
                    argv = listOf("copilot", "plugin", "add"),
                    optional = false,
                )
            cmd.description shouldBe "register plugin"
            cmd.argv shouldBe listOf("copilot", "plugin", "add")
            cmd.optional shouldBe false
        }

        test("ExternalCommand supports structural equality") {
            val a = ExternalCommand("desc", listOf("a"), true)
            val b = ExternalCommand("desc", listOf("a"), true)
            a shouldBe b
        }
    })
