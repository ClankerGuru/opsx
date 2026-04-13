package zone.clanker.opsx.workflow

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.ChangeConfig
import zone.clanker.opsx.model.ChangeStatus
import java.io.File

private fun createExtension(): Opsx.SettingsExtension = Opsx.SettingsExtension()

class ChangeWriterTest :
    BehaviorSpec({

        given("ChangeWriter.createChangeDir") {

            `when`("directory does not exist") {
                val rootDir =
                    File.createTempFile("opsx-writer", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val writer = ChangeWriter(rootDir, createExtension())

                val changeDir = writer.createChangeDir("new-feature")

                then("creates the directory") {
                    changeDir.exists() shouldBe true
                    changeDir.isDirectory shouldBe true
                }

                then("creates directory at correct path") {
                    changeDir.path shouldBe File(rootDir, "opsx/changes/new-feature").path
                }
            }

            `when`("directory already exists") {
                val rootDir =
                    File.createTempFile("opsx-writer", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val writer = ChangeWriter(rootDir, createExtension())
                val existing = File(rootDir, "opsx/changes/existing")
                existing.mkdirs()
                File(existing, "proposal.md").writeText("existing proposal")

                val changeDir = writer.createChangeDir("existing")

                then("directory still exists and preserves contents") {
                    changeDir.exists() shouldBe true
                    File(changeDir, "proposal.md").readText() shouldBe "existing proposal"
                }
            }

            `when`("custom extension directories") {
                val rootDir =
                    File.createTempFile("opsx-writer", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val ext =
                    Opsx.SettingsExtension().apply {
                        outputDir = "custom-out"
                        changesDir = "my-changes"
                    }
                val writer = ChangeWriter(rootDir, ext)

                val changeDir = writer.createChangeDir("test-change")

                then("uses custom directories") {
                    changeDir.path shouldBe File(rootDir, "custom-out/my-changes/test-change").path
                    changeDir.exists() shouldBe true
                }
            }
        }

        given("ChangeWriter.writeConfig") {

            `when`("writing config for a new change") {
                val rootDir =
                    File.createTempFile("opsx-writer", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val writer = ChangeWriter(rootDir, createExtension())
                val changeDir = File(rootDir, "opsx/changes/test").apply { mkdirs() }

                writer.writeConfig(changeDir, "test", ChangeStatus.ACTIVE)

                then("creates .opsx.yaml with correct content") {
                    val configFile = File(changeDir, ".opsx.yaml")
                    configFile.exists() shouldBe true
                    val config = ChangeConfig.parse(configFile)
                    config?.name shouldBe "test"
                    config?.status shouldBe "active"
                }
            }

            `when`("writing config with draft status") {
                val rootDir =
                    File.createTempFile("opsx-writer", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val writer = ChangeWriter(rootDir, createExtension())
                val changeDir = File(rootDir, "opsx/changes/draft-test").apply { mkdirs() }

                writer.writeConfig(changeDir, "draft-test", ChangeStatus.DRAFT)

                then("creates config with draft status") {
                    val config = ChangeConfig.parse(File(changeDir, ".opsx.yaml"))
                    config?.status shouldBe "draft"
                }
            }
        }

        given("ChangeWriter.writeProposal") {

            `when`("writing proposal content") {
                val rootDir =
                    File.createTempFile("opsx-writer", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val writer = ChangeWriter(rootDir, createExtension())
                val changeDir = File(rootDir, "opsx/changes/test").apply { mkdirs() }

                writer.writeProposal(changeDir, "This is a proposal.")

                then("creates proposal.md with content") {
                    val file = File(changeDir, "proposal.md")
                    file.exists() shouldBe true
                    file.readText() shouldBe "This is a proposal."
                }
            }

            `when`("overwriting existing proposal") {
                val rootDir =
                    File.createTempFile("opsx-writer", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val writer = ChangeWriter(rootDir, createExtension())
                val changeDir = File(rootDir, "opsx/changes/test").apply { mkdirs() }
                File(changeDir, "proposal.md").writeText("old proposal")

                writer.writeProposal(changeDir, "new proposal")

                then("overwrites with new content") {
                    File(changeDir, "proposal.md").readText() shouldBe "new proposal"
                }
            }
        }

        given("ChangeWriter.writeDesignSkeleton") {

            `when`("design.md does not exist") {
                val rootDir =
                    File.createTempFile("opsx-writer", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val writer = ChangeWriter(rootDir, createExtension())
                val changeDir = File(rootDir, "opsx/changes/test").apply { mkdirs() }

                writer.writeDesignSkeleton(changeDir, "test-change")

                then("creates design.md with skeleton content") {
                    val file = File(changeDir, "design.md")
                    file.exists() shouldBe true
                    file.readText() shouldContain "# Design: test-change"
                    file.readText() shouldContain "Fill in the design details"
                }
            }

            `when`("design.md already exists") {
                val rootDir =
                    File.createTempFile("opsx-writer", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val writer = ChangeWriter(rootDir, createExtension())
                val changeDir = File(rootDir, "opsx/changes/test").apply { mkdirs() }
                File(changeDir, "design.md").writeText("existing design content")

                writer.writeDesignSkeleton(changeDir, "test-change")

                then("does not overwrite existing file") {
                    File(changeDir, "design.md").readText() shouldBe "existing design content"
                }
            }
        }

        given("ChangeWriter.writeTasksSkeleton") {

            `when`("tasks.md does not exist") {
                val rootDir =
                    File.createTempFile("opsx-writer", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val writer = ChangeWriter(rootDir, createExtension())
                val changeDir = File(rootDir, "opsx/changes/test").apply { mkdirs() }

                writer.writeTasksSkeleton(changeDir, "test-change")

                then("creates tasks.md with skeleton content") {
                    val file = File(changeDir, "tasks.md")
                    file.exists() shouldBe true
                    file.readText() shouldContain "# Tasks: test-change"
                    file.readText() shouldContain "- [ ] TODO"
                }
            }

            `when`("tasks.md already exists") {
                val rootDir =
                    File.createTempFile("opsx-writer", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val writer = ChangeWriter(rootDir, createExtension())
                val changeDir = File(rootDir, "opsx/changes/test").apply { mkdirs() }
                File(changeDir, "tasks.md").writeText("existing tasks content")

                writer.writeTasksSkeleton(changeDir, "test-change")

                then("does not overwrite existing file") {
                    File(changeDir, "tasks.md").readText() shouldBe "existing tasks content"
                }
            }
        }

        given("ChangeWriter.updateStatus") {

            `when`("config file exists with current status") {
                val rootDir =
                    File.createTempFile("opsx-writer", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val writer = ChangeWriter(rootDir, createExtension())
                val changeDir = File(rootDir, "opsx/changes/test").apply { mkdirs() }
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(name = "test", status = "active"),
                )

                writer.updateStatus(changeDir, ChangeStatus.IN_PROGRESS)

                then("updates the status") {
                    val config = ChangeConfig.parse(File(changeDir, ".opsx.yaml"))
                    config?.status shouldBe "in-progress"
                }
            }

            `when`("config file does not exist") {
                val rootDir =
                    File.createTempFile("opsx-writer", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val writer = ChangeWriter(rootDir, createExtension())
                val changeDir = File(rootDir, "opsx/changes/test").apply { mkdirs() }

                writer.updateStatus(changeDir, ChangeStatus.VERIFIED)

                then("creates config file with status") {
                    val file = File(changeDir, ".opsx.yaml")
                    file.exists() shouldBe true
                    file.readText() shouldContain "status: verified"
                }
            }

            `when`("updating to archived") {
                val rootDir =
                    File.createTempFile("opsx-writer", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val writer = ChangeWriter(rootDir, createExtension())
                val changeDir = File(rootDir, "opsx/changes/test").apply { mkdirs() }
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(name = "test", status = "done"),
                )

                writer.updateStatus(changeDir, ChangeStatus.ARCHIVED)

                then("updates status to archived") {
                    val config = ChangeConfig.parse(File(changeDir, ".opsx.yaml"))
                    config?.status shouldBe "archived"
                }
            }
        }

        given("ChangeWriter.appendFeedback") {

            `when`("feedback.md does not exist") {
                val rootDir =
                    File.createTempFile("opsx-writer", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val writer = ChangeWriter(rootDir, createExtension())
                val changeDir = File(rootDir, "opsx/changes/test").apply { mkdirs() }

                writer.appendFeedback(changeDir, "First feedback.")

                then("creates feedback.md with content") {
                    val file = File(changeDir, "feedback.md")
                    file.exists() shouldBe true
                    file.readText() shouldBe "First feedback."
                }
            }

            `when`("feedback.md already exists") {
                val rootDir =
                    File.createTempFile("opsx-writer", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val writer = ChangeWriter(rootDir, createExtension())
                val changeDir = File(rootDir, "opsx/changes/test").apply { mkdirs() }
                File(changeDir, "feedback.md").writeText("Existing feedback.")

                writer.appendFeedback(changeDir, "Additional feedback.")

                then("appends to existing content") {
                    val content = File(changeDir, "feedback.md").readText()
                    content shouldContain "Existing feedback."
                    content shouldContain "Additional feedback."
                }
            }

            `when`("appending multiple feedbacks") {
                val rootDir =
                    File.createTempFile("opsx-writer", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val writer = ChangeWriter(rootDir, createExtension())
                val changeDir = File(rootDir, "opsx/changes/test").apply { mkdirs() }

                writer.appendFeedback(changeDir, "First.")
                writer.appendFeedback(changeDir, "Second.")
                writer.appendFeedback(changeDir, "Third.")

                then("contains all feedback entries") {
                    val content = File(changeDir, "feedback.md").readText()
                    content shouldContain "First."
                    content shouldContain "Second."
                    content shouldContain "Third."
                }
            }

            `when`("appending empty feedback") {
                val rootDir =
                    File.createTempFile("opsx-writer", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val writer = ChangeWriter(rootDir, createExtension())
                val changeDir = File(rootDir, "opsx/changes/test").apply { mkdirs() }

                writer.appendFeedback(changeDir, "")

                then("creates file even with empty content") {
                    File(changeDir, "feedback.md").exists() shouldBe true
                }
            }
        }
    })
