package zone.clanker.gradle.conventions

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.resolve.RepositoriesMode

class ClkxSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {

        settings.pluginManagement {
            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
        }

        @Suppress("UnstableApiUsage")
        settings.dependencyResolutionManagement {
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
        }
    }
}
