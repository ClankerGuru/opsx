package zone.clanker.opsx.cli.init.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class ManifestTest :
    FunSpec({

        test("parses agents and clis") {
            val json =
                """
                {
                  "agents": {
                    "lead": { "description": "Tech Lead", "color": "green", "skills": ["opsx-propose"] }
                  },
                  "clis": {
                    "claude": { "skillsDir": ".claude/skills", "agentDir": ".claude/agents",
                                "instructionFile": "CLAUDE.md", "frontmatter": "plain" }
                  }
                }
                """.trimIndent()

            val manifest = Manifest.parse(json)
            manifest.agents.size shouldBe 1
            manifest.agents["lead"]?.description shouldBe "Tech Lead"
            manifest.agents["lead"]?.skills!! shouldContain "opsx-propose"
            manifest.clis["claude"]?.skillsDir shouldBe ".claude/skills"
        }

        test("skills defaults to empty when missing") {
            val json = """{"agents":{"x":{"description":"d","color":"c"}},"clis":{}}"""
            Manifest.parse(json).agents["x"]?.skills shouldBe emptyList()
        }

        test("ignores unknown keys") {
            val json = """{"agents":{},"clis":{},"extra":"ignored"}"""
            Manifest.parse(json).agents.size shouldBe 0
        }

        test("CliEntry exposes all fields") {
            val json =
                """
                {
                  "agents": {},
                  "clis": {
                    "claude": {
                      "skillsDir": ".claude/skills",
                      "agentDir": ".claude/agents",
                      "instructionFile": "CLAUDE.md",
                      "frontmatter": "plain"
                    }
                  }
                }
                """.trimIndent()

            val cli = Manifest.parse(json).clis["claude"]!!
            cli.skillsDir shouldBe ".claude/skills"
            cli.agentDir shouldBe ".claude/agents"
            cli.instructionFile shouldBe "CLAUDE.md"
            cli.frontmatter shouldBe "plain"
        }

        test("AgentEntry exposes all fields including color") {
            val json =
                """
                {
                  "agents": {
                    "lead": { "description": "Tech Lead", "color": "green", "skills": ["a", "b"] }
                  },
                  "clis": {}
                }
                """.trimIndent()

            val agent = Manifest.parse(json).agents["lead"]!!
            agent.description shouldBe "Tech Lead"
            agent.color shouldBe "green"
            agent.skills shouldBe listOf("a", "b")
        }
    })
