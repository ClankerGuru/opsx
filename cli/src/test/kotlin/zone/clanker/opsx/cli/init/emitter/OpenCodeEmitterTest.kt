package zone.clanker.opsx.cli.init.emitter

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readString
import zone.clanker.opsx.cli.init.config.Manifest
import zone.clanker.opsx.cli.init.config.ResourceTree

class OpenCodeEmitterTest :
    FunSpec({

        lateinit var rootPath: Path

        beforeEach {
            rootPath = Path(SystemTemporaryDirectory, "emit-oc-${System.nanoTime()}")
            SystemFileSystem.createDirectories(rootPath)
        }

        afterEach { java.io.File(rootPath.toString()).deleteRecursively() }

        test("emit writes to .opencode/command") {
            val resources = ResourceTree()
            val manifest = Manifest.parse(resources.readText("manifest.json"))
            val plan = OpenCodeEmitter.emit(rootPath, manifest, resources)
            plan.writtenFiles.shouldNotBeEmpty()
            plan.externalCommands shouldBe emptyList()
        }

        test("emit creates AGENTS.md with marker") {
            val resources = ResourceTree()
            val manifest = Manifest.parse(resources.readText("manifest.json"))
            OpenCodeEmitter.emit(rootPath, manifest, resources)
            SystemFileSystem.exists(Path(rootPath, "AGENTS.md")) shouldBe true
            SystemFileSystem
                .source(Path(rootPath, "AGENTS.md"))
                .buffered()
                .use { it.readString() } shouldContain "<!-- >>> opsx >>> -->"
        }

        test("unmake removes emitted commands agents and marker") {
            val resources = ResourceTree()
            val manifest = Manifest.parse(resources.readText("manifest.json"))
            OpenCodeEmitter.emit(rootPath, manifest, resources)

            val plan = OpenCodeEmitter.unmake(rootPath, manifest)

            plan.deletedFiles.shouldNotBeEmpty()
            SystemFileSystem.exists(Path(rootPath, ".opencode/command")) shouldBe false
            SystemFileSystem.exists(Path(rootPath, ".opencode/agent")) shouldBe false
            SystemFileSystem.exists(Path(rootPath, "AGENTS.md")) shouldBe false
        }
    })
