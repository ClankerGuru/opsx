import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    id("org.jetbrains.kotlinx.kover")
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "*MainKt",
                    "*InstallCommand",
                    "*NukeCommand",
                    "*InitCommand",
                    "*UpdateCommand",
                    "*CompletionCommand",
                    "*StatusCommand",
                    "*ListCommand",
                    "*OnboardingGuide*",
                )
            }
        }
        verify {
            rule {
                minBound(90, CoverageUnit.LINE, AggregationType.COVERED_PERCENTAGE)
            }
        }
    }
}
