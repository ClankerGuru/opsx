package zone.clanker.opsx.cli.init.config

import io.kotest.assertions.withClue
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

        test("readText loads every bundled skill with well-formed front matter") {
            val tree = ResourceTree()
            val skills = tree.listNames("skills").filter { !it.startsWith("_") }
            skills.shouldNotBeEmpty()
            skills.forEach { skill ->
                withClue("skills/$skill/SKILL.md") {
                    val lines = tree.readText("skills/$skill/SKILL.md").lines()
                    lines.first() shouldBe "---"
                    val closing = lines.drop(1).indexOf("---")
                    (closing >= 0) shouldBe true
                    val frontMatter = lines.subList(1, closing + 1)
                    frontMatter.any { it.startsWith("name: ") && it.length > "name: ".length } shouldBe true
                    frontMatter.any {
                        it.startsWith("description: ") && it.length > "description: ".length
                    } shouldBe true
                }
            }
        }
    })
