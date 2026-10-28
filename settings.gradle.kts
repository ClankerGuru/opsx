pluginManagement {
    includeBuild("build-logic") { name = "opsx-build-logic" }
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("clkx-settings")
}

rootProject.name = "opsx"

include(
    "app",
    "cli",
    "tui",
)
