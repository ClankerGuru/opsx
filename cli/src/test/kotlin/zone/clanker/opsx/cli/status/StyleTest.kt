package zone.clanker.opsx.cli.status

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class StyleTest :
    FunSpec({

        test("lifecycle order has 7 statuses") {
            Style.lifecycleOrder.size shouldBe 7
        }

        test("badge contains status name") {
            for (status in Style.lifecycleOrder) {
                Style.badge(status) shouldContain status.replace("-", "-")
            }
        }

        test("progress bar at zero") {
            val bar = Style.progressBar(0, 10)
            bar shouldContain "0%"
            bar shouldContain "(0/10)"
        }

        test("progress bar at full") {
            val bar = Style.progressBar(10, 10)
            bar shouldContain "100%"
            bar shouldContain "(10/10)"
        }

        test("progress bar handles zero total") {
            val bar = Style.progressBar(0, 0)
            bar shouldContain "0%"
        }

        test("elapsed formats seconds") {
            Style.elapsed(30) shouldBe "30s"
        }

        test("elapsed formats minutes") {
            Style.elapsed(90) shouldBe "1m 30s"
        }

        test("elapsed formats hours") {
            Style.elapsed(3661) shouldBe "1h 1m"
        }

        test("agent colors cover all known agents") {
            val agents = listOf("lead", "scout", "forge", "developer", "qa", "architect", "devOps")
            for (agent in agents) {
                Style.agentColors.containsKey(agent) shouldBe true
            }
        }
    })
