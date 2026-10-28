plugins {
    id("zone.clanker.build.version")
    id("clkx-app")
}

val mainClassFqn = "zone.clanker.opsx.cli.MainKt"

application {
    mainClass = mainClassFqn
    applicationName = "opsx"
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

dependencies {
    implementation(project(":cli"))
    implementation(project(":tui"))
    implementation(kotlin("stdlib"))
}

val manifestAttrs =
    mapOf(
        "Main-Class" to mainClassFqn,
        "Implementation-Title" to "opsx",
        "Implementation-Version" to project.version,
        "Implementation-Vendor" to "ClankerGuru",
        "Implementation-URL" to "https://github.com/ClankerGuru/opsx",
    )

tasks.jar {
    manifest { attributes(manifestAttrs) }
}

tasks.shadowJar {
    mergeServiceFiles()
    manifest {
        attributes(manifestAttrs)
    }
}

runtime {
    javaHome = System.getenv("JAVA_HOME")?.let { file(it).absolutePath }

    options =
        listOf(
            "--strip-debug",
            "--compress",
            "2",
            "--no-header-files",
            "--no-man-pages",
        )

    modules =
        listOf(
            "java.base",
            "java.logging",
            "java.desktop",
            "jdk.unsupported",
        )

    jpackage {
        imageName = "opsx"
    }
}
