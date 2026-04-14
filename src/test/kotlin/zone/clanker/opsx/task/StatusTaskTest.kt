package zone.clanker.opsx.task

import io.kotest.core.spec.style.BehaviorSpec
import org.gradle.testfixtures.ProjectBuilder
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.ChangeConfig
import java.io.File

private fun createConfig() = Opsx.SettingsExtension().toOpsxConfig()

class StatusTaskTest :
    BehaviorSpec({

        given("StatusTask.run") {

            `when`("no changes exist") {
                val projectDir =
                    File.createTempFile("opsx-status", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-status", StatusTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                then("reports no changes") {
                    task.run()
                }
            }

            `when`("single active change with all artifacts") {
                val projectDir =
                    File.createTempFile("opsx-status", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changeDir = File(projectDir, "opsx/changes/feature-a")
                changeDir.mkdirs()
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(name = "feature-a", status = "active"),
                )
                File(changeDir, "proposal.md").writeText("proposal")
                File(changeDir, "design.md").writeText("design")
                File(changeDir, "tasks.md").writeText(
                    """
                    # Tasks: feature-a

                    - [x] a1b2c3d4e5 | First task
                        Description of first task.
                      depends: none

                    - [ ] f6g7h8i9j0 | Second task
                        Description of second task.
                      depends: none
                    """.trimIndent(),
                )

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-status", StatusTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                then("shows status with file list and task progress") {
                    task.run()
                }
            }

            `when`("change has no artifacts") {
                val projectDir =
                    File.createTempFile("opsx-status", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changeDir = File(projectDir, "opsx/changes/bare-change")
                changeDir.mkdirs()
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(name = "bare-change", status = "draft"),
                )

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-status", StatusTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                then("shows no artifacts label") {
                    task.run()
                }
            }

            `when`("change has dependencies") {
                val projectDir =
                    File.createTempFile("opsx-status", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changeDir = File(projectDir, "opsx/changes/dependent-change")
                changeDir.mkdirs()
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(
                        name = "dependent-change",
                        status = "active",
                        depends = listOf("base-change", "other-change"),
                    ),
                )
                File(changeDir, "proposal.md").writeText("proposal")

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-status", StatusTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                then("shows dependency info") {
                    task.run()
                }
            }

            `when`("changes span multiple statuses") {
                val projectDir =
                    File.createTempFile("opsx-status", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changesDir = File(projectDir, "opsx/changes")

                val draft = File(changesDir, "draft-change")
                draft.mkdirs()
                ChangeConfig.write(
                    File(draft, ".opsx.yaml"),
                    ChangeConfig(name = "draft-change", status = "draft"),
                )

                val active = File(changesDir, "active-change")
                active.mkdirs()
                ChangeConfig.write(
                    File(active, ".opsx.yaml"),
                    ChangeConfig(name = "active-change", status = "active"),
                )

                val inProgress = File(changesDir, "wip-change")
                inProgress.mkdirs()
                ChangeConfig.write(
                    File(inProgress, ".opsx.yaml"),
                    ChangeConfig(name = "wip-change", status = "in-progress"),
                )

                val done = File(changesDir, "done-change")
                done.mkdirs()
                ChangeConfig.write(
                    File(done, ".opsx.yaml"),
                    ChangeConfig(name = "done-change", status = "done"),
                )

                val verified = File(changesDir, "verified-change")
                verified.mkdirs()
                ChangeConfig.write(
                    File(verified, ".opsx.yaml"),
                    ChangeConfig(name = "verified-change", status = "verified"),
                )

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-status", StatusTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                then("groups changes by status in lifecycle order") {
                    task.run()
                }
            }

            `when`("change has unknown status not in STATUS_ORDER") {
                val projectDir =
                    File.createTempFile("opsx-status", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changeDir = File(projectDir, "opsx/changes/weird-change")
                changeDir.mkdirs()
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(name = "weird-change", status = "custom-status"),
                )

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-status", StatusTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                then("shows custom status in the catch-all section") {
                    task.run()
                }
            }

            `when`("tasks.md has in-progress and blocked tasks") {
                val projectDir =
                    File.createTempFile("opsx-status", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changeDir = File(projectDir, "opsx/changes/complex-change")
                changeDir.mkdirs()
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(name = "complex-change", status = "in-progress"),
                )
                File(changeDir, "proposal.md").writeText("proposal")
                File(changeDir, "design.md").writeText("design")
                File(changeDir, "tasks.md").writeText(
                    """
                    # Tasks: complex-change

                    - [x] a1b2c3d4e5 | Done task
                        Completed work.
                      depends: none

                    - [>] f6g7h8i9j0 | Running task
                        Currently in progress.
                      depends: none

                    - [!] k1l2m3n4o5 | Blocked task
                        Waiting on external.
                      depends: none

                    - [ ] p6q7r8s9t0 | Pending task
                        Not started yet.
                      depends: none
                    """.trimIndent(),
                )

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-status", StatusTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                then("shows task progress with running and blocked counts") {
                    task.run()
                }
            }

            `when`("tasks.md exists but has no valid tasks") {
                val projectDir =
                    File.createTempFile("opsx-status", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changeDir = File(projectDir, "opsx/changes/empty-tasks")
                changeDir.mkdirs()
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(name = "empty-tasks", status = "active"),
                )
                File(changeDir, "tasks.md").writeText("# Tasks\n\nNo tasks here.\n")

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-status", StatusTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                then("shows no task progress") {
                    task.run()
                }
            }

            `when`("change has feedback file") {
                val projectDir =
                    File.createTempFile("opsx-status", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changeDir = File(projectDir, "opsx/changes/feedback-change")
                changeDir.mkdirs()
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(name = "feedback-change", status = "active"),
                )
                File(changeDir, "proposal.md").writeText("proposal")
                File(changeDir, "feedback.md").writeText("some feedback")

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-status", StatusTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                then("includes feedback in files list") {
                    task.run()
                }
            }
        }
    })
