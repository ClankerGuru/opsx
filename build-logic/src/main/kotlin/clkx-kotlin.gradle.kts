plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = providers.gradleProperty("GROUP").getOrElse("zone.clanker")

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.test {
    useJUnitPlatform()
}
