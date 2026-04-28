package zone.clanker.opsx.cli

import com.github.ajalt.clikt.testing.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import zone.clanker.opsx.cli.OpsxCommand

class OpsxCommandTest :
    FunSpec({

        test("--help shows all commands") {
            val result = OpsxCommand().test("--help")
            result.statusCode shouldBe 0
            result.stdout shouldContain "init"
            result.stdout shouldContain "install"
            result.stdout shouldContain "nuke"
            result.stdout shouldContain "update"
            result.stdout shouldContain "status"
            result.stdout shouldContain "list"
            result.stdout shouldContain "log"
            result.stdout shouldContain "completion"
        }

        test("--version prints version") {
            val result = OpsxCommand().test("--version")
            result.stdout shouldContain "opsx version"
        }

        test("list with no changes produces empty output") {
            val result = OpsxCommand().test("list")
            result.statusCode shouldBe 0
        }

        test("completion requires shell argument") {
            val result = OpsxCommand().test("completion")
            result.statusCode shouldBe 1
        }

        test("completion with valid shell") {
            val result = OpsxCommand().test("completion bash")
            result.statusCode shouldBe 0
            result.stdout shouldContain "completion"
        }

        test("nuke help") {
            val result = OpsxCommand().test("nuke --help")
            result.stdout shouldContain "Uninstall"
        }

        test("install help") {
            val result = OpsxCommand().test("install --help")
            result.stdout shouldContain "Install"
        }

        test("update help") {
            val result = OpsxCommand().test("update --help")
            result.stdout shouldContain "Self-update"
        }
    })
