plugins {
    id("clkx-cli")
}

dependencies {
    api(project(":cli"))
    implementation(libs.mordant)
    implementation(libs.mordant.coroutines)
    implementation(libs.mordant.markdown)
    runtimeOnly(libs.mordant.jvm.jna)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
}

tasks.named("koverVerify") {
    enabled = true
}
