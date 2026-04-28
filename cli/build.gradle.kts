plugins {
    id("clkx-cli")
}

version = providers.gradleProperty("VERSION_NAME").getOrElse("0.0.0-dev")

val generateBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildconfig")
    val ver = project.version.toString()
    outputs.dir(outputDir)
    inputs.property("version", ver)
    doLast {
        val dir = outputDir.get().dir("zone/clanker/opsx/cli").asFile
        dir.mkdirs()
        dir.resolve("BuildConfig.kt").writeText(
            """
            |package zone.clanker.opsx.cli
            |
            |object BuildConfig {
            |    const val VERSION: String = "$ver"
            |}
            |
            """.trimMargin(),
        )
    }
}

sourceSets.main { kotlin.srcDir(generateBuildConfig) }

dependencies {
    api(libs.clikt)
    api(libs.mordant)
    runtimeOnly(libs.mordant.jvm.jna)
    implementation(libs.kotlinx.serialization.json)
    api(libs.kotlinx.io.core)

    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
}
