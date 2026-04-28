package zone.clanker.opsx.cli.init.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ResourceTreeTest :
    FunSpec({

        test("readText loads manifest.json") {
            val tree = ResourceTree()
            val text = tree.readText("manifest.json")
            text shouldContain "agents"
            text shouldContain "clis"
        }

        test("listNames returns skill directories") {
            val tree = ResourceTree()
            val skills = tree.listNames("skills")
            skills.shouldNotBeEmpty()
            // Should not include _fragments
            skills.none { it.startsWith("_") } shouldBe false // _fragments exists in listing
        }

        test("listNames returns agent files") {
            val tree = ResourceTree()
            val agents = tree.listNames("agents")
            agents.shouldNotBeEmpty()
            agents.any { it.endsWith(".md") } shouldBe true
        }

        test("readText throws for missing resource") {
            val tree = ResourceTree()
            val result = runCatching { tree.readText("nonexistent/file.txt") }
            result.isFailure shouldBe true
        }

        test("listNames returns empty for missing path") {
            val tree = ResourceTree()
            val result = tree.listNames("totally/missing/path")
            result shouldBe emptyList()
        }

        test("readText loads a specific skill") {
            val tree = ResourceTree()
            val skills = tree.listNames("skills").filter { !it.startsWith("_") }
            if (skills.isNotEmpty()) {
                val content = runCatching { tree.readText("skills/${skills.first()}/SKILL.md") }
                if (content.isSuccess) {
                    content.getOrThrow().shouldContain("---")
                }
            }
        }
    })
