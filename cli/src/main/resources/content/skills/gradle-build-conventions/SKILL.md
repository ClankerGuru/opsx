---
name: gradle-build-conventions
description: Provides a build-logic convention plugin architecture (e.g. clkx-*.gradle.kts) for multi-module Gradle projects, including module, toolchain, publish, testing, detekt, ktlint, and serialization conventions. Activates when adding or modifying convention plugins or understanding shared build configuration.
---

# Gradle Build Conventions (build-logic)

> Project-specific `clkx-*` convention layout. For generic Gradle mechanisms see:
> - `/gradle` — build lifecycle, Settings/Project model
> - `/gradle-plugins-basics` — `plugins { }` DSL, `pluginManagement`, core plugin catalog
> - `/gradle-custom-plugins` — precompiled script plugins, `build-logic` as an included build, `java-gradle-plugin`, `gradlePlugin { }`
> - `/gradle-tasks` — task registration, input/output annotations
> - `/gradle-providers-properties` — lazy API used by convention wiring
> - `/gradle-settings-plugin` — sibling pattern for settings-targeted plugins

## Rules

1. **Convention plugins live in `build-logic/src/main/kotlin/`.** Each file is a precompiled script plugin named `clkx-*.gradle.kts`. They are automatically compiled into plugin classes by the `kotlin-dsl` plugin applied in `build-logic/build.gradle.kts`.

2. **The root project's `settings.gradle.kts` includes build-logic as a pluginManagement build:**
   ```kotlin
   pluginManagement {
       includeBuild("build-logic")
   }
   ```

3. **Convention plugins are applied by ID in `build.gradle.kts`.** The file `clkx-foo.gradle.kts` becomes plugin ID `clkx-foo`:
   ```kotlin
   plugins {
       id("clkx-conventions")  // the meta-convention that aggregates all others
   }
   ```

4. **`clkx-conventions` is the aggregator.** It applies all standard conventions for a complete plugin project:
   ```kotlin
   // clkx-conventions.gradle.kts
   plugins {
       id("clkx-module")
       id("clkx-toolchain")
       id("clkx-plugin")
       id("clkx-publish")
       id("clkx-testing")
       id("clkx-detekt")
       id("clkx-ktlint")
   }
   ```

5. **Each convention has a single responsibility:**

   | Plugin | Purpose |
   |--------|---------|
   | `clkx-module` | `java-library` + `kotlin("jvm")`, group, gradleApi(), JUnit5, sign-before-publish |
   | `clkx-toolchain` | Java 17 toolchain with JetBrains vendor |
   | `clkx-plugin` | `java-gradle-plugin` with relaxed validation |
   | `clkx-publish` | Maven Central via vanniktech with empty javadoc jar |
   | `clkx-testing` | Kotest + TestContainers + GradleTestKit + Kover coverage (90% min) + slopTest source set |
   | `clkx-detekt` | Detekt with `config/detekt.yml`, build upon defaults, parallel |
   | `clkx-ktlint` | Ktlint 1.5.0 |
   | `clkx-serialization` | `kotlin("plugin.serialization")` + kotlinx-serialization-json |

6. **`clkx-settings` is a compiled plugin (not precompiled script).** It lives in `build-logic/src/main/kotlin/{group}/gradle/conventions/ClkxSettingsPlugin.kt` and is registered in `build-logic/build.gradle.kts`:
   ```kotlin
   gradlePlugin {
       plugins {
           register("clkx-settings") {
               id = "clkx-settings"
               implementationClass = "com.example.gradle.conventions.ClkxSettingsPlugin"
           }
       }
   }
   ```
   It applies the foojay toolchain resolver and configures `dependencyResolutionManagement` with `FAIL_ON_PROJECT_REPOS`.

7. **The `slopTest` source set is for architecture tests.** It uses Konsist to enforce naming conventions, forbidden patterns, package boundaries, and task placement. It runs as part of `check`:
   ```kotlin
   val slopTest by sourceSets.creating { ... }
   val slopTask = tasks.register<Test>("slopTest") {
       description = "Run slop taste tests -- architecture, naming, boundaries, style"
       group = "verification"
       testClassesDirs = slopTest.output.classesDirs
       classpath = slopTest.runtimeClasspath
       useJUnitPlatform()
   }
   tasks.named("check") { dependsOn(slopTask) }
   ```

8. **Version catalog is NOT used.** Dependencies are declared directly in `build-logic/build.gradle.kts` with version strings. Convention plugins use string-based configurations:
   ```kotlin
   dependencies {
       "testImplementation"("io.kotest:kotest-runner-junit5:5.9.1")
       "testImplementation"("io.kotest:kotest-assertions-core:5.9.1")
       "slopTestImplementation"("com.lemonappdev:konsist:0.17.3")
   }
   ```

9. **Publication uses vanniktech maven-publish-plugin.** Configure with `GradlePlugin(javadocJar = JavadocJar.Empty())`. Metadata comes from `gradle.properties`:
   ```properties
   GROUP=com.example
   VERSION_NAME=0.0.0-dev
   POM_ARTIFACT_ID=plugin-myplugin
   SONATYPE_HOST=CENTRAL_PORTAL
   ```

10. **Kover coverage minimum is 90%.** No exclusions are allowed. The verify rule is:
    ```kotlin
    kover {
        reports {
            filters {
                excludes {
                    // It is prohibited to add exclusions
                }
            }
            verify {
                rule {
                    minBound(90)
                }
            }
        }
    }
    ```

## Examples

### Adding a new convention plugin

1. Create `build-logic/src/main/kotlin/clkx-myfeature.gradle.kts`:
   ```kotlin
   plugins {
       `java-base`
   }

   // configure your feature
   ```

2. Add any required plugin dependencies to `build-logic/build.gradle.kts`:
   ```kotlin
   dependencies {
       implementation("com.example:my-plugin:1.0.0")
   }
   ```

3. Apply it from `clkx-conventions.gradle.kts` if it should be universal:
   ```kotlin
   plugins {
       id("clkx-module")
       id("clkx-toolchain")
       id("clkx-myfeature")  // add here
       // ...
   }
   ```

4. Or apply it individually from a project's `build.gradle.kts`:
   ```kotlin
   plugins {
       id("clkx-conventions")
       id("clkx-myfeature")
   }
   ```

### build-logic/build.gradle.kts structure
```kotlin
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${embeddedKotlinVersion}")
    implementation("org.jetbrains.kotlin:kotlin-serialization:${embeddedKotlinVersion}")
    implementation("com.vanniktech:gradle-maven-publish-plugin:0.36.0")
    implementation("org.gradle.toolchains:foojay-resolver:1.0.0")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.1")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:12.1.2")
}
```

## Anti-patterns

```kotlin
// WRONG: putting convention logic in the root build.gradle.kts
// All shared config belongs in build-logic/src/main/kotlin/clkx-*.gradle.kts

// WRONG: using allprojects/subprojects blocks
allprojects {
    apply(plugin = "kotlin")
}
// Correct: each project opts in via plugins { id("clkx-module") }

// WRONG: adding Kover exclusions
kover {
    reports {
        filters {
            excludes {
                classes("com.example.Generated*")  // PROHIBITED
            }
        }
    }
}

// WRONG: using version catalog references in convention plugins
// Version catalogs are not accessible from precompiled script plugins
implementation(libs.kotest.runner)
// Correct: use string coordinates
"testImplementation"("io.kotest:kotest-runner-junit5:5.9.1")

// WRONG: creating a new settings plugin as a precompiled script
// Settings script plugins have different semantics
// Use a compiled Plugin<Settings> class instead
```
