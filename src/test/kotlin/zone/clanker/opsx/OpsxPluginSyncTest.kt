package zone.clanker.opsx

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testfixtures.ProjectBuilder
import zone.clanker.opsx.skill.TaskInfo
import zone.clanker.opsx.task.CleanTask
import zone.clanker.opsx.task.SyncTask
import java.io.File

class OpsxPluginSyncTest :
    BehaviorSpec({
        given("SyncTask with empty agents") {
            `when`("run is called with no agents configured") {
                val tempDir =
                    File.createTempFile("opsx-sync-empty-agents", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }

                val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
                val task = project.tasks.create("test-sync-empty-agents", SyncTask::class.java)
                task.rootDir.set(tempDir)
                task.taskInfos.set(listOf(TaskInfo("opsx-list", "opsx", "List changes")))
                task.includedBuildNames.set(emptyList())
                task.includedBuildDirs.set(emptyList())
                task.agents.set(emptyList())
                task.skillDirectories.set(emptyList())
                task.agentDirectories.set(emptyList())
                task.run()

                then("does not generate skill files") {
                    File(tempDir, ".clkx/skills").exists() shouldBe false
                }

                then("does not generate instruction files") {
                    File(tempDir, "CLAUDE.md").exists() shouldBe false
                    File(tempDir, "AGENTS.md").exists() shouldBe false
                }
            }
        }

        given("SyncTask @TaskAction") {
            `when`("run is called") {
                val tempDir =
                    File.createTempFile("opsx-inner-sync", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val origHome = System.getProperty("user.home")
                System.setProperty("user.home", tempDir.absolutePath)

                val project = ProjectBuilder.builder().withProjectDir(tempDir).build()

                val task = project.tasks.create("test-inner-sync", SyncTask::class.java)
                task.rootDir.set(tempDir)
                task.taskInfos.set(listOf(TaskInfo("opsx-test-sync-skill", "opsx", "Test sync skill")))
                task.includedBuildNames.set(emptyList())
                task.includedBuildDirs.set(emptyList())
                task.agents.set(listOf("claude"))
                task.skillDirectories.set(emptyList())
                task.agentDirectories.set(emptyList())
                task.run()

                System.setProperty("user.home", origHome)

                then("generates skill files and .gitignore") {
                    val skillsDir = File(tempDir, ".clkx/skills")
                    skillsDir.exists() shouldBe true
                    val gitignore = File(skillsDir, ".gitignore")
                    gitignore.exists() shouldBe true
                    gitignore.readText() shouldContain "*"
                }
            }
        }

        given("CleanTask @TaskAction") {
            `when`("nothing to clean") {
                val projectDir =
                    File.createTempFile("opsx-inner-clean", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                val task = project.tasks.create("test-inner-clean", CleanTask::class.java)
                task.rootDir.set(projectDir)
                task.includedBuildDirs.set(emptyList())

                then("completes without error") {
                    task.run()
                }
            }

            `when`("skills and srcx directories exist") {
                val tempDir =
                    File.createTempFile("opsx-inner-clean2", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }
                val origHome = System.getProperty("user.home")
                System.setProperty("user.home", tempDir.absolutePath)

                val skillsDir = File(tempDir, ".clkx/skills")
                skillsDir.mkdirs()
                File(skillsDir, "test.md").writeText("test")

                val srcxDir = File(tempDir, ".srcx")
                srcxDir.mkdirs()
                File(srcxDir, "context.md").writeText("context")

                val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
                val task = project.tasks.create("test-inner-clean2", CleanTask::class.java)
                task.rootDir.set(tempDir)
                task.includedBuildDirs.set(emptyList())
                task.run()

                System.setProperty("user.home", origHome)

                then("cleans skills directory") {
                    skillsDir.exists() shouldBe false
                }

                then("cleans srcx directory") {
                    srcxDir.exists() shouldBe false
                }
            }

            `when`("instruction files have markers") {
                val tempDir =
                    File.createTempFile("opsx-inner-clean3", "").apply {
                        delete()
                        mkdirs()
                        deleteOnExit()
                    }

                val claudeMd = File(tempDir, "CLAUDE.md")
                claudeMd.writeText("# Proj\n<!-- OPSX:AUTO -->\ngen\n<!-- /OPSX:AUTO -->\n")

                val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
                val task = project.tasks.create("test-inner-clean3", CleanTask::class.java)
                task.rootDir.set(tempDir)
                task.includedBuildDirs.set(emptyList())
                task.run()

                then("removes markers from instruction files") {
                    val content = claudeMd.readText()
                    (content.contains("<!-- OPSX:AUTO -->")) shouldBe false
                    content shouldContain "# Proj"
                }
            }
        }
    })
