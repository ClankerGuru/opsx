package zone.clanker.opsx

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.core.spec.style.BehaviorSpec

/**
 * Enforces directional import boundaries between packages.
 *
 * The dependency direction is:
 * ```
 * task -> model      (tasks consume models)
 * workflow -> model   (workflows consume models)
 * model -> (nothing)  (models are leaf nodes)
 * workflow -> (no tasks)
 * ```
 *
 * Models must never depend on tasks or workflow.
 * Workflow must never depend on tasks.
 * This prevents circular dependencies and keeps the model layer pure.
 */
class PackageBoundaryTest :
    BehaviorSpec({

        val mainScope = Konsist.scopeFromSourceSet("main")

        given("import direction enforcement") {

            `when`("files are in the model package") {
                val modelFiles =
                    mainScope.files.filter {
                        it.packagee?.name?.contains("opsx.model") == true
                    }

                then("models never import from the task package") {
                    modelFiles.assertTrue {
                        it.imports.none { imp -> imp.name.contains("opsx.task") }
                    }
                }

                then("models never import from the workflow package") {
                    modelFiles.assertTrue {
                        it.imports.none { imp -> imp.name.contains("opsx.workflow") }
                    }
                }
            }

            `when`("files are in the workflow package") {
                val workflowFiles =
                    mainScope.files.filter {
                        it.packagee?.name?.contains("opsx.workflow") == true
                    }

                then("workflow never imports from the task package") {
                    workflowFiles.assertTrue {
                        it.imports.none { imp -> imp.name.contains("opsx.task") }
                    }
                }
            }
        }
    })
