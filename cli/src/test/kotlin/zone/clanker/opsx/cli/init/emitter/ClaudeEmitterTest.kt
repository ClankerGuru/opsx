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

class ClaudeEmitterTest :
    FunSpec({

        lateinit var rootPath: Path

        beforeEach {
            rootPath = Path(SystemTemporaryDirectory, "emit-claude-${System.nanoTime()}")
            SystemFileSystem.createDirectories(rootPath)
        }

        afterEach { java.io.File(rootPath.toString()).deleteRecursively() }

        test("emit writes skills and agents") {
            val resources = ResourceTree()
            val manifest = Manifest.parse(resources.readText("manifest.json"))

            val plan = ClaudeEmitter.emit(rootPath, manifest, resources)
            plan.writtenFiles.shouldNotBeEmpty()
            plan.externalCommands shouldBe emptyList()
        }

        test("emit creates instruction file with marker") {
            val resources = ResourceTree()
            val manifest = Manifest.parse(resources.readText("manifest.json"))

            ClaudeEmitter.emit(rootPath, manifest, resources)
            val instructionFile = Path(rootPath, "CLAUDE.md")
            SystemFileSystem.exists(instructionFile) shouldBe true
            SystemFileSystem
                .source(instructionFile)
                .buffered()
                .use { it.readString() } shouldContain "<!-- >>> opsx >>> -->"
            SystemFileSystem
                .source(instructionFile)
                .buffered()
                .use { it.readString() } shouldContain "# Agents"
        }

        test("unmake deletes emitted dirs") {
            val resources = ResourceTree()
            val manifest = Manifest.parse(resources.readText("manifest.json"))

            ClaudeEmitter.emit(rootPath, manifest, resources)
            val plan = ClaudeEmitter.unmake(rootPath, manifest)
            plan.deletedFiles.shouldNotBeEmpty()
        }
    })
