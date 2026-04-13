package zone.clanker.opsx.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class TaskDefinitionTest :
    BehaviorSpec({

        given("TaskDefinition") {
            `when`("created with all fields") {
                val task =
                    TaskDefinition(
                        id = "a1b2c3d4e5",
                        name = "Test ChangeConfig.parse",
                        description = "Write a Kotest test for parse method.",
                        status = TaskStatus.TODO,
                        dependencies = listOf("x9y8z7w6v5"),
                    )

                then("fields are accessible") {
                    task.id shouldBe "a1b2c3d4e5"
                    task.name shouldBe "Test ChangeConfig.parse"
                    task.description shouldBe "Write a Kotest test for parse method."
                    task.status shouldBe TaskStatus.TODO
                    task.dependencies shouldBe listOf("x9y8z7w6v5")
                }
            }

            `when`("created with no dependencies") {
                val task =
                    TaskDefinition(
                        id = "a1b2c3d4e5",
                        name = "Standalone task",
                        description = "No deps.",
                        status = TaskStatus.DONE,
                        dependencies = emptyList(),
                    )

                then("dependencies list is empty") {
                    task.dependencies shouldBe emptyList()
                }
            }
        }
    })
