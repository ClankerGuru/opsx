package zone.clanker.opsx.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import java.io.File

class ChangeTest :
    BehaviorSpec({
        given("Change data class") {
            val dir = File("/tmp/opsx-test/changes/my-change")
            val change =
                Change(
                    name = "my-change",
                    status = "active",
                    depends = listOf("dep-a"),
                    dir = dir,
                )

            then("name is correct") {
                change.name shouldBe "my-change"
            }

            then("status is correct") {
                change.status shouldBe "active"
            }

            then("depends is correct") {
                change.depends shouldBe listOf("dep-a")
            }

            then("dir is correct") {
                change.dir shouldBe dir
            }

            then("proposalFile points to proposal.md") {
                change.proposalFile.name shouldBe "proposal.md"
                change.proposalFile.path shouldEndWith "my-change/proposal.md"
            }

            then("designFile points to design.md") {
                change.designFile.name shouldBe "design.md"
                change.designFile.path shouldEndWith "my-change/design.md"
            }

            then("tasksFile points to tasks.md") {
                change.tasksFile.name shouldBe "tasks.md"
                change.tasksFile.path shouldEndWith "my-change/tasks.md"
            }

            then("configFile points to .opsx.yaml") {
                change.configFile.name shouldBe ".opsx.yaml"
                change.configFile.path shouldEndWith "my-change/.opsx.yaml"
            }
        }
    })
