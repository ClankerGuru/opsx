package zone.clanker.opsx.cli

import com.github.ajalt.clikt.testing.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import zone.clanker.opsx.cli.OpsxCommand

class AllCommandsTest :
    FunSpec({

        test("root --help lists all commands") {
            val result = OpsxCommand().test("--help")
            result.statusCode shouldBe 0
            result.stdout shouldContain "init"
            result.stdout shouldContain "update"
            result.stdout shouldContain "install"
            result.stdout shouldContain "nuke"
            result.stdout shouldContain "status"
            result.stdout shouldContain "list"
            result.stdout shouldContain "log"
            result.stdout shouldContain "completion"
        }

        test("root --version prints version") {
            val result = OpsxCommand().test("--version")
            result.stdout shouldContain "opsx version"
        }

        test("root with no args prints help") {
            val result = OpsxCommand().test("")
            result.stdout shouldContain "Usage:"
        }

        test("init --help shows host option") {
            val result = OpsxCommand().test("init --help")
            result.statusCode shouldBe 0
            result.stdout shouldContain "--host"
            result.stdout shouldContain "--no-interactive"
        }

        test("update --help shows force and check") {
            val result = OpsxCommand().test("update --help")
            result.statusCode shouldBe 0
            result.stdout shouldContain "--force"
            result.stdout shouldContain "--check"
        }

        test("install --help") {
            val result = OpsxCommand().test("install --help")
            result.statusCode shouldBe 0
            result.stdout shouldContain "Install"
        }

        test("nuke --help shows keep-rc") {
            val result = OpsxCommand().test("nuke --help")
            result.statusCode shouldBe 0
            result.stdout shouldContain "--keep-rc"
        }

        test("status --help shows change and follow") {
            val result = OpsxCommand().test("status --help")
            result.statusCode shouldBe 0
            result.stdout shouldContain "--change"
            result.stdout shouldContain "--follow"
            result.stdout shouldContain "--archive"
        }

        test("list --help shows archive and only") {
            val result = OpsxCommand().test("list --help")
            result.statusCode shouldBe 0
            result.stdout shouldContain "--archive"
            result.stdout shouldContain "--only"
        }

        test("log --help shows required options") {
            val result = OpsxCommand().test("log --help")
            result.statusCode shouldBe 0
            result.stdout shouldContain "--agent"
            result.stdout shouldContain "--state"
            result.stdout shouldContain "--change"
            result.stdout shouldContain "--task"
        }

        test("log fails without required options") {
            val result = OpsxCommand().test("log")
            result.statusCode shouldBe 1
        }

        test("log fails without change") {
            val result = OpsxCommand().test("log --agent lead --state start desc")
            result.statusCode shouldBe 2
        }

        test("completion bash produces script") {
            val result = OpsxCommand().test("completion bash")
            result.statusCode shouldBe 0
            result.stdout shouldContain "_opsx"
            result.stdout shouldContain "complete"
        }

        test("completion zsh produces script") {
            val result = OpsxCommand().test("completion zsh")
            result.statusCode shouldBe 0
            result.stdout shouldContain "#compdef"
            result.stdout shouldContain "_opsx"
        }

        test("completion fish produces script") {
            val result = OpsxCommand().test("completion fish")
            result.statusCode shouldBe 0
            result.stdout shouldContain "complete -c opsx"
        }

        test("completion without shell arg fails") {
            val result = OpsxCommand().test("completion")
            result.statusCode shouldBe 1
        }

        test("list with no changes shows message") {
            val result = OpsxCommand().test("list")
            result.statusCode shouldBe 0
            result.stdout shouldContain "no changes found"
        }
    })
