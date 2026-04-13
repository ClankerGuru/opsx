package zone.clanker.opsx.task

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import zone.clanker.opsx.model.ChangeStatus

class ArchiveTaskTest :
    BehaviorSpec({

        given("ChangeStatus.canTransitionTo(ARCHIVED)") {

            `when`("status is completed") {
                then("can transition to archived") {
                    ChangeStatus.from("completed").canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }
            }

            `when`("status is done") {
                then("can transition to archived") {
                    ChangeStatus.from("done").canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }
            }

            `when`("status is verified") {
                then("can transition to archived") {
                    ChangeStatus.from("verified").canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }
            }

            `when`("status is archived") {
                then("cannot transition to archived") {
                    ChangeStatus.from("archived").canTransitionTo(ChangeStatus.ARCHIVED) shouldBe false
                }
            }

            `when`("status is active") {
                then("can transition to archived") {
                    ChangeStatus.from("active").canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }
            }

            `when`("status is draft") {
                then("can transition to archived") {
                    ChangeStatus.from("draft").canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }
            }

            `when`("status is pending") {
                then("can transition to archived") {
                    ChangeStatus.from("pending").canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }
            }

            `when`("status is in-progress") {
                then("cannot transition to archived") {
                    ChangeStatus.from("in-progress").canTransitionTo(ChangeStatus.ARCHIVED) shouldBe false
                }
            }
        }
    })
