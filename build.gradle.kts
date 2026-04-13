plugins {
    id("clkx-conventions")
}

dependencies {
    implementation("zone.clanker:plugin-wrkx:0.40.0")
    implementation("zone.clanker:plugin-srcx:0.46.0")
}

gradlePlugin {
    plugins {
        register("opsx") {
            id = "zone.clanker.opsx"
            implementationClass = "zone.clanker.opsx.Opsx\$SettingsPlugin"
            displayName = "OpenSpec Workflow Plugin (opsx)"
            description = "Spec-driven development with AI agent orchestration for Gradle workspaces."
        }
    }
}
