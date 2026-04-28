package zone.clanker.opsx.cli.nuke

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import zone.clanker.opsx.cli.init.config.ResourceTree
import zone.clanker.opsx.cli.init.host.Host

/** Tests for per-host nuke operations, cleanEmptyParents, and helper functions. */
class NukeRunnerHostTest :
    FunSpec({

        fun createTempRoot(): Path {
            val tempFile = java.io.File.createTempFile("nuke-host-test-", "")
            val tempDir = Path(tempFile.absolutePath + "-dir")
            SystemFileSystem.createDirectories(tempDir)
            return tempDir
        }

        fun cleanupDir(path: Path) {
            java.io.File(path.toString()).deleteRecursively()
        }

        fun writeFile(
            root: Path,
            relativePath: String,
            content: String,
        ) {
            val filePath = Path(root, relativePath)
            filePath.parent?.let { SystemFileSystem.createDirectories(it) }
            SystemFileSystem.sink(filePath).buffered().use { it.writeString(content) }
        }

        fun readFile(
            root: Path,
            relativePath: String,
        ): String = SystemFileSystem.source(Path(root, relativePath)).buffered().use { it.readString() }

        fun exists(
            root: Path,
            relativePath: String,
        ): Boolean = SystemFileSystem.exists(Path(root, relativePath))

        val resources = ResourceTree()
        val ourSkillNames = resources.listNames("skills").filter { !it.startsWith("_") }
        val ourAgentNames = resources.listNames("agents")

        // --- nukeHostSkills ---

        test("nukeHostSkills deletes skill directories for Claude") {
            val root = createTempRoot()
            try {
                val skillName = ourSkillNames.first()
                writeFile(root, ".claude/skills/$skillName/SKILL.md", "# Skill")
                val entries = NukeRunner.nukeHostSkills(root, Host.CLAUDE, listOf(skillName))
                entries shouldHaveSize 1
                entries.first().success.shouldBeTrue()
                entries.first().description shouldBe "1 skills removed"
                exists(root, ".claude/skills/$skillName").shouldBeFalse()
            } finally {
                cleanupDir(root)
            }
        }

        test("nukeHostSkills deletes skill directories for Copilot") {
            val root = createTempRoot()
            try {
                val skillName = ourSkillNames.first()
                writeFile(root, ".github/skills/$skillName/SKILL.md", "# Skill")
                val entries = NukeRunner.nukeHostSkills(root, Host.COPILOT, listOf(skillName))
                entries shouldHaveSize 1
                entries.first().success.shouldBeTrue()
                exists(root, ".github/skills/$skillName").shouldBeFalse()
            } finally {
                cleanupDir(root)
            }
        }

        test("nukeHostSkills deletes skill directories for Codex") {
            val root = createTempRoot()
            try {
                val skillName = ourSkillNames.first()
                writeFile(root, ".codex-plugin/skills/$skillName/SKILL.md", "# Skill")
                val entries = NukeRunner.nukeHostSkills(root, Host.CODEX, listOf(skillName))
                entries shouldHaveSize 1
                entries.first().success.shouldBeTrue()
                exists(root, ".codex-plugin/skills/$skillName").shouldBeFalse()
            } finally {
                cleanupDir(root)
            }
        }

        test("nukeHostSkills deletes flat .md files for OpenCode") {
            val root = createTempRoot()
            try {
                val skillName = ourSkillNames.first()
                writeFile(root, ".opencode/command/$skillName.md", "# Skill")
                val entries = NukeRunner.nukeHostSkills(root, Host.OPENCODE, listOf(skillName))
                entries shouldHaveSize 1
                entries.first().success.shouldBeTrue()
                exists(root, ".opencode/command/$skillName.md").shouldBeFalse()
            } finally {
                cleanupDir(root)
            }
        }

        test("nukeHostSkills returns empty when skills directory does not exist") {
            val root = createTempRoot()
            try {
                NukeRunner.nukeHostSkills(root, Host.CLAUDE, ourSkillNames) shouldHaveSize 0
            } finally {
                cleanupDir(root)
            }
        }

        // --- nukeHostAgents ---

        test("nukeHostAgents deletes plain .md agents for Claude") {
            val root = createTempRoot()
            try {
                val agentName = ourAgentNames.first()
                writeFile(root, ".claude/agents/$agentName", "agent content")
                val entries = NukeRunner.nukeHostAgents(root, Host.CLAUDE, listOf(agentName))
                entries shouldHaveSize 1
                entries.first().success.shouldBeTrue()
                entries.first().description shouldBe "1 agents removed"
                exists(root, ".claude/agents/$agentName").shouldBeFalse()
            } finally {
                cleanupDir(root)
            }
        }

        test("nukeHostAgents deletes .agent.md files for Copilot") {
            val root = createTempRoot()
            try {
                val agentName = ourAgentNames.first()
                val base = agentName.removeSuffix(".md")
                writeFile(root, ".github/agents/$base.agent.md", "agent")
                val entries = NukeRunner.nukeHostAgents(root, Host.COPILOT, listOf(agentName))
                entries shouldHaveSize 1
                entries.first().success.shouldBeTrue()
                exists(root, ".github/agents/$base.agent.md").shouldBeFalse()
            } finally {
                cleanupDir(root)
            }
        }

        test("nukeHostAgents deletes plain .md agents for Codex") {
            val root = createTempRoot()
            try {
                val agentName = ourAgentNames.first()
                writeFile(root, ".codex-plugin/agents/$agentName", "agent")
                val entries = NukeRunner.nukeHostAgents(root, Host.CODEX, listOf(agentName))
                entries shouldHaveSize 1
                entries.first().success.shouldBeTrue()
                exists(root, ".codex-plugin/agents/$agentName").shouldBeFalse()
            } finally {
                cleanupDir(root)
            }
        }

        test("nukeHostAgents deletes plain .md agents for OpenCode") {
            val root = createTempRoot()
            try {
                val agentName = ourAgentNames.first()
                writeFile(root, ".opencode/agent/$agentName", "agent")
                val entries = NukeRunner.nukeHostAgents(root, Host.OPENCODE, listOf(agentName))
                entries shouldHaveSize 1
                entries.first().success.shouldBeTrue()
                exists(root, ".opencode/agent/$agentName").shouldBeFalse()
            } finally {
                cleanupDir(root)
            }
        }

        test("nukeHostAgents returns empty when agent directory does not exist") {
            val root = createTempRoot()
            try {
                NukeRunner.nukeHostAgents(root, Host.CLAUDE, ourAgentNames) shouldHaveSize 0
            } finally {
                cleanupDir(root)
            }
        }

        test("nukeHostAgents preserves user-created agents") {
            val root = createTempRoot()
            try {
                val opsxAgent = ourAgentNames.first()
                writeFile(root, ".claude/agents/$opsxAgent", "opsx agent")
                writeFile(root, ".claude/agents/my-custom-agent.md", "user agent")
                NukeRunner.nukeHostAgents(root, Host.CLAUDE, ourAgentNames)
                exists(root, ".claude/agents/$opsxAgent").shouldBeFalse()
                exists(root, ".claude/agents/my-custom-agent.md").shouldBeTrue()
                readFile(root, ".claude/agents/my-custom-agent.md") shouldBe "user agent"
            } finally {
                cleanupDir(root)
            }
        }

        // --- nukeHostPluginJson ---

        test("nukeHostPluginJson deletes codex plugin.json") {
            val root = createTempRoot()
            try {
                writeFile(root, ".codex-plugin/plugin.json", "{}")
                val entries = NukeRunner.nukeHostPluginJson(root, Host.CODEX)
                entries shouldHaveSize 1
                entries.first().success.shouldBeTrue()
                entries.first().description shouldBe "removed"
                exists(root, ".codex-plugin/plugin.json").shouldBeFalse()
            } finally {
                cleanupDir(root)
            }
        }

        test("nukeHostPluginJson returns empty for non-Codex hosts") {
            val root = createTempRoot()
            try {
                NukeRunner.nukeHostPluginJson(root, Host.CLAUDE) shouldHaveSize 0
                NukeRunner.nukeHostPluginJson(root, Host.COPILOT) shouldHaveSize 0
                NukeRunner.nukeHostPluginJson(root, Host.OPENCODE) shouldHaveSize 0
            } finally {
                cleanupDir(root)
            }
        }

        test("nukeHostPluginJson returns empty when file does not exist") {
            val root = createTempRoot()
            try {
                NukeRunner.nukeHostPluginJson(root, Host.CODEX) shouldHaveSize 0
            } finally {
                cleanupDir(root)
            }
        }

        // --- cleanEmptyParents ---

        test("cleanEmptyParents removes nested empty directories up to root") {
            val root = createTempRoot()
            try {
                val nested = Path(root, "a/b/c")
                SystemFileSystem.createDirectories(nested)
                cleanEmptyParents(nested, root)
                exists(root, "a/b/c").shouldBeFalse()
                exists(root, "a/b").shouldBeFalse()
                exists(root, "a").shouldBeFalse()
                SystemFileSystem.exists(root).shouldBeTrue()
            } finally {
                cleanupDir(root)
            }
        }

        test("cleanEmptyParents stops at non-empty parent") {
            val root = createTempRoot()
            try {
                val nested = Path(root, "a/b/c")
                SystemFileSystem.createDirectories(nested)
                writeFile(root, "a/keep.txt", "keep")
                cleanEmptyParents(nested, root)
                exists(root, "a/b/c").shouldBeFalse()
                exists(root, "a/b").shouldBeFalse()
                exists(root, "a").shouldBeTrue()
                exists(root, "a/keep.txt").shouldBeTrue()
            } finally {
                cleanupDir(root)
            }
        }

        test("cleanEmptyParents handles already-deleted directory") {
            val root = createTempRoot()
            try {
                cleanEmptyParents(Path(root, "a/b/c"), root)
                SystemFileSystem.exists(root).shouldBeTrue()
            } finally {
                cleanupDir(root)
            }
        }

        // --- stripMarkers ---

        test("stripMarkers returns not-found entries for missing files") {
            val root = createTempRoot()
            try {
                val entries = NukeRunner.stripMarkers(root)
                entries shouldHaveSize 3
                entries.forEach { it.description shouldBe "not found" }
            } finally {
                cleanupDir(root)
            }
        }

        // --- deleteConfig ---

        test("deleteConfig returns not-found when config is missing") {
            val root = createTempRoot()
            try {
                NukeRunner.deleteConfig(root).first().description shouldBe "not found"
            } finally {
                cleanupDir(root)
            }
        }

        test("deleteConfig removes existing config file") {
            val root = createTempRoot()
            try {
                writeFile(root, ".opsx/config.json", "{}")
                val entry = NukeRunner.deleteConfig(root).first()
                entry.success.shouldBeTrue()
                entry.description shouldBe "removed"
                exists(root, ".opsx/config.json").shouldBeFalse()
            } finally {
                cleanupDir(root)
            }
        }

        // --- deleteCache ---

        test("deleteCache returns not-found when cache is missing") {
            val root = createTempRoot()
            try {
                NukeRunner.deleteCache(root).first().description shouldBe "not found"
            } finally {
                cleanupDir(root)
            }
        }

        test("deleteCache removes existing cache directory") {
            val root = createTempRoot()
            try {
                writeFile(root, ".opsx/cache/opsx-plugin/plugin.json", "{}")
                val entry = NukeRunner.deleteCache(root).first()
                entry.success.shouldBeTrue()
                entry.description shouldBe "removed"
                exists(root, ".opsx/cache").shouldBeFalse()
            } finally {
                cleanupDir(root)
            }
        }
    })
