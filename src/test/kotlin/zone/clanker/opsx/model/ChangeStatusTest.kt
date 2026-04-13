package zone.clanker.opsx.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ChangeStatusTest :
    BehaviorSpec({
        given("ChangeStatus.from") {
            `when`("called with valid values") {
                then("returns correct enum for draft") {
                    ChangeStatus.from("draft") shouldBe ChangeStatus.DRAFT
                }

                then("returns correct enum for active") {
                    ChangeStatus.from("active") shouldBe ChangeStatus.ACTIVE
                }

                then("returns correct enum for in-progress") {
                    ChangeStatus.from("in-progress") shouldBe ChangeStatus.IN_PROGRESS
                }

                then("returns correct enum for completed") {
                    ChangeStatus.from("completed") shouldBe ChangeStatus.COMPLETED
                }

                then("returns correct enum for verified") {
                    ChangeStatus.from("verified") shouldBe ChangeStatus.VERIFIED
                }

                then("returns correct enum for archived") {
                    ChangeStatus.from("archived") shouldBe ChangeStatus.ARCHIVED
                }

                then("returns correct enum for done") {
                    ChangeStatus.from("done") shouldBe ChangeStatus.DONE
                }

                then("returns correct enum for pending") {
                    ChangeStatus.from("pending") shouldBe ChangeStatus.PENDING
                }
            }

            `when`("called with unknown value") {
                then("returns ACTIVE") {
                    ChangeStatus.from("unknown") shouldBe ChangeStatus.ACTIVE
                }
            }
        }

        given("ChangeStatus.canTransitionTo") {
            `when`("status is DRAFT") {
                then("can transition to ACTIVE") {
                    ChangeStatus.DRAFT.canTransitionTo(ChangeStatus.ACTIVE) shouldBe true
                }

                then("can transition to ARCHIVED") {
                    ChangeStatus.DRAFT.canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }

                then("cannot transition to IN_PROGRESS") {
                    ChangeStatus.DRAFT.canTransitionTo(ChangeStatus.IN_PROGRESS) shouldBe false
                }

                then("cannot transition to COMPLETED") {
                    ChangeStatus.DRAFT.canTransitionTo(ChangeStatus.COMPLETED) shouldBe false
                }

                then("cannot transition to VERIFIED") {
                    ChangeStatus.DRAFT.canTransitionTo(ChangeStatus.VERIFIED) shouldBe false
                }
            }

            `when`("status is ACTIVE") {
                then("can transition to IN_PROGRESS") {
                    ChangeStatus.ACTIVE.canTransitionTo(ChangeStatus.IN_PROGRESS) shouldBe true
                }

                then("can transition to ARCHIVED") {
                    ChangeStatus.ACTIVE.canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }

                then("cannot transition to DRAFT") {
                    ChangeStatus.ACTIVE.canTransitionTo(ChangeStatus.DRAFT) shouldBe false
                }

                then("cannot transition to COMPLETED") {
                    ChangeStatus.ACTIVE.canTransitionTo(ChangeStatus.COMPLETED) shouldBe false
                }
            }

            `when`("status is IN_PROGRESS") {
                then("can transition to COMPLETED") {
                    ChangeStatus.IN_PROGRESS.canTransitionTo(ChangeStatus.COMPLETED) shouldBe true
                }

                then("can transition to ACTIVE") {
                    ChangeStatus.IN_PROGRESS.canTransitionTo(ChangeStatus.ACTIVE) shouldBe true
                }

                then("cannot transition to DRAFT") {
                    ChangeStatus.IN_PROGRESS.canTransitionTo(ChangeStatus.DRAFT) shouldBe false
                }

                then("cannot transition to ARCHIVED") {
                    ChangeStatus.IN_PROGRESS.canTransitionTo(ChangeStatus.ARCHIVED) shouldBe false
                }
            }

            `when`("status is COMPLETED") {
                then("can transition to VERIFIED") {
                    ChangeStatus.COMPLETED.canTransitionTo(ChangeStatus.VERIFIED) shouldBe true
                }

                then("can transition to ARCHIVED") {
                    ChangeStatus.COMPLETED.canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }

                then("cannot transition to DRAFT") {
                    ChangeStatus.COMPLETED.canTransitionTo(ChangeStatus.DRAFT) shouldBe false
                }

                then("cannot transition to ACTIVE") {
                    ChangeStatus.COMPLETED.canTransitionTo(ChangeStatus.ACTIVE) shouldBe false
                }
            }

            `when`("status is VERIFIED") {
                then("can transition to ARCHIVED") {
                    ChangeStatus.VERIFIED.canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }

                then("cannot transition to DRAFT") {
                    ChangeStatus.VERIFIED.canTransitionTo(ChangeStatus.DRAFT) shouldBe false
                }

                then("cannot transition to ACTIVE") {
                    ChangeStatus.VERIFIED.canTransitionTo(ChangeStatus.ACTIVE) shouldBe false
                }

                then("cannot transition to IN_PROGRESS") {
                    ChangeStatus.VERIFIED.canTransitionTo(ChangeStatus.IN_PROGRESS) shouldBe false
                }

                then("cannot transition to COMPLETED") {
                    ChangeStatus.VERIFIED.canTransitionTo(ChangeStatus.COMPLETED) shouldBe false
                }
            }

            `when`("status is DONE") {
                then("can transition to VERIFIED") {
                    ChangeStatus.DONE.canTransitionTo(ChangeStatus.VERIFIED) shouldBe true
                }

                then("can transition to ARCHIVED") {
                    ChangeStatus.DONE.canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }

                then("cannot transition to DRAFT") {
                    ChangeStatus.DONE.canTransitionTo(ChangeStatus.DRAFT) shouldBe false
                }
            }

            `when`("status is PENDING") {
                then("can transition to ACTIVE") {
                    ChangeStatus.PENDING.canTransitionTo(ChangeStatus.ACTIVE) shouldBe true
                }

                then("can transition to ARCHIVED") {
                    ChangeStatus.PENDING.canTransitionTo(ChangeStatus.ARCHIVED) shouldBe true
                }

                then("cannot transition to IN_PROGRESS") {
                    ChangeStatus.PENDING.canTransitionTo(ChangeStatus.IN_PROGRESS) shouldBe false
                }
            }

            `when`("status is ARCHIVED") {
                then("cannot transition to DRAFT") {
                    ChangeStatus.ARCHIVED.canTransitionTo(ChangeStatus.DRAFT) shouldBe false
                }

                then("cannot transition to ACTIVE") {
                    ChangeStatus.ARCHIVED.canTransitionTo(ChangeStatus.ACTIVE) shouldBe false
                }

                then("cannot transition to IN_PROGRESS") {
                    ChangeStatus.ARCHIVED.canTransitionTo(ChangeStatus.IN_PROGRESS) shouldBe false
                }

                then("cannot transition to COMPLETED") {
                    ChangeStatus.ARCHIVED.canTransitionTo(ChangeStatus.COMPLETED) shouldBe false
                }

                then("cannot transition to VERIFIED") {
                    ChangeStatus.ARCHIVED.canTransitionTo(ChangeStatus.VERIFIED) shouldBe false
                }

                then("cannot transition to ARCHIVED") {
                    ChangeStatus.ARCHIVED.canTransitionTo(ChangeStatus.ARCHIVED) shouldBe false
                }
            }
        }
    })
