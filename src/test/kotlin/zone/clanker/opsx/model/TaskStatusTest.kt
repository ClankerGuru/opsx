package zone.clanker.opsx.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class TaskStatusTest :
    BehaviorSpec({

        given("TaskStatus.fromSymbol") {
            `when`("symbol is space") {
                then("returns TODO") {
                    TaskStatus.fromSymbol(' ') shouldBe TaskStatus.TODO
                }
            }
            `when`("symbol is >") {
                then("returns IN_PROGRESS") {
                    TaskStatus.fromSymbol('>') shouldBe TaskStatus.IN_PROGRESS
                }
            }
            `when`("symbol is x") {
                then("returns DONE") {
                    TaskStatus.fromSymbol('x') shouldBe TaskStatus.DONE
                }
            }
            `when`("symbol is !") {
                then("returns BLOCKED") {
                    TaskStatus.fromSymbol('!') shouldBe TaskStatus.BLOCKED
                }
            }
            `when`("symbol is ~") {
                then("returns SKIPPED") {
                    TaskStatus.fromSymbol('~') shouldBe TaskStatus.SKIPPED
                }
            }
            `when`("symbol is unknown") {
                then("defaults to TODO") {
                    TaskStatus.fromSymbol('?') shouldBe TaskStatus.TODO
                }
            }
        }

        given("TaskStatus.symbol") {
            then("each status has correct symbol") {
                TaskStatus.TODO.symbol shouldBe ' '
                TaskStatus.IN_PROGRESS.symbol shouldBe '>'
                TaskStatus.DONE.symbol shouldBe 'x'
                TaskStatus.BLOCKED.symbol shouldBe '!'
                TaskStatus.SKIPPED.symbol shouldBe '~'
            }
        }
    })
