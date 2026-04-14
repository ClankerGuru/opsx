package zone.clanker.opsx.task

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testfixtures.ProjectBuilder
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.ChangeConfig
import zone.clanker.opsx.model.ChangeStatus
import java.io.File

private fun createConfig() = Opsx.SettingsExtension().toOpsxConfig()

class ArchiveTaskTest :
    BehaviorSpec({

        given("ChangeStatus.canTransitionTo(ARCHIVED)") {

            `when`("status is completed") {
                then("can transition to archived") {
                    ChangeStatus.from("completed").canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }
            }

            `when`("status is done") {
                then("can transition to archived") {
                    ChangeStatus.from("done").canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }
            }

            `when`("status is verified") {
                then("can transition to archived") {
                    ChangeStatus.from("verified").canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }
            }

            `when`("status is archived") {
                then("cannot transition to archived") {
                    ChangeStatus.from("archived").canTransitionTo(ChangeStatus.ARCHIVED) shouldBe false
                }
            }

            `when`("status is active") {
                then("can transition to archived") {
                    ChangeStatus.from("active").canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }
            }

            `when`("status is draft") {
                then("can transition to archived") {
                    ChangeStatus.from("draft").canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }
            }

            `when`("status is pending") {
                then("can transition to archived") {
                    ChangeStatus.from("pending").canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }
            }

            `when`("status is in-progress") {
                then("cannot transition to archived") {
                    ChangeStatus.from("in-progress").canTransitionTo(ChangeStatus.ARCHIVED) shouldBe false
                }
            }
        }

        given("ArchiveTask with verify command gate") {

            `when`("change has verify command and status is verified") {
                val projectDir =
                    File.createTempFile("opsx-archive", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changeDir = File(projectDir, "opsx/changes/gate-test").apply { mkdirs() }
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(name = "gate-test", status = "verified", verify = "./gradlew test"),
                )

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-archive", ArchiveTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())
                task.changeName.set("gate-test")

                then("archives successfully") {
                    shouldNotThrow<IllegalArgumentException> { task.run() }
                    val config = ChangeConfig.parse(File(changeDir, ".opsx.yaml"))
                    config!!.status shouldBe "archived"
                }
            }

            `when`("change has verify command but status is active") {
                val projectDir =
                    File.createTempFile("opsx-archive", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changeDir = File(projectDir, "opsx/changes/gate-fail").apply { mkdirs() }
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(name = "gate-fail", status = "active", verify = "./gradlew test"),
                )

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-archive-fail", ArchiveTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())
                task.changeName.set("gate-fail")

                then("rejects with error about verify") {
                    val ex = shouldThrow<IllegalArgumentException> { task.run() }
                    ex.message shouldContain "verify"
                    ex.message shouldContain "not 'verified'"
                }
            }

            `when`("change has no verify command and status is done") {
                val projectDir =
                    File.createTempFile("opsx-archive", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changeDir = File(projectDir, "opsx/changes/no-verify").apply { mkdirs() }
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(name = "no-verify", status = "done"),
                )

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-archive-no-verify", ArchiveTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())
                task.changeName.set("no-verify")

                then("archives via normal transition") {
                    shouldNotThrow<IllegalArgumentException> { task.run() }
                    val config = ChangeConfig.parse(File(changeDir, ".opsx.yaml"))
                    config!!.status shouldBe "archived"
                }
            }
        }
    })
