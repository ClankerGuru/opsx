package zone.clanker.opsx.cli.status

import com.github.ajalt.clikt.testing.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import zone.clanker.opsx.cli.OpsxCommand

class StatusCommandTest :
    FunSpec({

        test("status --help shows correct description") {
            val result = OpsxCommand().test("status --help")
            result.statusCode shouldBe 0
            result.stdout shouldContain "ledger"
        }

        test("status --change with nonexistent change shows no activity") {
            // When run via clikt test(), terminal is not interactive so
            // OnboardingGuide won't launch — it will fall through or error.
            // This test verifies the --change path works.
            val result = OpsxCommand().test("status --change nonexistent")
            result.statusCode shouldBe 0
        }
    })
