---
name: gradle-plugins-basics
description: Applying Gradle plugins — plugins{} DSL, pluginManagement, resolution, core plugin catalog, settings vs project plugins.
---

# Gradle Plugins — Basics

Plugins are how Gradle grows. The core distribution ships a small kernel; everything else — Java, Kotlin, Android, publishing, Spring Boot — arrives as a plugin. This skill covers *applying* plugins. Writing them is `/gradle-custom-plugins`.

Source of truth:
- https://docs.gradle.org/current/userguide/plugins.html
- https://docs.gradle.org/current/userguide/plugin_reference.html
- https://docs.gradle.org/current/userguide/custom_plugins.html
- https://plugins.gradle.org

## 1. The `plugins {}` block

The canonical way to apply plugins. Runs before the rest of the script — it resolves the plugin, adds it to the classpath, and applies it.

```kotlin
// build.gradle.kts
plugins {
    `java-library`                                      // core plugin, no version
    kotlin("jvm") version "2.0.21"                      // Kotlin-DSL accessor
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}
```

Three forms:
- `` `java-library` `` — core plugin via Kotlin-DSL accessor. Core plugins take no version.
- `kotlin("jvm")` — typed accessor exposed by the Gradle Kotlin DSL for well-known plugins.
- `id("...") version "..."` — string ID, required for third-party plugins.

### Rules
- Must be the first block in the script (only `buildscript {}` may precede it).
- No conditional logic — `if (...) plugins { ... }` does not work.
- No variables — the block is evaluated in an isolated context.
- Version may be omitted if declared in `settings.gradle.kts` `pluginManagement`.

### `apply false`

Used in the root build of a multi-module project. Resolves the plugin (pins its version) without applying it to this project — subprojects can then apply it by ID without repeating the version.

```kotlin
// root build.gradle.kts
plugins {
    kotlin("jvm") version "2.0.21" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
}

// subproject build.gradle.kts
plugins {
    kotlin("jvm")                     // version inherited
    id("io.spring.dependency-management")
}
```

## 2. `pluginManagement` in settings

`settings.gradle.kts` is where you centralise plugin resolution:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()            // default
        mavenCentral()
        maven("https://jitpack.io")
        google()                        // for Android plugins
    }

    plugins {
        // pin versions once, reference without version in build scripts
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
        id("com.diffplug.spotless")     version "6.25.0"
    }

    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "legacy.example") {
                useModule("com.example:legacy-plugin:${requested.version}")
            }
        }
    }
}
```

Precedence for a plugin declared `id("foo")` without version:
1. `pluginManagement.plugins { }` mapping in settings.
2. Included build (composite build) exposing a plugin by that ID.
3. `gradlePluginPortal()` / declared repositories.

## 3. Version catalogs and plugins

If you use a version catalog (`gradle/libs.versions.toml`), plugins can be declared there:

```toml
[versions]
kotlin = "2.0.21"

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
shadow    = { id = "com.github.johnrengelman.shadow", version = "8.1.1" }
```

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow) apply false
}
```

Catalogs are the recommended way to share plugin versions across a multi-module project.

## 4. Legacy `buildscript {}`

Still valid; required for plugins that don't publish markers to the plugin portal.

```kotlin
buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("com.example:legacy-plugin:1.2.3")
    }
}
apply(plugin = "com.example.legacy")
```

Disadvantages: no plugin markers, no `apply false`, harder to share across subprojects, does not work with the `plugins {}` DSL.

Use only when the plugin author hasn't published a plugin marker.

## 5. Script plugins

Apply a local script as a plugin. Small, no-version-needed snippets.

```kotlin
apply(from = "gradle/reports.gradle.kts")
```

Script plugins are legacy. Prefer **precompiled script plugins** in `build-logic` (see `/gradle-custom-plugins`).

## 6. Core plugin catalog

Core plugins ship with Gradle — no repository, no version. Group them by purpose:

### JVM languages
| ID              | Purpose                                   |
|-----------------|-------------------------------------------|
| `java`          | Base Java support (superseded by `java-library` for libraries) |
| `java-library`  | Library with `api`/`implementation` split |
| `application`   | Runnable JVM app, adds `run`/`installDist`/`distZip` |
| `java-platform` | BOM-style platform of dependency constraints |
| `groovy`        | Groovy sources                            |
| `scala`         | Scala sources                             |
| `antlr`         | ANTLR grammars                            |

### Packaging & publishing
| ID               | Purpose                                   |
|------------------|-------------------------------------------|
| `distribution`   | Generic zip/tar distributions             |
| `maven-publish`  | Publish to Maven repositories             |
| `ivy-publish`    | Publish to Ivy repositories               |
| `signing`        | PGP signing of publications               |

### Native
| ID                       | Purpose                          |
|--------------------------|----------------------------------|
| `cpp-application`        | C++ executable                   |
| `cpp-library`            | C++ library                      |
| `cpp-unit-test`          | C++ tests                        |
| `swift-application`      | Swift executable                 |
| `swift-library`          | Swift library                    |
| `xctest`                 | Swift tests via XCTest           |

### Code quality
| ID           | Purpose                              |
|--------------|--------------------------------------|
| `checkstyle` | Checkstyle rules on Java            |
| `pmd`        | PMD analysis                        |
| `codenarc`   | CodeNarc for Groovy                 |
| `jacoco`     | JaCoCo code coverage                |

### IDE integration
| ID        | Purpose                    |
|-----------|----------------------------|
| `idea`    | Generate IntelliJ files    |
| `eclipse` | Generate Eclipse files     |
| `eclipse-wtp` | Eclipse WTP files      |
| `visual-studio` | Visual Studio files  |
| `xcode`   | Xcode project files        |

### Utility
| ID              | Purpose                                     |
|-----------------|---------------------------------------------|
| `base`          | Adds `clean`, `assemble`, `build` lifecycles |
| `build-init`    | `gradle init` for scaffolding               |
| `wrapper`       | `gradle wrapper` to generate the wrapper    |
| `project-report`| `projectReport`, `dependencyReport` tasks   |
| `help-tasks`    | `help`, `tasks`, `properties`, `dependencies` |
| `version-catalog` | Publish a `libs.versions.toml`            |

### Meta
| ID                          | Purpose                              |
|-----------------------------|--------------------------------------|
| `java-gradle-plugin`        | Building a Gradle plugin in Java/Kotlin |
| `groovy-gradle-plugin`      | Gradle plugin in Groovy              |
| `kotlin-dsl`                | Precompiled script plugins in Kotlin |
| `kotlin-dsl-precompiled-script-plugins` | (internal)                |

Full reference: https://docs.gradle.org/current/userguide/plugin_reference.html

## 7. Settings plugins vs project plugins

A plugin targets either `Settings` or `Project`.

```kotlin
// settings.gradle.kts
plugins {
    id("com.gradle.develocity") version "3.18"           // Settings plugin
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
```

Settings plugins apply before any project is configured — use them for:
- Develocity / build scans
- Toolchain resolution
- Included builds / composite
- Dependency resolution management (`dependencyResolutionManagement`)

Project plugins (the common case) apply inside a `build.gradle.kts`.

A plugin written to target `Settings` cannot be applied in a project script — the ID is the same, the target type differs. Check the plugin docs.

## 8. Inspecting what's applied

```bash
./gradlew :app:dependencies --configuration runtimeClasspath
./gradlew projects                                  # module tree
./gradlew help --task <taskName>                    # shows the plugin that registered it
./gradlew buildEnvironment                          # plugin classpath
```

## 9. Applying a plugin programmatically

Inside a custom plugin or convention script, avoid string IDs:

```kotlin
// precompiled script plugin
plugins {
    id("java-library")
}

// or programmatic
project.plugins.apply("java-library")
project.pluginManager.apply(JavaLibraryPlugin::class.java)
```

`pluginManager.apply` is idempotent — applying twice is a no-op.

## 10. Anti-patterns

- Mixing `buildscript {}` and `plugins {}` for the same plugin — pick one.
- Re-declaring a plugin version in every subproject — declare in the root with `apply false` or in `pluginManagement.plugins`.
- `apply(plugin = "...")` on a plugin that's already applied via `plugins {}` — redundant.
- Putting `pluginManagement` in the root build script — it only belongs in `settings.gradle.kts`.
- Depending on plugin order within `plugins {}` — Gradle doesn't guarantee it. If one plugin must see another's model, use `pluginManager.withPlugin("...") { ... }`:

```kotlin
pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }
}
```

This fires whenever that plugin applies — now or later — and is the correct idiom for cross-plugin configuration.

## 11. Sub-skill cross-reference

- Writing a plugin → `/gradle-custom-plugins`
- Settings-plugin patterns used in this project → `/gradle-settings-plugin`
- Convention plugins / `build-logic` layout → `/gradle-build-conventions`
- Tasks registered by plugins → `/gradle-tasks`
