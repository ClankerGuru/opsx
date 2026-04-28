package zone.clanker.opsx.cli.log

import com.github.ajalt.clikt.testing.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import zone.clanker.opsx.cli.OpsxCommand
import zone.clanker.opsx.cli.status.ActivityLog
import zone.clanker.opsx.cli.status.State

class LogCommandTest :
    FunSpec({

        lateinit var root: Path

        beforeEach {
            root = Path(SystemTemporaryDirectory, "opsx-log-test-${System.nanoTime()}")
            SystemFileSystem.createDirectories(Path(root, "opsx/changes/my-change"))
        }

        afterEach { java.io.File(root.toString()).deleteRecursively() }

        test("log fails without --change and no env var") {
            val result = OpsxCommand().test("log --agent lead --state start desc")
            result.statusCode shouldBe 2
        }

        test("log fails for missing change") {
            val result =
                OpsxCommand().test(
                    listOf(
                        "--root",
                        root.toString(),
                        "log",
                        "--change",
                        "nonexistent",
                        "--agent",
                        "lead",
                        "--state",
                        "start",
                        "desc",
                    ).joinToString(" "),
                )
            result.statusCode shouldBe 10
        }

        test("log appends start done and failed events under selected root") {
            val states = listOf("start" to State.START, "done" to State.DONE, "failed" to State.FAILED)

            states.forEach { (stateArg, expectedState) ->
                val result =
                    OpsxCommand().test(
                        listOf(
                            "--root",
                            root.toString(),
                            "log",
                            "--change",
                            "my-change",
                            "--agent",
                            "lead",
                            "--state",
                            stateArg,
                            "--task",
                            "t1",
                            "desc",
                        ).joinToString(" "),
                    )

                result.statusCode shouldBe 0
                result.stdout shouldContain "@lead"
                ActivityLog.read(root, "my-change").last().state shouldBe expectedState
            }

            ActivityLog.read(root, "my-change") shouldHaveSize 3
        }

        test("log without --task omits task label in output") {
            val result =
                OpsxCommand().test(
                    listOf(
                        "--root",
                        root.toString(),
                        "log",
                        "--change",
                        "my-change",
                        "--agent",
                        "dev",
                        "--state",
                        "done",
                        "done-desc",
                    ).joinToString(" "),
                )

            result.statusCode shouldBe 0
            result.stdout shouldContain "@dev"
            result.stdout shouldContain "done-desc"
        }
    })
