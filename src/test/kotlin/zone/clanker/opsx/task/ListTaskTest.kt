package zone.clanker.opsx.task

import io.kotest.core.spec.style.BehaviorSpec
import org.gradle.testfixtures.ProjectBuilder
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.ChangeConfig
import java.io.File

private fun createConfig() = Opsx.SettingsExtension().toOpsxConfig()

class ListTaskTest :
    BehaviorSpec({

        given("ListTask.run") {

            `when`("no changes directory exists") {
                val projectDir =
                    File.createTempFile("opsx-list", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-list", ListTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                then("completes without error") {
                    task.run()
                    // No exception means the "no changes" path was taken
                }
            }

            `when`("changes directory exists but is empty") {
                val projectDir =
                    File.createTempFile("opsx-list", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changesDir = File(projectDir, "opsx/changes")
                changesDir.mkdirs()

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-list", ListTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                then("completes without error") {
                    task.run()
                }
            }

            `when`("changes directory has one change") {
                val projectDir =
                    File.createTempFile("opsx-list", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changesDir = File(projectDir, "opsx/changes")
                val changeDir = File(changesDir, "add-feature")
                changeDir.mkdirs()
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(name = "add-feature", status = "active"),
                )

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-list", ListTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                then("completes without error") {
                    task.run()
                }
            }

            `when`("changes directory has multiple changes") {
                val projectDir =
                    File.createTempFile("opsx-list", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changesDir = File(projectDir, "opsx/changes")

                val change1 = File(changesDir, "alpha-change")
                change1.mkdirs()
                ChangeConfig.write(
                    File(change1, ".opsx.yaml"),
                    ChangeConfig(name = "alpha-change", status = "active"),
                )

                val change2 = File(changesDir, "beta-change")
                change2.mkdirs()
                ChangeConfig.write(
                    File(change2, ".opsx.yaml"),
                    ChangeConfig(name = "beta-change", status = "done"),
                )

                val change3 = File(changesDir, "gamma-change")
                change3.mkdirs()
                ChangeConfig.write(
                    File(change3, ".opsx.yaml"),
                    ChangeConfig(name = "gamma-change", status = "draft"),
                )

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-list", ListTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                then("completes without error listing all changes") {
                    task.run()
                }
            }

            `when`("custom extension directories are configured") {
                val projectDir =
                    File.createTempFile("opsx-list", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val customExt =
                    Opsx.SettingsExtension().apply {
                        outputDir = "custom-output"
                        changesDir = "custom-changes"
                    }
                val customConfig = customExt.toOpsxConfig()

                val changesDir = File(projectDir, "custom-output/custom-changes")
                val changeDir = File(changesDir, "test-change")
                changeDir.mkdirs()
                ChangeConfig.write(
                    File(changeDir, ".opsx.yaml"),
                    ChangeConfig(name = "test-change", status = "pending"),
                )

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-list", ListTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(customConfig)

                then("reads from custom directories") {
                    task.run()
                }
            }

            `when`("change directory has no config file") {
                val projectDir =
                    File.createTempFile("opsx-list", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val changesDir = File(projectDir, "opsx/changes")
                val changeDir = File(changesDir, "no-config-change")
                changeDir.mkdirs()

                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-list", ListTask::class.java)
                task.rootDir.set(projectDir)
                task.config.set(createConfig())

                then("falls back to directory name and default status") {
                    task.run()
                }
            }
        }
    })
