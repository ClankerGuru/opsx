plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${embeddedKotlinVersion}")
    implementation("org.jetbrains.kotlin:kotlin-serialization:${embeddedKotlinVersion}")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.1")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.8")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:12.2.0")
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.0.0-beta13")
    implementation("org.beryx:badass-runtime-plugin:2.0.1")
}

gradlePlugin {
    plugins {
        register("clkx-settings") {
            id = "clkx-settings"
            implementationClass = "zone.clanker.gradle.conventions.ClkxSettingsPlugin"
        }
    }
}
