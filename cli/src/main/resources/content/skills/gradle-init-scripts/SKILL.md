---
name: gradle-init-scripts
description: Gradle init scripts and Plugin<Gradle> — locations, loading order, callbacks, enterprise use cases.
---

# Gradle Init Scripts

Init scripts run *before* any settings file and apply to every build on the machine (or every build in a particular invocation). They are the earliest extension point in Gradle's lifecycle — the only thing that runs before them is the Gradle daemon bootstrap itself.

Source:
- https://docs.gradle.org/current/userguide/init_scripts.html

## 1. When an init script runs

```
daemon start
  ↓
init scripts            ← you are here
  ↓
settings.gradle.kts     (pluginManagement, includeBuild, include)
  ↓
root + subproject build.gradle.kts
  ↓
task graph → execute
```

An init script sees the `Gradle` instance before any project exists. It can register settings plugins, configure repositories, add `beforeProject` hooks, inject credentials, and subscribe to build events.

## 2. Discovery locations

Gradle loads (in order):

1. Script(s) passed via `-I` / `--init-script <file>` on the command line.
2. `~/.gradle/init.gradle` or `~/.gradle/init.gradle.kts` (single file).
3. Every `.gradle` / `.gradle.kts` in `~/.gradle/init.d/` (lexicographic).
4. Every `.gradle` / `.gradle.kts` in `$GRADLE_HOME/init.d/` (tooling shipped with Gradle installation).

All that are found run. Order is: CLI scripts, then user init, then user `init.d`, then `GRADLE_HOME/init.d`.

## 3. Minimum init script

```kotlin
// ~/.gradle/init.d/repos.init.gradle.kts
allprojects {
    repositories {
        mavenCentral()
    }
}
```

This hooks every project across every build on the machine.

## 4. Shape of an init script

An init script is evaluated against the `Gradle` object. Top-level declarations become calls on that instance.

```kotlin
// ~/.gradle/init.d/enterprise.init.gradle.kts
initscript {
    repositories { gradlePluginPortal() }
    dependencies { classpath("com.gradle:develocity-gradle-plugin:3.18") }
}

beforeSettings {
    pluginManager.apply(com.gradle.develocity.agent.gradle.DevelocityPlugin::class.java)
    extensions.configure<com.gradle.develocity.agent.gradle.DevelocityConfiguration> {
        server.set("https://ge.example.com")
        buildScan.publishing.onlyIf { false }
    }
}
```

`initscript { }` is the init-script equivalent of `buildscript { }` — it adds dependencies to the init-script classpath.

## 5. `Plugin<Gradle>` — binary init plugins

An init script can stay tiny by loading a proper `Plugin<Gradle>`:

```kotlin
// in a jar on initscript classpath
class EnterpriseInitPlugin : Plugin<Gradle> {
    override fun apply(target: Gradle) {
        target.beforeSettings { /* ... */ }
        target.settingsEvaluated { /* ... */ }
        target.beforeProject { project ->
            project.repositories { mavenCentral() }
        }
        target.projectsEvaluated { /* all projects configured */ }
    }
}

// ~/.gradle/init.d/enterprise.init.gradle.kts
initscript {
    dependencies {
        classpath(files("/opt/enterprise-init/enterprise-init.jar"))
    }
}
apply<EnterpriseInitPlugin>()
```

## 6. Callback surface on `Gradle`

| Callback                  | Fires                                         | Useful for                                  |
|---------------------------|-----------------------------------------------|---------------------------------------------|
| `beforeSettings { s -> }` | Before `settings.gradle.kts` evaluates        | Register settings plugins, tweak `pluginManagement` |
| `settingsEvaluated { s -> }` | After settings file, before projects created | Inspect `s.includedBuilds`, resolved plugins |
| `projectsLoaded { g -> }` | After project tree is built                   | Iterate all projects before evaluation      |
| `beforeProject { p -> }`  | Before each project's build script runs       | Inject repositories, apply plugins          |
| `afterProject { p -> }`   | After each project's build script runs        | Post-process configured projects            |
| `projectsEvaluated { g -> }` | After all project build scripts evaluated  | Build-level cross-project wiring            |
| `buildFinished { r -> }`  | After execution                               | Logging, reporting (prefer `BuildService` + `BuildEventsListenerRegistry` for CC) |

`beforeProject` is the canonical hook for injecting repositories, applying conventions, or enforcing policy without editing the target build.

## 7. Common use cases

### Organisation-wide repositories

```kotlin
beforeSettings {
    pluginManagement {
        repositories {
            maven("https://artifactory.example.com/gradle-plugins")
            gradlePluginPortal()
        }
    }
}
allprojects {
    repositories {
        maven("https://artifactory.example.com/maven")
    }
}
```

### Credentials injection

```kotlin
allprojects {
    repositories {
        maven("https://artifactory.example.com/private") {
            credentials {
                username = System.getenv("ARTIFACTORY_USER")
                password = System.getenv("ARTIFACTORY_TOKEN")
            }
        }
    }
}
```

Keep credentials in env vars or a keychain — don't hard-code.

### Force Develocity / build scans

```kotlin
beforeSettings {
    pluginManager.apply("com.gradle.develocity")
    extensions.configure<com.gradle.develocity.agent.gradle.DevelocityConfiguration> {
        server.set("https://ge.example.com")
    }
}
```

Ensures every build reports scans without each project opting in.

### Global dependency rules

```kotlin
allprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "log4j") useTarget("org.apache.logging.log4j:log4j-core:2.24.1")
        }
    }
}
```

### CI-only tweaks

```kotlin
if (System.getenv("CI") != null) {
    allprojects {
        tasks.withType<Test>().configureEach {
            maxParallelForks = 4
            retry.maxRetries.set(2)
        }
    }
}
```

## 8. Init vs settings vs project plugin

| Goal                                      | Use                                    |
|-------------------------------------------|----------------------------------------|
| Apply to every build on this machine      | Init script                            |
| Apply to this repo's builds only          | Settings plugin in `build-logic`       |
| Apply to one module                       | Project plugin / convention plugin     |
| Decide which plugins resolve where        | `pluginManagement` in `settings.gradle.kts` |
| Inject credentials for Artifactory        | Init script                            |
| Enforce Kotlin toolchain across projects  | Convention plugin in `build-logic`     |

Rule: put it at the narrowest scope that works. A convention plugin in `build-logic` is version-controlled and reviewable; an init script on one developer's laptop is invisible to everyone else.

## 9. Distribution patterns

**Per-developer** — they copy `foo.init.gradle.kts` into `~/.gradle/init.d/`. Fragile.

**Per-company** — ship an installer or a dotfiles repo that writes to `~/.gradle/init.d/`.

**Per-invocation** — CI uses `-I ci.init.gradle.kts` for CI-specific config (retries, scan publishing, mirror repos). Keeps CI concerns out of developer machines.

```bash
./gradlew build -I ci.init.gradle.kts
```

## 10. Configuration cache

Init scripts run at configuration time and their logic is part of the cache key. Rules:

- `beforeSettings` / `settingsEvaluated` / `beforeProject` closures run on first configuration; the CC then skips them on subsequent runs.
- Don't capture mutable state in those closures — they can be serialised.
- Use `providers.environmentVariable(...)` instead of `System.getenv(...)` so the CC invalidates correctly when env changes.
- `buildFinished { }` is not CC-compatible. Use `BuildEventsListenerRegistry` from a `Plugin<Gradle>` instead:

```kotlin
class MyInit : Plugin<Gradle> {
    override fun apply(target: Gradle) {
        val registry = target.sharedServices.registerIfAbsent(
            "telemetry", TelemetryService::class.java
        ) {}
        target.rootProject { project ->
            project.extensions.getByType<BuildEventsListenerRegistry>()
                .onTaskCompletion(registry)
        }
    }
}
```

## 11. Debugging

```bash
./gradlew --info build 2>&1 | head -40
```

The info log lists every init script that was evaluated. If a script isn't firing, check:
- File extension is `.gradle` or `.gradle.kts`.
- Located in `~/.gradle/init.d/`, not `~/.gradle/init-scripts/` or similar.
- Kotlin DSL script reports its compilation errors in `--info` — silent failures are rare.

## 12. Anti-patterns

- Using init scripts for project-specific logic — invisible to anyone who hasn't installed them. Put it in `build-logic`.
- Hard-coded credentials in `~/.gradle/init.gradle.kts` — use env vars or a credential helper.
- `gradle.buildFinished { }` in an init script — not CC-compatible. Use `BuildEventsListenerRegistry`.
- Heavy configuration in `beforeProject` without guarding on plugins — every project pays the cost. Use `project.pluginManager.withPlugin("...")` to be reactive.
- Overriding `pluginManagement.repositories` in an init script when the project's `settings.gradle.kts` also configures them — confusing precedence. Prefer to centralise in settings.
- Loading plugins from a local filesystem path (`classpath(files("/opt/..."))`) — non-reproducible. Publish to an internal Maven repo.

## 13. Sub-skill cross-reference

- `Plugin<Settings>` patterns → `/gradle-custom-plugins`
- `BuildEventsListenerRegistry` → `/gradle-dependency-injection`
- `pluginManagement` block → `/gradle-plugins-basics`
- Build lifecycle overview → `/gradle`
