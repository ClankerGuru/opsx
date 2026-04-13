package zone.clanker.opsx.task

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testfixtures.ProjectBuilder
import zone.clanker.opsx.Opsx
import java.io.File

private fun createExtension(): Opsx.SettingsExtension = Opsx.SettingsExtension()

class ProposeTaskTest :
    BehaviorSpec({

        given("ProposeTask.resolveChangeName") {

            `when`("spec is provided") {
                val projectDir =
                    File.createTempFile("opsx-propose", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-propose", ProposeTask::class.java)
                task.extension = createExtension()

                then("returns spec name") {
                    task.resolveChangeName("my-spec", null) shouldBe "my-spec"
                }
            }

            `when`("prompt is provided") {
                val projectDir =
                    File.createTempFile("opsx-propose", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-propose", ProposeTask::class.java)
                task.extension = createExtension()

                then("slugifies first 4 words") {
                    task.resolveChangeName(null, "Add user authentication flow") shouldBe
                        "add-user-authentication-flow"
                }
            }

            `when`("prompt has special characters") {
                val projectDir =
                    File.createTempFile("opsx-propose", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-propose", ProposeTask::class.java)
                task.extension = createExtension()

                then("removes special chars and limits to 4 words") {
                    task.resolveChangeName(null, "Fix the bug! In the auth module quickly") shouldBe
                        "fix-the-bug-in"
                }
            }

            `when`("prompt is empty") {
                val projectDir =
                    File.createTempFile("opsx-propose", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-propose", ProposeTask::class.java)
                task.extension = createExtension()

                then("returns untitled-change") {
                    task.resolveChangeName(null, "") shouldBe "untitled-change"
                }
            }

            `when`("explicit name overrides spec") {
                val projectDir =
                    File.createTempFile("opsx-propose", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-propose", ProposeTask::class.java)
                task.extension = createExtension()

                then("spec is used when no name override") {
                    task.resolveChangeName("my-spec", "some prompt") shouldBe "my-spec"
                }
            }
        }

        given("ProposeTask.buildProposalPrompt") {

            `when`("all inputs provided") {
                val projectDir =
                    File.createTempFile("opsx-propose", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-propose", ProposeTask::class.java)
                task.extension = createExtension()

                val changeDir = File(projectDir, "opsx/changes/test-change").apply { mkdirs() }
                val prompt =
                    task.buildProposalPrompt(
                        "context here",
                        "project desc",
                        "user request",
                        changeDir,
                    )

                then("includes context") {
                    prompt shouldContain "context here"
                }

                then("includes project description") {
                    prompt shouldContain "project desc"
                }

                then("includes user request") {
                    prompt shouldContain "user request"
                }

                then("includes change directory path") {
                    prompt shouldContain "opsx/changes/test-change"
                }

                then("includes artifact instructions") {
                    prompt shouldContain "proposal.md"
                    prompt shouldContain "design.md"
                    prompt shouldContain "tasks.md"
                }
            }
        }
    })
