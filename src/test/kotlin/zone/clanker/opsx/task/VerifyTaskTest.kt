package zone.clanker.opsx.task

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.ChangeConfig
import java.io.File

private fun createConfig() = Opsx.SettingsExtension().toOpsxConfig()

class VerifyTaskTest :
    BehaviorSpec({

        given("VerifyTask.buildVerifyPrompt") {

            `when`("both context and change context provided") {
                val projectDir =
                    File.createTempFile("opsx-verify", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-verify", VerifyTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                val prompt =
                    task.buildVerifyPrompt(
                        projectDir,
                        "codebase context content",
                        "change context content",
                    )

                then("includes codebase context") {
                    prompt shouldContain "codebase context content"
                }

                then("includes change context") {
                    prompt shouldContain "change context content"
                }

                then("includes verification instructions") {
                    prompt shouldContain "Verify that this change was implemented correctly"
                    prompt shouldContain "All tests pass"
                    prompt shouldContain "design was followed faithfully"
                    prompt shouldContain "No regressions"
                    prompt shouldContain "tasks.md"
                    prompt shouldContain "Exit with code 0"
                }
            }

            `when`("context is empty") {
                val projectDir =
                    File.createTempFile("opsx-verify", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-verify", VerifyTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                val prompt =
                    task.buildVerifyPrompt(
                        projectDir,
                        "",
                        "change context content",
                    )

                then("skips empty context section") {
                    prompt shouldNotContain "# Codebase Context"
                }

                then("includes change context") {
                    prompt shouldContain "change context content"
                }

                then("still includes instructions") {
                    prompt shouldContain "Verify that this change"
                }
            }

            `when`("change context is empty") {
                val projectDir =
                    File.createTempFile("opsx-verify", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-verify", VerifyTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                val prompt =
                    task.buildVerifyPrompt(
                        projectDir,
                        "codebase context",
                        "",
                    )

                then("skips empty change section") {
                    prompt shouldNotContain "# Change"
                }

                then("includes codebase context") {
                    prompt shouldContain "codebase context"
                }

                then("still includes instructions") {
                    prompt shouldContain "Verify that this change"
                }
            }

            `when`("both inputs are empty") {
                val projectDir =
                    File.createTempFile("opsx-verify", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-verify", VerifyTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                val prompt = task.buildVerifyPrompt(projectDir, "", "")

                then("only includes instructions section") {
                    prompt shouldNotContain "# Codebase Context"
                    prompt shouldNotContain "# Change"
                    prompt shouldContain "Verify that this change"
                    prompt shouldContain "All tests pass"
                }
            }
        }

        given("VerifyTask.run") {

            `when`("no change property is provided") {
                val projectDir =
                    File.createTempFile("opsx-verify-run", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-verify-no-prop", VerifyTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                then("throws error requiring change property") {
                    val ex = shouldThrow<IllegalStateException> { task.run() }
                    ex.message shouldContain Opsx.PROP_CHANGE
                }
            }

            `when`("change property is set but change does not exist") {
                val projectDir =
                    File.createTempFile("opsx-verify-run", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-verify-missing", VerifyTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())
                task.changeName.set("nonexistent")

                then("throws error that change was not found") {
                    val ex = shouldThrow<IllegalStateException> { task.run() }
                    ex.message shouldContain "Change not found"
                    ex.message shouldContain "nonexistent"
                }
            }

            `when`("change has a verify command in .opsx.yaml that succeeds") {
                val projectDir =
                    File.createTempFile("opsx-verify-cmd", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changeDir = File(projectDir, "opsx/changes/test-cmd")
                changeDir.mkdirs()
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(name = "test-cmd", status = "active", verify = "true"),
                )

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-verify-cmd-ok", VerifyTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())
                task.changeName.set("test-cmd")

                then("runs verify command and marks as verified") {
                    task.run()
                    val config = ChangeConfig.parse(File(changeDir, ".opsx.yaml"))
                    config!!.status shouldContain "verified"
                }
            }

            `when`("change has a verify command that fails") {
                val projectDir =
                    File.createTempFile("opsx-verify-fail", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changeDir = File(projectDir, "opsx/changes/test-fail")
                changeDir.mkdirs()
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(name = "test-fail", status = "active", verify = "false"),
                )

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-verify-cmd-fail", VerifyTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())
                task.changeName.set("test-fail")

                then("throws GradleException and does not mark as verified") {
                    val ex = shouldThrow<GradleException> { task.run() }
                    ex.message shouldContain "verify command failed"
                    val config = ChangeConfig.parse(File(changeDir, ".opsx.yaml"))
                    config!!.status shouldContain "active"
                }
            }

            `when`("change has no verify command — agent fallback path") {
                val projectDir =
                    File.createTempFile("opsx-verify-agent", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changeDir = File(projectDir, "opsx/changes/test-agent")
                changeDir.mkdirs()
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(name = "test-agent", status = "active"),
                )
                File(changeDir, "proposal.md").writeText("proposal content")

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-verify-agent", VerifyTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())
                task.changeName.set("test-agent")
                task.agent.set("nonexistent-agent-binary-xyz")
                task.model.set("")

                then("throws when agent is unknown") {
                    val ex = shouldThrow<IllegalStateException> { task.run() }
                    ex.message shouldContain "Unknown agent"
                }
            }

            `when`("change has no config file") {
                val projectDir =
                    File.createTempFile("opsx-verify-noconfig", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changeDir = File(projectDir, "opsx/changes/no-config")
                changeDir.mkdirs()
                // No .opsx.yaml — ChangeReader will use dir name and default status

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-verify-noconfig", VerifyTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())
                task.changeName.set("no-config")
                task.agent.set("nonexistent-agent-binary-xyz")
                task.model.set("")

                then("falls back to agent verify path and throws when agent unknown") {
                    val ex = shouldThrow<IllegalStateException> { task.run() }
                    ex.message shouldContain "Unknown agent"
                }
            }
        }
    })
