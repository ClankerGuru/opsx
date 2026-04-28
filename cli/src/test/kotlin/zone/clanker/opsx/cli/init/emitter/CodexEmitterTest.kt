package zone.clanker.opsx.cli.init.emitter

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readString
import kotlinx.io.writeString
import zone.clanker.opsx.cli.init.config.Manifest
import zone.clanker.opsx.cli.init.config.ResourceTree

class CodexEmitterTest :
    FunSpec({

        lateinit var root: Path
        lateinit var home: Path
        lateinit var rootPath: Path
        lateinit var previousHome: String

        beforeEach {
            root = Path(SystemTemporaryDirectory, "emit-cdx-${System.nanoTime()}")
            home = Path(SystemTemporaryDirectory, "emit-cdx-home-${System.nanoTime()}")
            SystemFileSystem.createDirectories(root)
            SystemFileSystem.createDirectories(home)
            previousHome = System.getProperty("user.home")
            System.setProperty("user.home", home.toString())
            rootPath = root
        }

        afterEach {
            System.setProperty("user.home", previousHome)
            java.io.File(root.toString()).deleteRecursively()
            java.io.File(home.toString()).deleteRecursively()
        }

        test("emit writes plugin.json and skills") {
            val resources = ResourceTree()
            val manifest = Manifest.parse(resources.readText("manifest.json"))
            val plan = CodexEmitter.emit(rootPath, manifest, resources)
            plan.writtenFiles.shouldNotBeEmpty()
            SystemFileSystem.exists(Path(root, ".codex-plugin/plugin.json")) shouldBe true
            SystemFileSystem
                .source(Path(home, ".agents/plugins/marketplace.json"))
                .buffered()
                .use { it.readString() } shouldContain """"opsx""""
        }

        test("unmake removes plugin and prunes marketplace entry") {
            SystemFileSystem.createDirectories(Path(home, ".agents/plugins"))
            SystemFileSystem.sink(Path(home, ".agents/plugins/marketplace.json")).buffered().use {
                it.writeString(
                    """
                    |{
                    |    "other": {"path": "/tmp/other"},
                    |    "opsx": {"path": "/tmp/old"}
                    |}
                    """.trimMargin(),
                )
            }
            val resources = ResourceTree()
            val manifest = Manifest.parse(resources.readText("manifest.json"))
            CodexEmitter.emit(rootPath, manifest, resources)

            val plan = CodexEmitter.unmake(rootPath, manifest)

            plan.deletedFiles.shouldNotBeEmpty()
            SystemFileSystem.exists(Path(root, ".codex-plugin")) shouldBe false
            val marketplace =
                SystemFileSystem
                    .source(Path(home, ".agents/plugins/marketplace.json"))
                    .buffered()
                    .use { it.readString() }
            marketplace shouldContain """"other""""
            marketplace shouldNotContain """"opsx""""
        }
    })
