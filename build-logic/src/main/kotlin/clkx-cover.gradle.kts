plugins {
    id("org.jetbrains.kotlinx.kover")
}

kover {
    reports {
        filters {
            excludes {
                // @TaskAction run() methods require real Gradle execution context — untestable in unit tests
                annotatedBy("org.gradle.api.tasks.TaskAction")
                // Settings plugin apply() requires real Gradle Settings — tested via registerTasks
                classes("*\$SettingsPlugin", "*\$SettingsPlugin\$*")
            }
        }
        verify {
            rule {
                minBound(90)
            }
        }
    }
}
