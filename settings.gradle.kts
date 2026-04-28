pluginManagement {
    includeBuild("build-logic") { name = "opsx-build-logic" }
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
