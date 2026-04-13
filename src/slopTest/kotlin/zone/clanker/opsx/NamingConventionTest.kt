package zone.clanker.opsx

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.core.spec.style.BehaviorSpec

/**
 * Enforces naming conventions across the codebase.
 *
 * Every class must communicate its role through its name:
 * - Classes in `task` end with `Task` (e.g. `ApplyTask`, `ProposeTask`)
 * - Value classes in `model` use domain nouns, never generic suffixes
 *
 * Generic names like `Helper`, `Manager`, or `Util` are banned.
 * If a class needs one of these suffixes, it doesn't have a clear
 * enough responsibility.
 */
class NamingConventionTest :
    BehaviorSpec({

        val mainScope = Konsist.scopeFromSourceSet("main")
        val allClasses = mainScope.classes()

        given("task package naming") {

            `when`("top-level classes are in the task package") {
                val taskClasses =
                    allClasses
                        .filter { it.packagee?.name?.contains("opsx.task") == true }
                        .filter { it.isTopLevel }

                then("every class name ends with Task") {
                    taskClasses.assertTrue { it.name.endsWith("Task") }
                }
            }
        }

        given("forbidden class name suffixes") {

            val forbidden =
                listOf(
                    "Helper",
                    "Manager",
                    "Util",
                    "Utils",
                )

            `when`("examining all classes in main source") {
                then("no class uses a generic suffix") {
                    allClasses.assertTrue { cls ->
                        forbidden.none { suffix -> cls.name.endsWith(suffix) }
                    }
                }
            }
        }
    })
