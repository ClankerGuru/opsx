package zone.clanker.opsx.cli.nuke

import com.github.ajalt.clikt.testing.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import zone.clanker.opsx.cli.OpsxCommand

class NukeCommandTest :
    FunSpec({

        test("nuke --help shows Uninstall in description") {
            val result = OpsxCommand().test("nuke --help")
            result.statusCode shouldBe 0
            result.stdout shouldContain "Uninstall"
        }

        test("nuke --help shows keep-rc option") {
            val result = OpsxCommand().test("nuke --help")
            result.stdout shouldContain "--keep-rc"
        }

        test("nuke runs and prints done") {
            val result = OpsxCommand().test("nuke")
            result.stdout shouldContain "done"
        }

        test("nuke with keep-rc runs and prints done") {
            val result = OpsxCommand().test("nuke --keep-rc")
            result.stdout shouldContain "done"
        }

        test("nuke prints cleaned count") {
            val result = OpsxCommand().test("nuke")
            result.stdout shouldContain "cleaned"
        }
    })
