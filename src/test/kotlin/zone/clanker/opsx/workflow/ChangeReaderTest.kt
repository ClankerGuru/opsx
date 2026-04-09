package zone.clanker.opsx.workflow

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import zone.clanker.opsx.Opsx
import java.io.File

class ChangeReaderTest :
    BehaviorSpec({
        given("ChangeReader.readAll") {
            `when`("changes directory does not exist") {
                val tempDir =
                    File.createTempFile("opsx-root", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()
                val reader = ChangeReader(tempDir, Opsx.SettingsExtension())

                then("returns empty list") {
                    reader.readAll() shouldHaveSize 0
                }
            }

            `when`("changes directory exists with changes") {
                val tempDir =
                    File.createTempFile("opsx-root", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val changesDir = File(tempDir, "opsx/changes")
                changesDir.mkdirs()

                val changeA = File(changesDir, "alpha")
                changeA.mkdirs()
                File(changeA, ".opsx.yaml").writeText(
                    """
                    name: alpha
                    status: active
                    """.trimIndent(),
                )
                File(changeA, "proposal.md").writeText("Proposal A")

                val changeB = File(changesDir, "beta")
                changeB.mkdirs()
                File(changeB, ".opsx.yaml").writeText(
                    """
                    name: beta
                    status: draft
                    depends: [alpha]
                    """.trimIndent(),
                )

                val reader = ChangeReader(tempDir, Opsx.SettingsExtension())

                then("reads all changes sorted by name") {
                    val changes = reader.readAll()
                    changes shouldHaveSize 2
                    changes[0].name shouldBe "alpha"
                    changes[0].status shouldBe "active"
                    changes[1].name shouldBe "beta"
                    changes[1].status shouldBe "draft"
                    changes[1].depends shouldBe listOf("alpha")
                }
            }

            `when`("change directory has no config file") {
                val tempDir =
                    File.createTempFile("opsx-root", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val changesDir = File(tempDir, "opsx/changes")
                changesDir.mkdirs()

                val changeDir = File(changesDir, "no-config")
                changeDir.mkdirs()

                val reader = ChangeReader(tempDir, Opsx.SettingsExtension())

                then("uses directory name as change name") {
                    val changes = reader.readAll()
                    changes shouldHaveSize 1
                    changes[0].name shouldBe "no-config"
                    changes[0].status shouldBe "active"
                }
            }
        }

        given("ChangeReader.readChange") {
            `when`("directory has config") {
                val tempDir =
                    File.createTempFile("opsx-change", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                File(tempDir, ".opsx.yaml").writeText(
                    """
                    name: test-change
                    status: archived
                    """.trimIndent(),
                )

                val reader = ChangeReader(File("/tmp"), Opsx.SettingsExtension())

                then("reads change from directory") {
                    val change = reader.readChange(tempDir)
                    change.shouldNotBeNull()
                    change.name shouldBe "test-change"
                    change.status shouldBe "archived"
                }
            }

            `when`("directory has no config") {
                val tempDir =
                    File.createTempFile("opsx-change", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val reader = ChangeReader(File("/tmp"), Opsx.SettingsExtension())

                then("uses directory name") {
                    val change = reader.readChange(tempDir)
                    change.shouldNotBeNull()
                    change.name shouldBe tempDir.name
                    change.status shouldBe "active"
                }
            }
        }

        given("ChangeReader with custom extension") {
            `when`("outputDir and changesDir are customized") {
                val tempDir =
                    File.createTempFile("opsx-root", "").also {
                        it.delete()
                        it.mkdirs()
                    }
                tempDir.deleteOnExit()

                val changesDir = File(tempDir, "custom/proposals")
                changesDir.mkdirs()

                val changeDir = File(changesDir, "my-proposal")
                changeDir.mkdirs()
                File(changeDir, ".opsx.yaml").writeText(
                    """
                    name: my-proposal
                    status: active
                    """.trimIndent(),
                )

                val ext = Opsx.SettingsExtension()
                ext.outputDir = "custom"
                ext.changesDir = "proposals"
                val reader = ChangeReader(tempDir, ext)

                then("reads from custom path") {
                    val changes = reader.readAll()
                    changes shouldHaveSize 1
                    changes[0].name shouldBe "my-proposal"
                }
            }
        }
    })
