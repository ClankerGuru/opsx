package zone.clanker.opsx.cli.init.host

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class HostTest :
    FunSpec({

        test("fromId resolves all four hosts") {
            Host.fromId("claude") shouldBe Host.CLAUDE
            Host.fromId("copilot") shouldBe Host.COPILOT
            Host.fromId("codex") shouldBe Host.CODEX
            Host.fromId("opencode") shouldBe Host.OPENCODE
        }

        test("fromId returns null for unknown") {
            Host.fromId("unknown").shouldBeNull()
        }

        test("entries has four hosts") {
            Host.entries.size shouldBe 4
        }

        test("claude has no external cli") {
            Host.CLAUDE.externalCli.shouldBeNull()
        }

        test("copilot uses agent.md convention") {
            Host.COPILOT.filenameConvention shouldBe FilenameConvention.AGENT_MD
        }
    })
