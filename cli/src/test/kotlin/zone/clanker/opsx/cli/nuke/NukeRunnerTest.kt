package zone.clanker.opsx.cli.nuke

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import zone.clanker.opsx.cli.init.config.ResourceTree

class NukeRunnerTest :
    FunSpec({

        fun createTempRoot(): Path {
            val tempFile = java.io.File.createTempFile("nuke-test-", "")
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
        ): String {
            val filePath = Path(root, relativePath)
            return SystemFileSystem.source(filePath).buffered().use { it.readString() }
        }

        fun exists(
            root: Path,
            relativePath: String,
        ): Boolean = SystemFileSystem.exists(Path(root, relativePath))

        val resources = ResourceTree()
        val ourSkillNames = resources.listNames("skills").filter { !it.startsWith("_") }
        val ourAgentNames = resources.listNames("agents")

        // --- nukeProject: preserves user-created skills ---

        test("nukeProject removes opsx skills and preserves user-created skills") {
            val root = createTempRoot()
            try {
                val firstSkill = ourSkillNames.first()
                val secondSkill = ourSkillNames.drop(1).first()
                writeFile(root, ".claude/skills/$firstSkill/SKILL.md", "# $firstSkill")
                writeFile(root, ".claude/skills/$secondSkill/SKILL.md", "# $secondSkill")
                writeFile(root, ".claude/skills/my-custom-thing/SKILL.md", "# My Custom Thing")

                NukeRunner.nukeProject(root, resources)

                exists(root, ".claude/skills/$firstSkill").shouldBeFalse()
                exists(root, ".claude/skills/$secondSkill").shouldBeFalse()
                exists(root, ".claude/skills/my-custom-thing/SKILL.md").shouldBeTrue()
                readFile(root, ".claude/skills/my-custom-thing/SKILL.md") shouldBe "# My Custom Thing"
                exists(root, ".claude/skills").shouldBeTrue()
            } finally {
                cleanupDir(root)
            }
        }

        test("nukeProject removes opsx agents and preserves user-created agents") {
            val root = createTempRoot()
            try {
                val firstAgent = ourAgentNames.first()
                writeFile(root, ".claude/agents/$firstAgent", "opsx agent")
                writeFile(root, ".claude/agents/my-agent.md", "user agent")

                NukeRunner.nukeProject(root, resources)

                exists(root, ".claude/agents/$firstAgent").shouldBeFalse()
                exists(root, ".claude/agents/my-agent.md").shouldBeTrue()
                readFile(root, ".claude/agents/my-agent.md") shouldBe "user agent"
            } finally {
                cleanupDir(root)
            }
        }

        // --- nukeProject: full project with all hosts ---

        test("nukeProject removes opsx files from all hosts and cleans markers") {
            val root = createTempRoot()
            try {
                val firstSkill = ourSkillNames.first()
                val firstAgent = ourAgentNames.first()
                val agentBase = firstAgent.removeSuffix(".md")

                writeFile(root, ".claude/skills/$firstSkill/SKILL.md", "# Skill")
                writeFile(root, ".claude/agents/$firstAgent", "Agent")
                writeFile(root, ".opsx/cache/opsx-plugin/skills/$firstSkill/SKILL.md", "# Skill")
                writeFile(root, ".opsx/cache/opsx-plugin/agents/$agentBase.agent.md", "Agent")
                writeFile(root, ".codex-plugin/skills/$firstSkill/SKILL.md", "# Skill")
                writeFile(root, ".codex-plugin/agents/$firstAgent", "Agent")
                writeFile(root, ".codex-plugin/plugin.json", "{}")
                writeFile(root, ".opencode/command/$firstSkill.md", "# Skill")
                writeFile(root, ".opencode/agent/$firstAgent", "Agent")
                writeFile(root, ".opsx/config.json", "{}")
                writeFile(root, ".opsx/cache/opsx-plugin/plugin.json", "{}")
                writeFile(
                    root,
                    "CLAUDE.md",
                    "# My Project\n\n<!-- >>> opsx >>> -->\nmanaged\n<!-- <<< opsx <<< -->\n",
                )
                writeFile(
                    root,
                    ".github/copilot-instructions.md",
                    "<!-- >>> opsx >>> -->\nmanaged\n<!-- <<< opsx <<< -->\n",
                )
                writeFile(
                    root,
                    "AGENTS.md",
                    "# Agents\n\n<!-- >>> opsx >>> -->\nmanaged\n<!-- <<< opsx <<< -->\n",
                )

                val entries = NukeRunner.nukeProject(root, resources)

                exists(root, ".claude/skills/$firstSkill").shouldBeFalse()
                exists(root, ".claude/agents/$firstAgent").shouldBeFalse()
                exists(root, ".codex-plugin/skills/$firstSkill").shouldBeFalse()
                exists(root, ".codex-plugin/agents/$firstAgent").shouldBeFalse()
                exists(root, ".codex-plugin/plugin.json").shouldBeFalse()
                exists(root, ".opencode/command/$firstSkill.md").shouldBeFalse()
                exists(root, ".opencode/agent/$firstAgent").shouldBeFalse()
                exists(root, ".opsx/cache").shouldBeFalse()
                exists(root, ".opsx/config.json").shouldBeFalse()

                exists(root, "CLAUDE.md").shouldBeTrue()
                val claudeMd = readFile(root, "CLAUDE.md")
                claudeMd shouldContain "# My Project"
                claudeMd.contains("opsx >>>").shouldBeFalse()

                exists(root, ".github/copilot-instructions.md").shouldBeFalse()

                exists(root, "AGENTS.md").shouldBeTrue()
                val agentsMd = readFile(root, "AGENTS.md")
                agentsMd shouldContain "# Agents"
                agentsMd.contains("opsx >>>").shouldBeFalse()

                val claudeSkillEntry = entries.first { it.path == ".claude/skills" }
                claudeSkillEntry.success.shouldBeTrue()
                claudeSkillEntry.description shouldContain "skills removed"

                val configEntry = entries.first { it.path == ".opsx/config.json" }
                configEntry.success.shouldBeTrue()
                configEntry.description shouldBe "removed"
            } finally {
                cleanupDir(root)
            }
        }

        // --- nukeProject: empty directory ---

        test("nukeProject on empty directory reports markers and config as not found") {
            val root = createTempRoot()
            try {
                val entries = NukeRunner.nukeProject(root, resources)

                val markerEntries = entries.filter { it.description == "not found" }
                markerEntries.size shouldBe 5

                entries.forEach { entry ->
                    entry.success.shouldBeTrue()
                }
            } finally {
                cleanupDir(root)
            }
        }

        // --- nukeProject: Claude host only ---

        test("nukeProject when only Claude host was installed") {
            val root = createTempRoot()
            try {
                val firstSkill = ourSkillNames.first()
                writeFile(root, ".claude/skills/$firstSkill/SKILL.md", "# Skill")
                writeFile(root, ".claude/agents/${ourAgentNames.first()}", "Agent")
                writeFile(
                    root,
                    "CLAUDE.md",
                    "# Project\n\n<!-- >>> opsx >>> -->\nblock\n<!-- <<< opsx <<< -->\n",
                )

                val entries = NukeRunner.nukeProject(root, resources)

                val claudeSkills = entries.firstOrNull { it.path == ".claude/skills" }
                claudeSkills.shouldNotBeNull()
                claudeSkills.success.shouldBeTrue()
                claudeSkills.description shouldContain "skills removed"

                val claudeAgents = entries.firstOrNull { it.path == ".claude/agents" }
                claudeAgents.shouldNotBeNull()
                claudeAgents.success.shouldBeTrue()
                claudeAgents.description shouldContain "agents removed"

                entries.none { it.path == ".codex-plugin/skills" }.shouldBeTrue()
                entries.none { it.path == ".opencode/command" }.shouldBeTrue()
            } finally {
                cleanupDir(root)
            }
        }

        // --- nukeProject: Codex and OpenCode only ---

        test("nukeProject when only Codex and OpenCode hosts were installed") {
            val root = createTempRoot()
            try {
                val firstSkill = ourSkillNames.first()
                writeFile(root, ".codex-plugin/skills/$firstSkill/SKILL.md", "# Skill")
                writeFile(root, ".codex-plugin/plugin.json", "{}")
                writeFile(root, ".opencode/command/$firstSkill.md", "bar")
                writeFile(root, ".opencode/agent/${ourAgentNames.first()}", "lead")
                writeFile(
                    root,
                    "AGENTS.md",
                    "<!-- >>> opsx >>> -->\nblock\n<!-- <<< opsx <<< -->\n",
                )

                val entries = NukeRunner.nukeProject(root, resources)

                entries.none { it.path == ".claude/skills" }.shouldBeTrue()

                val codexSkills = entries.firstOrNull { it.path == ".codex-plugin/skills" }
                codexSkills.shouldNotBeNull()
                codexSkills.success.shouldBeTrue()

                val openCodeCommands = entries.firstOrNull { it.path == ".opencode/command" }
                openCodeCommands.shouldNotBeNull()
                openCodeCommands.success.shouldBeTrue()

                exists(root, "AGENTS.md").shouldBeFalse()
            } finally {
                cleanupDir(root)
            }
        }

        // --- nukeProject: correct counts ---

        test("nukeProject reports correct skill and agent counts per host") {
            val root = createTempRoot()
            try {
                val threeSkills = ourSkillNames.take(3)
                for (skill in threeSkills) {
                    writeFile(root, ".claude/skills/$skill/SKILL.md", "# $skill")
                }
                val twoAgents = ourAgentNames.take(2)
                for (agent in twoAgents) {
                    writeFile(root, ".claude/agents/$agent", "agent")
                }

                val entries = NukeRunner.nukeProject(root, resources)

                val skills = entries.first { it.path == ".claude/skills" }
                skills.description shouldBe "3 skills removed"

                val agents = entries.first { it.path == ".claude/agents" }
                agents.description shouldBe "2 agents removed"
            } finally {
                cleanupDir(root)
            }
        }

        // --- nukeProject: marker preservation ---

        test("nukeProject preserves non-marker content in instruction files") {
            val root = createTempRoot()
            try {
                val markerContent =
                    buildString {
                        append("# Header\n\nUser content here.\n\n")
                        append("<!-- >>> opsx >>> -->\nmanaged\n")
                        append("<!-- <<< opsx <<< -->\n\n")
                        append("More user content.\n")
                    }
                writeFile(root, "CLAUDE.md", markerContent)

                NukeRunner.nukeProject(root, resources)

                val content = readFile(root, "CLAUDE.md")
                content shouldContain "# Header"
                content shouldContain "User content here."
                content shouldContain "More user content."
                content.contains(">>> opsx >>>").shouldBeFalse()
            } finally {
                cleanupDir(root)
            }
        }

        // --- nukeProject: empty parent cleanup ---

        test("nukeProject cleans up empty parent directories after removing opsx files") {
            val root = createTempRoot()
            try {
                val firstSkill = ourSkillNames.first()
                val firstAgent = ourAgentNames.first()
                writeFile(root, ".claude/skills/$firstSkill/SKILL.md", "# Skill")
                writeFile(root, ".claude/agents/$firstAgent", "Agent")

                NukeRunner.nukeProject(root, resources)

                exists(root, ".claude/skills").shouldBeFalse()
                exists(root, ".claude/agents").shouldBeFalse()
                exists(root, ".claude").shouldBeFalse()
            } finally {
                cleanupDir(root)
            }
        }

        test("nukeProject does not remove parent dir if user files remain") {
            val root = createTempRoot()
            try {
                val firstSkill = ourSkillNames.first()
                writeFile(root, ".claude/skills/$firstSkill/SKILL.md", "# Skill")
                writeFile(root, ".claude/skills/user-skill/SKILL.md", "# User")
                writeFile(root, ".claude/settings.json", "{}")

                NukeRunner.nukeProject(root, resources)

                exists(root, ".claude/skills").shouldBeTrue()
                exists(root, ".claude/skills/user-skill/SKILL.md").shouldBeTrue()
                exists(root, ".claude").shouldBeTrue()
            } finally {
                cleanupDir(root)
            }
        }

        // --- error paths ---

        test("nukeProject reports failure when marker file is unreadable") {
            val root = createTempRoot()
            try {
                writeFile(
                    root,
                    "CLAUDE.md",
                    "# Proj\n\n<!-- >>> opsx >>> -->\nmanaged\n<!-- <<< opsx <<< -->\n",
                )
                java.io.File(Path(root, "CLAUDE.md").toString()).setReadable(false)

                val entries = NukeRunner.nukeProject(root, resources)

                val claudeEntry = entries.first { it.path == "CLAUDE.md" }
                claudeEntry.success.shouldBeFalse()
                claudeEntry.description shouldBe "strip marker"
                claudeEntry.error.shouldNotBeNull()
            } finally {
                java.io.File(Path(root, "CLAUDE.md").toString()).setReadable(true)
                cleanupDir(root)
            }
        }

        test("nukeProject reports failure when config file parent is read-only") {
            val root = createTempRoot()
            try {
                writeFile(root, ".opsx/config.json", "{}")
                java.io.File(Path(root, ".opsx").toString()).setWritable(false)

                val entries = NukeRunner.nukeProject(root, resources)

                val configEntry = entries.first { it.path == ".opsx/config.json" }
                configEntry.success.shouldBeFalse()
                configEntry.error.shouldNotBeNull()
            } finally {
                java.io.File(Path(root, ".opsx").toString()).setWritable(true)
                cleanupDir(root)
            }
        }

        test("nukeProject reports failure when cache directory cannot be deleted") {
            val root = createTempRoot()
            try {
                writeFile(root, ".opsx/cache/foo/bar.json", "{}")
                java.io.File(Path(root, ".opsx/cache/foo").toString()).setWritable(false)

                val entries = NukeRunner.nukeProject(root, resources)

                val cacheEntry = entries.first { it.path == ".opsx/cache" }
                cacheEntry.success.shouldBeFalse()
                cacheEntry.error shouldContain "failed to delete"
            } finally {
                java.io.File(Path(root, ".opsx/cache/foo").toString()).setWritable(true)
                cleanupDir(root)
            }
        }

        // --- NukeEntry data class ---

        test("NukeEntry defaults error to null") {
            val entry = NukeEntry(path = "foo", description = "ok", success = true)
            entry.error shouldBe null
        }

        test("NukeEntry carries error message on failure") {
            val entry =
                NukeEntry(
                    path = "bar",
                    description = "fail",
                    success = false,
                    error = "denied",
                )
            entry.error shouldBe "denied"
        }

        // --- integration: user files preserved across all hosts ---

        test("nukeProject full integration preserves user files across all hosts") {
            val root = createTempRoot()
            try {
                val skill1 = ourSkillNames.first()
                val skill2 = ourSkillNames.drop(1).first()
                val agent1 = ourAgentNames.first()
                val agentBase1 = agent1.removeSuffix(".md")

                writeFile(root, ".claude/skills/$skill1/SKILL.md", "opsx")
                writeFile(root, ".claude/skills/$skill2/SKILL.md", "opsx")
                writeFile(root, ".claude/skills/user-project/SKILL.md", "user")
                writeFile(root, ".claude/agents/$agent1", "opsx")
                writeFile(root, ".claude/agents/custom-bot.md", "user")

                writeFile(root, ".opencode/command/$skill1.md", "opsx")
                writeFile(root, ".opencode/command/user-cmd.md", "user")
                writeFile(root, ".opencode/agent/$agent1", "opsx")
                writeFile(root, ".opencode/agent/user-agent.md", "user")

                writeFile(root, ".codex-plugin/skills/$skill1/SKILL.md", "opsx")
                writeFile(root, ".codex-plugin/skills/user-codex-skill/SKILL.md", "user")
                writeFile(root, ".codex-plugin/agents/$agent1", "opsx")
                writeFile(root, ".codex-plugin/agents/user-codex-agent.md", "user")
                writeFile(root, ".codex-plugin/plugin.json", "{}")

                writeFile(root, ".opsx/cache/opsx-plugin/skills/$skill1/SKILL.md", "opsx")
                writeFile(root, ".opsx/cache/opsx-plugin/skills/user-copilot/SKILL.md", "user")
                writeFile(root, ".opsx/cache/opsx-plugin/agents/$agentBase1.agent.md", "opsx")
                writeFile(root, ".opsx/cache/opsx-plugin/agents/user-bot.agent.md", "user")

                writeFile(root, ".opsx/config.json", "{}")

                NukeRunner.nukeProject(root, resources)

                exists(root, ".claude/skills/$skill1").shouldBeFalse()
                exists(root, ".claude/skills/$skill2").shouldBeFalse()
                exists(root, ".claude/skills/user-project/SKILL.md").shouldBeTrue()
                exists(root, ".claude/agents/$agent1").shouldBeFalse()
                exists(root, ".claude/agents/custom-bot.md").shouldBeTrue()

                exists(root, ".opencode/command/$skill1.md").shouldBeFalse()
                exists(root, ".opencode/command/user-cmd.md").shouldBeTrue()
                exists(root, ".opencode/agent/$agent1").shouldBeFalse()
                exists(root, ".opencode/agent/user-agent.md").shouldBeTrue()

                exists(root, ".codex-plugin/skills/$skill1").shouldBeFalse()
                exists(root, ".codex-plugin/skills/user-codex-skill/SKILL.md").shouldBeTrue()
                exists(root, ".codex-plugin/agents/$agent1").shouldBeFalse()
                exists(root, ".codex-plugin/agents/user-codex-agent.md").shouldBeTrue()
                exists(root, ".codex-plugin/plugin.json").shouldBeFalse()

                exists(root, ".opsx/cache").shouldBeFalse()
                exists(root, ".opsx/config.json").shouldBeFalse()
            } finally {
                cleanupDir(root)
            }
        }
    })
