---
name: testing-patterns
description: Reference guide for testing Kotlin Gradle plugins — generic principles (test pyramid, arrange-act-assert, determinism, fixtures) and this project's three-source-set strategy (test with Kotest+ProjectBuilder, slopTest with Konsist, functionalTest with Gradle TestKit), internal-methods pattern, createExtension helper, temp directory pattern, and the 90% thin-orchestrator coverage rule. Activates when writing tests, placing tests in the right source set, or deciding what to test.
---

# Testing Patterns

> For related topics see:
> - `/kotest` — the BehaviorSpec style used across all test source sets
> - `/kover` — coverage thresholds, filters, and the thin-orchestrator rule
> - `/konsist` — the framework used in `slopTest`
> - `/gradle` — `ProjectBuilder` and `Settings` internals
> - `/kotlin-conventions` — `internal` visibility, the tool that makes units testable
> - `/package-structure` — where the class under test lives, where its test lives

## Generic principles

### Test pyramid

Classic shape — many fast unit tests, fewer integration tests, even fewer
end-to-end tests:

```
         /\
        /E2E\         ← functionalTest (Gradle TestKit, forked JVM)
       /------\
      / integ  \      ← archTest / slopTest (Konsist, whole-module scan)
     /----------\
    /    unit    \    ← test (Kotest + ProjectBuilder)
   /--------------\
```

The pyramid is a **cost-vs-coverage** statement: unit tests are cheap
(milliseconds, isolated), so write lots of them. Functional tests are
expensive (seconds-per-test, fork a JVM), so write a handful that
exercise the full pipeline.

### Arrange / Act / Assert (Given / When / Then)

Every test has three phases:

1. **Arrange / Given** — set up inputs and collaborators.
2. **Act / When** — invoke the method under test exactly once.
3. **Assert / Then** — check outputs and observable side effects.

In Kotest BehaviorSpec, the nesting is:

```kotlin
given("a parser") {
    `when`("input is empty") {
        val result = parser.parse("")
        then("it returns an empty list") {
            result shouldBe emptyList()
        }
    }
}
```

One logical assertion per `then`. If a test needs two, split it.

### Test isolation

Tests must not share mutable state. Each `then` block sees a fresh
world — fresh project dir, fresh extension, fresh file. Shared state is
the #1 cause of flaky tests and heisenbugs that only fail in CI.

Kotest runs specs in parallel by default when configured. Write tests
that don't care about execution order.

### Determinism

Tests must produce the same result on every run. This means:

- **No `System.currentTimeMillis()`** in code under test without an
  injected clock.
- **No `Random()` without a seed** — use `Random(42)`.
- **No file system order assumptions** — `File.listFiles()` returns
  OS-dependent order; sort before asserting.
- **No network calls** — stub with local fixtures or skip the test.
- **No relying on env vars** — pass them explicitly.

When you find a flaky test, don't retry it. Fix the non-determinism.

### Naming tests

Test names describe behavior, not implementation. In Kotest:

```kotlin
// Good — behavior
then("returns empty list when input is blank") { ... }
then("propagates cancellation to child scopes") { ... }

// Bad — implementation
then("calls parseInternal with null") { ... }
then("works") { ... }
```

Backticked test function names (JUnit style) can read as natural
language: `` fun `returns null for missing key`() ``.

### Arrange with real objects, not mocks

Mocks prove your code calls `someService.foo(x)` — they don't prove
`someService.foo(x)` does the right thing. When the real object is
cheap and deterministic (data classes, pure functions, Gradle's
`ProjectBuilder`), use the real thing.

In this project, **mocking frameworks are banned**. Use:

- `ProjectBuilder.builder().build()` — real Gradle project.
- `project.objects.newInstance(Extension::class.java)` — real
  Gradle-managed extension.
- `File.createTempFile(...).apply { delete(); mkdirs() }` — real
  filesystem.

Mocks buy you nothing when the real thing is in-process and fast.

### Fixtures and builders

When several tests need a similar object, extract a builder or
fixture function. Keep fixtures **small** — a fixture that sets
fifteen fields hides which three actually matter for a given test.

```kotlin
// Named builder, defaults for most fields
private fun config(
    strict: Boolean = true,
    retry: Int = 3,
) = Config(strict = strict, retry = retry, timeout = 30.seconds, ...)
```

The test states only what differs from the default.

### Parameterized tests

Kotest's `withData` runs the same assertion over many inputs:

```kotlin
context("parseVersion") {
    withData(
        "1.0.0" to Version(1, 0, 0),
        "2.3.4" to Version(2, 3, 4),
        "10.20.30" to Version(10, 20, 30),
    ) { (input, expected) ->
        parseVersion(input) shouldBe expected
    }
}
```

Prefer this over a `forEach` in a single assertion — failures report
the specific input.

### Coverage as a tool, not a goal

Coverage tells you what was **exercised**, not what was **verified**.
A line with 100% coverage can still be buggy if the test doesn't
assert on the right thing. Treat coverage thresholds (`/kover`) as a
floor, not a target — aim to test behavior, and let coverage follow.

## This project's test source sets

| Source set | Framework | Purpose | Contributes to Kover? |
|------------|-----------|---------|----------------------|
| `test` | Kotest BehaviorSpec + ProjectBuilder | Unit tests for all internal logic | Yes |
| `slopTest` | Kotest BehaviorSpec + Konsist | Architecture enforcement (package boundaries, forbidden patterns, naming) | No |
| `functionalTest` | Kotest BehaviorSpec + Gradle TestKit | End-to-end plugin integration tests | No |

Each source set has a clear job. Don't cross the streams — architecture
tests don't belong in `test/`, and functional tests don't belong in
`test/` either.

## Rules

### Unit tests (src/test/)

- Test every `internal` method on task classes
- Test every public method on model, workflow, report, parse, and analysis classes
- Use `ProjectBuilder` to create Gradle project instances for task tests
- Use the `createExtension()` helper pattern for tasks that need a `SettingsExtension`
- Never test `@TaskAction` methods directly — they run in a Gradle execution context that cannot be reproduced in unit tests
- Never test `SettingsPlugin.apply()` in unit tests — use functional tests

### Architecture tests (src/slopTest/)

- Use Konsist to scan `main` source set AST
- Enforce package boundary rules (which packages can import which)
- Ban forbidden patterns (try-catch, standalone constant files, wildcard imports)
- Enforce structural rules (task classes live in task/ package)
- See the `/konsist` skill for details

### Functional tests (src/functionalTest/)

- Use Gradle TestKit (`GradleRunner`) to run tasks in a forked Gradle process
- Test the plugin end-to-end: apply in settings, run tasks, verify outputs
- Create temporary project directories with `File.createTempFile` + `deleteOnExit()`
- Use helper functions to build up project structure: `withRootBuild()`, `withCoreModule()`, etc.

## Internal methods for testability

The core testing strategy is: **all logic in `internal` methods, `@TaskAction` is a thin orchestrator.**

```kotlin
class ProposeTask : DefaultTask() {
    lateinit var extension: Opsx.SettingsExtension

    // Testable: called directly in unit tests
    internal fun resolveChangeName(spec: String?, prompt: String?): String {
        if (!spec.isNullOrBlank()) return spec
        if (prompt.isNullOrBlank()) return "untitled-change"
        return prompt.split(" ")
            .take(4)
            .joinToString("-") { it.lowercase().replace(Regex("[^a-z0-9]"), "") }
    }

    // Testable: called directly in unit tests
    internal fun buildProposalPrompt(
        context: String,
        spec: String,
        projectDesc: String,
        userRequest: String,
    ): String { ... }

    // NOT directly testable — thin orchestrator excluded from Kover
    @TaskAction
    fun run() {
        val name = resolveChangeName(
            project.findProperty("spec") as? String,
            project.findProperty("prompt") as? String,
        )
        val prompt = buildProposalPrompt(...)
        // ... dispatch to agent
    }
}
```

The pattern generalizes: any method whose signature requires a live
Gradle execution context (build services, injected workers, live
`ProviderFactory` resolution) stays as a thin orchestrator and is
excluded from coverage. All logic moves to an `internal` method that
takes plain data in and returns plain data out — pure functions are
the easiest thing in the world to test.

## Coverage strategy

- **minBound: 90%** — enforced by Kover
- `@TaskAction` methods are excluded from coverage (opsx) because they need a real Gradle context
- `SettingsPlugin` classes are excluded from coverage (opsx) because `Settings.apply()` needs real Gradle Settings
- In srcx, no exclusions are permitted — all code must be testable
- If a method is hard to test, refactor it: extract logic into an `internal` method

See `/kover` for the convention-plugin configuration, the exclusion
annotations, and the difference between the strict srcx policy and the
opsx policy.

## createExtension() helper pattern

Tests for tasks that need the plugin's extension use a top-level helper:

```kotlin
private fun createExtension(): Opsx.SettingsExtension {
    val project = ProjectBuilder.builder().build()
    val ext = project.objects.newInstance(Opsx.SettingsExtension::class.java)
    ext.outputDir.convention("opsx")
    ext.defaultAgent.convention("claude")
    ext.specsDir.convention("specs")
    ext.changesDir.convention("changes")
    ext.projectFile.convention("project.md")
    return ext
}
```

This creates a real Gradle-managed object with default property values,
without needing a full plugin application. `newInstance` wires up the
`@Inject` machinery so `Property<T>`, `ListProperty<T>`, and
`MapProperty<K,V>` behave exactly as they do in production.

## Temp directory pattern

```kotlin
val projectDir = File.createTempFile("opsx-propose", "").apply {
    delete()       // Remove the temp file
    mkdirs()       // Create a directory at the same path
    deleteOnExit() // Clean up on JVM exit
}
val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
val task = project.tasks.create("test-propose", ProposeTask::class.java)
task.extension = createExtension()
```

The `createTempFile` → `delete` → `mkdirs` dance guarantees a unique
directory name without race conditions with other tests. `deleteOnExit`
schedules cleanup — the JVM removes the directory when the test process
exits, so developers don't accumulate `/tmp` garbage.

## Functional test structure

```kotlin
class SrcxPluginTest : BehaviorSpec({

    fun tempProject(): File = File.createTempFile("srcx-test", "").apply {
        delete(); mkdirs(); deleteOnExit()
    }

    // Composable setup helpers
    fun File.withRootBuild(): File {
        resolve("settings.gradle.kts").writeText("""
            plugins { id("com.example.gradle.myplugin") }
            rootProject.name = "test-workspace"
            include(":app", ":lib")
        """.trimIndent())
        resolve("build.gradle.kts").writeText("plugins { base }")
        return this
    }

    fun File.gradle(vararg args: String) =
        GradleRunner.create()
            .withProjectDir(this)
            .withPluginClasspath()
            .withArguments(*args, "--stacktrace")

    given("the srcx plugin applied in settings") {

        `when`("srcx-context runs on a multi-project build") {
            val projectDir = tempProject().withRootBuild()

            then("it creates the .srcx directory") {
                projectDir.gradle("srcx-context").build()
                projectDir.resolve(".srcx").shouldExist()
            }
        }

        `when`("running twice without changes") {
            val projectDir = tempProject().withRootBuild()

            then("second run is UP_TO_DATE") {
                projectDir.gradle("srcx-context").build()
                val result = projectDir.gradle("srcx-context").build()
                result.task(":srcx-context")?.outcome shouldBe TaskOutcome.UP_TO_DATE
            }
        }
    }
})
```

`withPluginClasspath()` pulls the main source set onto the TestKit
runner — no publish step required. `withRootBuild()` and siblings
compose: a test can chain `.tempProject().withRootBuild().withCoreModule()`
to assemble the exact topology it needs.

## Running tests

```bash
# Unit tests only
./gradlew test

# Architecture tests only
./gradlew slopTest

# Functional tests only
./gradlew functionalTest

# All checks (unit + architecture + detekt + ktlint)
./gradlew check

# Coverage verification
./gradlew koverVerify

# Coverage report
./gradlew koverHtmlReport
```

Composite builds: prefix with `:plugin-name:` to limit scope — e.g.
`./gradlew :opsx:test`.

## Examples

### Unit test for an internal method

```kotlin
class ResolveChangeNameTest : BehaviorSpec({
    val project = ProjectBuilder.builder().build()
    val task = project.tasks.create("t", ProposeTask::class.java)

    given("a non-blank spec") {
        `when`("spec is 'add-retry'") {
            then("returns the spec verbatim") {
                task.resolveChangeName("add-retry", null) shouldBe "add-retry"
            }
        }
    }

    given("a blank spec and a prompt") {
        `when`("prompt has more than four words") {
            then("takes first four words and kebab-cases them") {
                task.resolveChangeName(null, "Add retry logic to the HTTP client") shouldBe
                    "add-retry-logic-to"
            }
        }
    }

    given("no spec and no prompt") {
        then("returns 'untitled-change'") {
            task.resolveChangeName(null, null) shouldBe "untitled-change"
        }
    }
})
```

### Parameterized test via withData

```kotlin
class VersionParserTest : BehaviorSpec({
    context("parseVersion") {
        withData(
            "1.0.0" to Version(1, 0, 0),
            "0.0.1" to Version(0, 0, 1),
            "10.20.30" to Version(10, 20, 30),
        ) { (raw, expected) ->
            parseVersion(raw) shouldBe expected
        }
    }
})
```

## Anti-patterns

```kotlin
// WRONG: testing @TaskAction directly
@Test fun `run produces output`() {
    task.run()   // fails — needs live Gradle exec context
}
// Correct: test the internal methods that run() calls
```

```kotlin
// WRONG: architecture test in src/test/
// src/test/kotlin/.../PackageBoundaryTest.kt
// Correct: src/slopTest/kotlin/.../PackageBoundaryTest.kt
```

```kotlin
// WRONG: mocking framework
val extensionMock = mockk<SettingsExtension>()
every { extensionMock.outputDir.get() } returns "opsx"
// Correct: real object via createExtension()
val ext = createExtension()
```

```kotlin
// WRONG: JUnit @TempDir (doesn't integrate with Kotest lifecycle)
@TempDir lateinit var tmp: Path
// Correct: createTempFile + deleteOnExit
val tmp = File.createTempFile("t", "").apply { delete(); mkdirs(); deleteOnExit() }
```

```kotlin
// WRONG: relying on functional tests for coverage
// Kover cannot instrument Gradle's forked JVM.
// Correct: unit-test internal methods; use functional tests for end-to-end sanity
```

```kotlin
// WRONG: shared mutable state between then blocks
var counter = 0
given("something") {
    `when`("a") { counter++; then("x") { counter shouldBe 1 } }
    `when`("b") { counter++; then("y") { counter shouldBe 1 } } // flaky
}
// Correct: each when block creates its own local state
```

```kotlin
// WRONG: putting business logic in a method excluded from coverage
@TaskAction fun run() {
    val filtered = inputs.filter { it.size > threshold }  // real logic
    writeOutput(filtered)
}
// Correct: extract to an internal method
@TaskAction fun run() = writeOutput(filterInputs())
internal fun filterInputs() = inputs.filter { it.size > threshold }
```

```kotlin
// WRONG: skipping unit tests because functional tests "cover" it
// Functional tests are slow, don't feed Kover, and fail late.
// Correct: unit-test every internal method; keep functional tests for
// end-to-end wiring only
```

## Common pitfalls

- **Flaky test that passes locally, fails in CI** — almost always a
  determinism leak: time, random, file ordering, or env vars. Fix the
  leak; don't add retries.
- **`ProjectBuilder` doesn't run afterEvaluate** — it builds the
  project model but does not execute the configuration lifecycle the
  way a real build does. If your code reads properties during
  `afterEvaluate`, test the logic, not the lifecycle.
- **Functional test leaves files behind** — if `deleteOnExit()` isn't
  fast enough on CI, explicitly clean up at the end of the spec.
- **Kotest spec isolation mode confusion** — default is
  `SingleInstance` per spec; check your `kotest.properties` before
  assuming leaks.
- **Coverage drops after refactor** — an `internal` method was
  inlined into `@TaskAction`. Extract it back out.
- **Forgotten `withPluginClasspath()`** — functional test fails with
  "plugin not found". Add it to the `GradleRunner` chain.
- **`@TaskAction` silently catches exceptions via runCatching** — the
  failure doesn't reach the test framework. Keep error paths in the
  testable `internal` method; let `@TaskAction` throw.

## References

- Kotest BehaviorSpec — https://kotest.io/docs/framework/styles.html#behavior-spec
- Gradle TestKit — https://docs.gradle.org/current/userguide/test_kit.html
- `ProjectBuilder` — https://docs.gradle.org/current/javadoc/org/gradle/testfixtures/ProjectBuilder.html
- Konsist — https://docs.konsist.lemonappdev.com/
- Kover — https://kotlin.github.io/kotlinx-kover/gradle-plugin/
