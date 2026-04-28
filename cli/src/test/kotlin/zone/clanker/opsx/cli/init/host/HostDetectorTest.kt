package zone.clanker.opsx.cli.init.host

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HostDetectorTest :
    FunSpec({

        test("null externalCli always returns true") {
            HostDetector.canRunExternal(Host.CLAUDE) shouldBe true
            HostDetector.canRunExternal(Host.OPENCODE) shouldBe true
        }

        test("probes PATH for external CLIs without crashing") {
            // Just verify it returns a boolean, doesn't crash
            (HostDetector.canRunExternal(Host.COPILOT) is Boolean) shouldBe true
            (HostDetector.canRunExternal(Host.CODEX) is Boolean) shouldBe true
        }
    })
