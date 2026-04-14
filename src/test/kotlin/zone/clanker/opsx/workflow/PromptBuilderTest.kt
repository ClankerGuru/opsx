package zone.clanker.opsx.workflow

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.Change
import java.io.File

private fun createConfig() = Opsx.SettingsExtension().toOpsxConfig()

class PromptBuilderTest :
    BehaviorSpec({
        given("PromptBuilder.srcxContext") {
            `when`("file exists") {
                val tempDir =
                    File.createTempFile("opsx-root", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()
                val srcxDir = File(tempDir, ".srcx")
                srcxDir.mkdirs()
                File(srcxDir, "context.md").writeText("srcx context content")

                val builder = PromptBuilder(tempDir)

                then("returns file content") {
                    builder.srcxContext() shouldBe "srcx context content"
                }
            }

            `when`("file does not exist") {
                val tempDir =
                    File.createTempFile("opsx-root", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val builder = PromptBuilder(tempDir)

                then("returns empty string") {
                    builder.srcxContext() shouldBe ""
                }
            }

            `when`("file exceeds maxChars") {
                val tempDir =
                    File.createTempFile("opsx-root", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()
                val srcxDir = File(tempDir, ".srcx")
                srcxDir.mkdirs()
                File(srcxDir, "context.md").writeText("A".repeat(200))

                val builder = PromptBuilder(tempDir)

                then("truncates and appends notice") {
                    val result = builder.srcxContext(maxChars = 100)
                    result shouldContain "A".repeat(100)
                    result shouldContain "[... truncated at 100 chars]"
                }
            }
        }

        given("PromptBuilder.projectDescription") {
            `when`("file exists") {
                val tempDir =
                    File.createTempFile("opsx-root", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()
                val opsxDir = File(tempDir, "opsx")
                opsxDir.mkdirs()
                File(opsxDir, "project.md").writeText("project description")

                val builder = PromptBuilder(tempDir)

                then("returns file content") {
                    builder.projectDescription(createConfig()) shouldBe "project description"
                }
            }

            `when`("file does not exist") {
                val tempDir =
                    File.createTempFile("opsx-root", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val builder = PromptBuilder(tempDir)

                then("returns empty string") {
                    builder.projectDescription(createConfig()) shouldBe ""
                }
            }
        }

        given("PromptBuilder.specContent") {
            `when`("spec file exists") {
                val tempDir =
                    File.createTempFile("opsx-root", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()
                val specsDir = File(tempDir, "opsx/specs")
                specsDir.mkdirs()
                File(specsDir, "my-spec.md").writeText("spec content here")

                val builder = PromptBuilder(tempDir)

                then("returns spec content") {
                    builder.specContent(createConfig(), "my-spec") shouldBe "spec content here"
                }
            }

            `when`("spec file does not exist") {
                val tempDir =
                    File.createTempFile("opsx-root", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val builder = PromptBuilder(tempDir)

                then("returns empty string") {
                    builder.specContent(createConfig(), "missing-spec") shouldBe ""
                }
            }
        }

        given("PromptBuilder.changeContext") {
            `when`("all files present") {
                val tempDir =
                    File.createTempFile("opsx-change", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()
                File(tempDir, "proposal.md").writeText("proposal text")
                File(tempDir, "design.md").writeText("design text")
                File(tempDir, "tasks.md").writeText("tasks text")
                File(tempDir, "feedback.md").writeText("feedback text")

                val change = Change("test", "active", emptyList(), tempDir)
                val builder = PromptBuilder(tempDir)

                then("includes all sections") {
                    val result = builder.changeContext(change)
                    result shouldContain "## Proposal"
                    result shouldContain "proposal text"
                    result shouldContain "## Design"
                    result shouldContain "design text"
                    result shouldContain "## Tasks"
                    result shouldContain "tasks text"
                    result shouldContain "## Feedback"
                    result shouldContain "feedback text"
                }
            }

            `when`("some files missing") {
                val tempDir =
                    File.createTempFile("opsx-change", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()
                File(tempDir, "proposal.md").writeText("proposal only")

                val change = Change("test", "active", emptyList(), tempDir)
                val builder = PromptBuilder(tempDir)

                then("includes only existing sections") {
                    val result = builder.changeContext(change)
                    result shouldContain "## Proposal"
                    result shouldContain "proposal only"
                    result shouldNotContain "## Design"
                    result shouldNotContain "## Tasks"
                    result shouldNotContain "## Feedback"
                }
            }

            `when`("all files missing") {
                val tempDir =
                    File.createTempFile("opsx-change", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val change = Change("test", "active", emptyList(), tempDir)
                val builder = PromptBuilder(tempDir)

                then("returns empty string") {
                    builder.changeContext(change) shouldBe ""
                }
            }
        }

        given("PromptBuilder.build") {
            `when`("multiple sections provided") {
                val builder = PromptBuilder(File("/tmp"))

                then("includes all non-blank sections with headings and separators") {
                    val result =
                        builder.build(
                            "Context" to "context content",
                            "Project" to "project content",
                        )
                    result shouldContain "# Context"
                    result shouldContain "context content"
                    result shouldContain "# Project"
                    result shouldContain "project content"
                    result shouldContain "---"
                }
            }

            `when`("some sections are blank") {
                val builder = PromptBuilder(File("/tmp"))

                then("skips blank sections") {
                    val result =
                        builder.build(
                            "Included" to "has content",
                            "Skipped" to "",
                            "Also Skipped" to "   ",
                            "Also Included" to "more content",
                        )
                    result shouldContain "# Included"
                    result shouldContain "has content"
                    result shouldNotContain "# Skipped"
                    result shouldNotContain "# Also Skipped"
                    result shouldContain "# Also Included"
                    result shouldContain "more content"
                }
            }
        }
    })
