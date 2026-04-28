package zone.clanker.opsx.cli

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ExitCodeTest :
    FunSpec({

        test("exit codes have expected values") {
            ExitCode.SUCCESS shouldBe 0
            ExitCode.FAILURE shouldBe 1
            ExitCode.USAGE_ERROR shouldBe 2
            ExitCode.CHANGE_NOT_FOUND shouldBe 10
            ExitCode.CHANGE_WRONG_STATE shouldBe 11
            ExitCode.HOST_EMISSION_FAILED shouldBe 20
            ExitCode.HOST_EXTERNAL_COMMAND_FAILED shouldBe 21
            ExitCode.NOT_AN_OPSX_PROJECT shouldBe 30
            ExitCode.SELF_UPDATE_NETWORK_FAILURE shouldBe 40
            ExitCode.SELF_UPDATE_CHECKSUM_MISMATCH shouldBe 41
        }
    })
