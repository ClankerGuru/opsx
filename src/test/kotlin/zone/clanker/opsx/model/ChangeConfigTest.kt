package zone.clanker.opsx.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File

class ChangeConfigTest :
    BehaviorSpec({
        given("ChangeConfig.parse") {
            `when`("file does not exist") {
                then("returns null") {
                    val result = ChangeConfig.parse(File("/nonexistent/.opsx.yaml"))
                    result.shouldBeNull()
                }
            }

            `when`("file has basic key-value pairs") {
                val tempFile = File.createTempFile("opsx", ".yaml")
                tempFile.deleteOnExit()
                tempFile.writeText(
                    """
                    name: my-change
                    status: active
                    """.trimIndent(),
                )

                then("parses name and status") {
                    val result = ChangeConfig.parse(tempFile)
                    result.shouldNotBeNull()
                    result.name shouldBe "my-change"
                    result.status shouldBe "active"
                    result.depends shouldBe emptyList()
                }
            }

            `when`("file has bracket-style depends") {
                val tempFile = File.createTempFile("opsx", ".yaml")
                tempFile.deleteOnExit()
                tempFile.writeText(
                    """
                    name: feature-b
                    status: draft
                    depends: [feature-a, feature-c]
                    """.trimIndent(),
                )

                then("parses depends list") {
                    val result = ChangeConfig.parse(tempFile)
                    result.shouldNotBeNull()
                    result.name shouldBe "feature-b"
                    result.status shouldBe "draft"
                    result.depends shouldBe listOf("feature-a", "feature-c")
                }
            }

            `when`("file has list-style depends") {
                val tempFile = File.createTempFile("opsx", ".yaml")
                tempFile.deleteOnExit()
                tempFile.writeText(
                    """
                    name: feature-x
                    status: active
                    depends:
                    - dep-one
                    - dep-two
                    """.trimIndent(),
                )

                then("parses depends list") {
                    val result = ChangeConfig.parse(tempFile)
                    result.shouldNotBeNull()
                    result.name shouldBe "feature-x"
                    result.depends shouldBe listOf("dep-one", "dep-two")
                }
            }

            `when`("file has empty depends") {
                val tempFile = File.createTempFile("opsx", ".yaml")
                tempFile.deleteOnExit()
                tempFile.writeText(
                    """
                    name: solo-change
                    status: active
                    depends: []
                    """.trimIndent(),
                )

                then("parses empty depends") {
                    val result = ChangeConfig.parse(tempFile)
                    result.shouldNotBeNull()
                    result.depends shouldBe emptyList()
                }
            }

            `when`("file has no name") {
                val tempFile = File.createTempFile("opsx", ".yaml")
                tempFile.deleteOnExit()
                tempFile.writeText(
                    """
                    status: active
                    """.trimIndent(),
                )

                then("returns null") {
                    val result = ChangeConfig.parse(tempFile)
                    result.shouldBeNull()
                }
            }

            `when`("file has comments and blank lines") {
                val tempFile = File.createTempFile("opsx", ".yaml")
                tempFile.deleteOnExit()
                tempFile.writeText(
                    """
                    # This is a comment
                    name: commented-change

                    # Another comment
                    status: archived
                    """.trimIndent(),
                )

                then("ignores comments and blank lines") {
                    val result = ChangeConfig.parse(tempFile)
                    result.shouldNotBeNull()
                    result.name shouldBe "commented-change"
                    result.status shouldBe "archived"
                }
            }

            `when`("file has default status") {
                val tempFile = File.createTempFile("opsx", ".yaml")
                tempFile.deleteOnExit()
                tempFile.writeText(
                    """
                    name: no-status
                    """.trimIndent(),
                )

                then("defaults to active") {
                    val result = ChangeConfig.parse(tempFile)
                    result.shouldNotBeNull()
                    result.status shouldBe "active"
                }
            }
        }

        given("ChangeConfig.write") {
            `when`("writing config with depends") {
                val tempFile = File.createTempFile("opsx", ".yaml")
                tempFile.deleteOnExit()
                val config =
                    ChangeConfig(
                        name = "write-test",
                        status = "draft",
                        depends = listOf("dep-a", "dep-b"),
                    )

                then("writes valid YAML that can be parsed back") {
                    ChangeConfig.write(tempFile, config)
                    val parsed = ChangeConfig.parse(tempFile)
                    parsed.shouldNotBeNull()
                    parsed.name shouldBe "write-test"
                    parsed.status shouldBe "draft"
                    parsed.depends shouldBe listOf("dep-a", "dep-b")
                }
            }

            `when`("writing config without depends") {
                val tempFile = File.createTempFile("opsx", ".yaml")
                tempFile.deleteOnExit()
                val config = ChangeConfig(name = "no-deps", status = "active")

                then("omits depends line") {
                    ChangeConfig.write(tempFile, config)
                    val content = tempFile.readText()
                    content shouldBe "name: no-deps\nstatus: active\n"
                }
            }
        }

        given("ChangeConfig.updateStatus") {
            `when`("file exists with existing status") {
                val tempFile = File.createTempFile("opsx", ".yaml")
                tempFile.deleteOnExit()
                tempFile.writeText(
                    """
                    name: my-change
                    status: active
                    """.trimIndent(),
                )

                then("replaces the status line") {
                    ChangeConfig.updateStatus(tempFile, "completed")
                    val parsed = ChangeConfig.parse(tempFile)
                    parsed.shouldNotBeNull()
                    parsed.name shouldBe "my-change"
                    parsed.status shouldBe "completed"
                }
            }

            `when`("file does not exist") {
                val tempFile = File.createTempFile("opsx", ".yaml")
                tempFile.deleteOnExit()
                tempFile.delete()

                then("throws IllegalArgumentException") {
                    shouldThrow<IllegalArgumentException> {
                        ChangeConfig.updateStatus(tempFile, "draft")
                    }
                }
            }

            `when`("file exists without status line") {
                val tempFile = File.createTempFile("opsx", ".yaml")
                tempFile.deleteOnExit()
                tempFile.writeText("name: no-status-change\n")

                then("adds status line") {
                    ChangeConfig.updateStatus(tempFile, "in-progress")
                    val content = tempFile.readText()
                    content.lines().filter { it.startsWith("status:") }.size shouldBe 1
                    val parsed = ChangeConfig.parse(tempFile)
                    parsed.shouldNotBeNull()
                    parsed.status shouldBe "in-progress"
                }
            }
        }

        given("ChangeConfig with verify field") {
            `when`("file has verify command") {
                val tempFile = File.createTempFile("opsx", ".yaml")
                tempFile.deleteOnExit()
                tempFile.writeText(
                    """
                    name: with-verify
                    status: active
                    verify: ./gradlew test --tests "*.ChangeConfigTest"
                    """.trimIndent(),
                )

                then("parses verify command") {
                    val result = ChangeConfig.parse(tempFile)
                    result.shouldNotBeNull()
                    result.verify shouldBe "./gradlew test --tests \"*.ChangeConfigTest\""
                }
            }

            `when`("file has no verify") {
                val tempFile = File.createTempFile("opsx", ".yaml")
                tempFile.deleteOnExit()
                tempFile.writeText(
                    """
                    name: no-verify
                    status: active
                    """.trimIndent(),
                )

                then("defaults to empty string") {
                    val result = ChangeConfig.parse(tempFile)
                    result.shouldNotBeNull()
                    result.verify shouldBe ""
                }
            }

            `when`("writing config with verify") {
                val tempFile = File.createTempFile("opsx", ".yaml")
                tempFile.deleteOnExit()
                val config =
                    ChangeConfig(
                        name = "verified",
                        status = "active",
                        verify = "./gradlew test",
                    )

                then("round-trips correctly") {
                    ChangeConfig.write(tempFile, config)
                    val parsed = ChangeConfig.parse(tempFile)
                    parsed.shouldNotBeNull()
                    parsed.verify shouldBe "./gradlew test"
                }
            }
        }

        given("ChangeConfig data class") {
            `when`("constructed with defaults") {
                val config = ChangeConfig(name = "test")

                then("has correct defaults") {
                    config.status shouldBe "active"
                    config.depends shouldBe emptyList()
                    config.verify shouldBe ""
                }
            }

            `when`("constructed with all fields") {
                val config =
                    ChangeConfig(
                        name = "full",
                        status = "archived",
                        depends = listOf("a", "b"),
                        verify = "./gradlew check",
                    )

                then("has correct values") {
                    config.name shouldBe "full"
                    config.status shouldBe "archived"
                    config.depends shouldBe listOf("a", "b")
                    config.verify shouldBe "./gradlew check"
                }
            }
        }
    })
