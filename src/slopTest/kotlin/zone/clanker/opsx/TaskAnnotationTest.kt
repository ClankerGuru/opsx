package zone.clanker.opsx

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.core.spec.style.BehaviorSpec

/**
 * Enforces that all Gradle task classes carry required caching annotations.
 *
 * Every concrete task in opsx must declare either `@DisableCachingByDefault`
 * or `@CacheableTask` to make caching behavior explicit and prevent
 * ambiguous up-to-date checks.
 */
class TaskAnnotationTest :
    BehaviorSpec({

        val mainScope = Konsist.scopeFromSourceSet("main")

        given("task classes require a caching annotation") {

            `when`("classes in the task package end with Task") {
                val taskClasses =
                    mainScope
                        .classes()
                        .filter { it.packagee?.name?.contains("opsx.task") == true }
                        .withNameEndingWith("Task")

                then("every task class has @DisableCachingByDefault or @CacheableTask") {
                    taskClasses.assertTrue {
                        it.annotations.any { ann ->
                            ann.name == "DisableCachingByDefault" || ann.name == "CacheableTask"
                        }
                    }
                }
            }
        }
    })
