plugins {
    `java-base`
}

val sourceSets = the<SourceSetContainer>()

val slopTest by sourceSets.creating {
    compileClasspath += sourceSets["main"].output
    runtimeClasspath += sourceSets["main"].output
}

configurations[slopTest.implementationConfigurationName].extendsFrom(configurations["testImplementation"])
configurations[slopTest.runtimeOnlyConfigurationName].extendsFrom(configurations["testRuntimeOnly"])

dependencies {
    "slopTestImplementation"("com.lemonappdev:konsist:0.17.3")
    "slopTestImplementation"("io.kotest:kotest-runner-junit5:5.9.1")
    "slopTestImplementation"("io.kotest:kotest-assertions-core:5.9.1")
}

val slopTask = tasks.register<Test>("slopTest") {
    description = "Run architecture tests — package boundaries, naming, style"
    group = "verification"
    testClassesDirs = slopTest.output.classesDirs
    classpath = slopTest.runtimeClasspath
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(slopTask)
}
