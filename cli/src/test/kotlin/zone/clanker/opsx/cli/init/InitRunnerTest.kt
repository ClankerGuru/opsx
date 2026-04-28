package zone.clanker.opsx.cli.init

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readString
import zone.clanker.opsx.cli.init.host.Host

class InitRunnerTest :
    FunSpec({

        lateinit var root: Path
        lateinit var home: Path
        lateinit var previousHome: String

        beforeEach {
            root = Path(SystemTemporaryDirectory, "opsx-init-test-${System.nanoTime()}")
            home = Path(SystemTemporaryDirectory, "opsx-home-test-${System.nanoTime()}")
            SystemFileSystem.createDirectories(root)
            SystemFileSystem.createDirectories(home)
            previousHome = System.getProperty("user.home")
            System.setProperty("user.home", home.toString())
        }

        afterEach {
            System.setProperty("user.home", previousHome)
            java.io.File(root.toString()).deleteRecursively()
            java.io.File(home.toString()).deleteRecursively()
        }

        test("run writes config and emits selected hosts") {
            val result = InitRunner.run(root, listOf(Host.CLAUDE, Host.OPENCODE))

            result.anyFailed shouldBe false
            result.hosts.map { it.host } shouldContainExactlyInAnyOrder listOf(Host.CLAUDE, Host.OPENCODE)
            SystemFileSystem
                .source(Path(root, ".opsx/config.json"))
                .buffered()
                .use { it.readString() } shouldContain """"hosts": ["""
            SystemFileSystem.exists(Path(root, ".claude/skills")) shouldBe true
            SystemFileSystem.exists(Path(root, ".opencode/command")) shouldBe true
        }

        test("run cleans legacy paths and emits all hosts without touching real home") {
            listOf(".opencode/skills", ".codex/skills")
                .forEach { SystemFileSystem.createDirectories(Path(root, it)) }

            val result = InitRunner.run(root, Host.entries.toList())

            result.anyFailed shouldBe false
            result.hosts shouldHaveSize Host.entries.size
            result.cleanedPaths shouldContainExactlyInAnyOrder
                listOf(".opencode/skills", ".codex/skills")
            SystemFileSystem.exists(Path(root, ".codex-plugin/plugin.json")) shouldBe true
            SystemFileSystem
                .source(Path(home, ".agents/plugins/marketplace.json"))
                .buffered()
                .use { it.readString() } shouldContain """"opsx""""
            SystemFileSystem.exists(Path(root, ".opencode/skills")) shouldBe false
            SystemFileSystem.exists(Path(root, ".codex/skills")) shouldBe false
        }
    })
