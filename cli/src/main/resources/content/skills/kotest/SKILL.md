---
name: kotest
description: Kotest testing framework — spec styles, matchers, lifecycle hooks, isolation modes, data-driven testing, property testing, extensions.
---

# Kotest

Kotest is a multiplatform Kotlin test framework with 10 spec styles, 350+ matchers, property testing, and a first-class lifecycle API. It runs on JUnit Platform (JVM), Kotlin/JS, Kotlin/Native, and WasmJs.

Source:
- https://kotest.io/docs/framework/framework.html
- https://kotest.io/docs/assertions/assertions.html
- https://kotest.io/docs/proptest/property-based-testing.html

## 1. Setup

```kotlin
// build.gradle.kts (JVM)
plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest:kotest-framework-datatest:5.9.1")
    testImplementation("io.kotest:kotest-property:5.9.1")           // property testing
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

Multiplatform: use `kotest-framework-engine` + `kotest-assertions-core` as `commonTest`, and `kotest-runner-junit5` only on JVM.

## 2. Spec styles

A spec is a class that extends one of the style bases. All styles are behaviourally identical — pick one and stick to it per repo.

### FunSpec — the default

```kotlin
class StringTest : FunSpec({
    test("length returns size") {
        "hello".length shouldBe 5
    }

    context("trim") {
        test("removes leading whitespace") {
            "  x".trim() shouldBe "x"
        }
    }
})
```

### DescribeSpec — RSpec / Jest style

```kotlin
class UserTest : DescribeSpec({
    describe("User") {
        describe("creation") {
            it("defaults name to blank") {
                User().name shouldBe ""
            }
        }
    }
})
```

### ShouldSpec

```kotlin
class CalcTest : ShouldSpec({
    context("add") {
        should("sum two ints") { (1 + 2) shouldBe 3 }
    }
})
```

### StringSpec — minimal

```kotlin
class StringSpecTest : StringSpec({
    "length is 5" { "hello".length shouldBe 5 }
    "reverse inverts" { "ab".reversed() shouldBe "ba" }
})
```

### BehaviorSpec — BDD

```kotlin
class CartTest : BehaviorSpec({
    given("an empty cart") {
        val cart = Cart()
        `when`("an item is added") {
            cart.add(Item("pen"))
            then("size is 1") { cart.size shouldBe 1 }
        }
    }
})
```

Note: `when` is a Kotlin keyword — escape with backticks.

### FreeSpec — arbitrary nesting via `-`

```kotlin
class TreeTest : FreeSpec({
    "root" - {
        "child" - {
            "leaf" { 1 shouldBe 1 }
        }
    }
})
```

### WordSpec

```kotlin
class WordSpecTest : WordSpec({
    "String.length" should {
        "return its size" { "abc".length shouldBe 3 }
    }
})
```

### FeatureSpec — Cucumber-like

```kotlin
class LoginFeature : FeatureSpec({
    feature("login") {
        scenario("valid credentials") { /* ... */ }
        scenario("wrong password") { /* ... */ }
    }
})
```

### ExpectSpec

```kotlin
class ExpectSpecTest : ExpectSpec({
    context("math") {
        expect("1 + 1 = 2") { (1 + 1) shouldBe 2 }
    }
})
```

### AnnotationSpec — JUnit-style annotations

```kotlin
class AnnotationSpecTest : AnnotationSpec() {
    @BeforeEach fun setup() { /* ... */ }
    @Test fun `adds two`() { (1 + 1) shouldBe 2 }
    @Test @Ignore fun pending() { }
}
```

Useful for migrating from JUnit; has no containers.

## 3. Disabling tests

| Technique          | Example                                      |
|--------------------|----------------------------------------------|
| `x`-prefix        | `xtest("…") { }`, `xdescribe("…")`, `xit`    |
| `!`-prefix (bang)  | `"!not ready" { }` — the `!` skips it        |
| `f:`-prefix (focus) | `"f:only this" { }` — runs only focused tests at the root level |
| `.config(enabled=false)` | per-test config                        |
| `enabledIf = { cond }`   | runtime predicate                      |

```kotlin
test("slow").config(enabled = System.getenv("SLOW") != null) { }
test("windows-only").config(enabledIf = { OS.current == OS.WINDOWS }) { }
```

## 4. Test config

```kotlin
test("with config").config(
    invocations = 10,
    threads = 2,
    timeout = 5.seconds,
    tags = setOf(Slow, Integration),
    enabled = true,
    retries = 3,
    retryDelay = 1.seconds,
) { /* body */ }
```

Defaults can be set per-spec (`override fun defaultTestCaseConfig()`) or project-wide in `AbstractProjectConfig`.

## 5. Lifecycle hooks

Two forms: DSL or override.

### DSL (inside the spec's init block)

```kotlin
class Example : FunSpec({
    beforeSpec { /* once, before any tests in this spec */ }
    afterSpec  { /* once, after all tests in this spec */ }

    beforeTest { testCase -> /* before every test */ }
    afterTest  { (test, result) -> /* after every test; runs even on failure */ }

    beforeEach { /* before each LEAF test */ }
    afterEach  { /* after each leaf test */ }

    beforeContainer { /* before container tests only */ }
    afterContainer  { /* after container tests only */ }

    beforeAny { /* before any test (leaf or container) */ }
    afterAny  { /* after any test */ }

    beforeInvocation { (tc, n) -> /* before invocation n when config.invocations > 1 */ }
    afterInvocation  { (tc, n) -> /* after invocation n */ }
})
```

### Override

```kotlin
class Example : FunSpec() {
    override suspend fun beforeSpec(spec: Spec) { }
    override suspend fun afterEach(testCase: TestCase, result: TestResult) { }
    init { test("x") { } }
}
```

### Project-level

```kotlin
// kotest-project config discovered via service loader
class ProjectConfig : AbstractProjectConfig() {
    override suspend fun beforeProject() { /* once per test run */ }
    override suspend fun afterProject() { /* once per test run */ }
    override fun extensions() = listOf(MyListener)
}
```

Rules:
- `afterTest` runs even when the test fails.
- If `beforeSpec` fails, no tests run and `afterSpec` is skipped.
- Skipped tests (bang / disabled) do not invoke `beforeTest` / `afterTest`.

## 6. Isolation modes

Controls how many spec instances Kotest creates.

| Mode                 | Behaviour                                              | Use when                              |
|----------------------|--------------------------------------------------------|----------------------------------------|
| `SingleInstance` (default) | One spec instance; state is shared across all tests | Pure functions, stateless helpers      |
| `InstancePerRoot` (recommended for stateful) | Fresh instance per root container | Mutable state at the top of the spec |
| `InstancePerTest` (deprecated) | Fresh instance per test and container | Legacy                          |
| `InstancePerLeaf` (deprecated) | Fresh instance per leaf; parents re-run | Legacy                             |

```kotlin
class T : FunSpec({
    test("x") { }
}) {
    override fun isolationMode() = IsolationMode.InstancePerRoot
}
```

Or globally in `AbstractProjectConfig.isolationMode`.

## 7. Matchers

Core matcher library (`kotest-assertions-core`) has 350+ matchers. Import-less infix form: `value shouldBe other`.

### Equality & nullability
```kotlin
x shouldBe 5
x shouldNotBe 6
x.shouldBeNull()
x.shouldNotBeNull()
x shouldBeSameInstanceAs y         // ===
x shouldNotBeSameInstanceAs y
```

### Strings
```kotlin
"abc" shouldHaveLength 3
"abc" shouldContain "b"
"abc" shouldStartWith "a"
"abc" shouldEndWith "c"
"abc" shouldMatch Regex("[a-z]+")
"abc".shouldBeLowerCase()
```

### Numbers
```kotlin
3.1 shouldBe (3.0 plusOrMinus 0.2)
5 shouldBeInRange 1..10
5.shouldBePositive()
0.shouldBeZero()
```

### Collections
```kotlin
list shouldHaveSize 3
list shouldContain x
list shouldContainAll listOf(1, 2)
list shouldContainExactly listOf(1, 2, 3)                // order matters
list shouldContainExactlyInAnyOrder listOf(3, 2, 1)
list.shouldBeEmpty()
list.shouldBeSortedWith(compareBy { it.name })
list.shouldBeUnique()

map shouldContainKey "k"
map shouldContain ("k" to "v")
```

### Throwables
```kotlin
val ex = shouldThrow<IllegalStateException> { doIt() }
ex.message shouldContain "bad"

shouldNotThrow<Exception> { safe() }

val any = shouldThrowAny { doIt() }                       // any Throwable
shouldThrowExactly<IllegalArgumentException> { }          // NOT subclass
```

### Files
```kotlin
file.shouldExist()
file.shouldBeADirectory()
file.shouldBeAFile()
file.shouldContainFile("x.txt")
```

### Negation
Every `shouldX` has a `shouldNotX`.

### Custom matcher
```kotlin
fun haveEvenLength() = object : Matcher<String> {
    override fun test(value: String) = MatcherResult(
        passed = value.length % 2 == 0,
        failureMessageFn = { "\"$value\" length ${value.length} should be even" },
        negatedFailureMessageFn = { "\"$value\" length should not be even" },
    )
}

"abcd" should haveEvenLength()
"abc" shouldNot haveEvenLength()
```

## 8. Soft assertions

Collect multiple failures instead of stopping at the first:

```kotlin
assertSoftly(user) {
    name shouldBe "Sam"
    age shouldBe 30
    email shouldContain "@"
}
```

Or untargeted:

```kotlin
assertSoftly {
    x shouldBe 1
    y shouldBe 2
    z shouldBe 3
}
```

All failures are reported together.

## 9. Inspectors — assertions over collections

```kotlin
users.forAll { it.age shouldBeGreaterThan 0 }
users.forNone { it.banned }
users.forOne { it.role shouldBe Role.Admin }
users.forExactly(3) { it.city shouldBe "Chicago" }
users.forAtLeast(2) { it.active }
users.forAtMost(1) { it.admin }
users.forSome { it.verified }
```

Each fails with a count-based message: "expected exactly 3 but 2 passed".

## 10. Clues

Add context to failure messages:

```kotlin
withClue("user $id should be admin") {
    user.role shouldBe Role.Admin
}

user.asClue {
    it.name shouldBe "Sam"
    it.age shouldBe 30
}
```

Clues compose through nesting.

## 11. Data-driven testing (table tests)

```kotlin
class CToFTest : FunSpec({
    context("celsius to fahrenheit") {
        withData(
            row(0, 32),
            row(100, 212),
            row(-40, -40),
        ) { (c, f) ->
            cToF(c) shouldBe f
        }
    }
})
```

With named rows:

```kotlin
withData(
    nameFn = { "${it.input} -> ${it.expected}" },
    TestCase(0, 32),
    TestCase(100, 212),
) { (input, expected) ->
    cToF(input) shouldBe expected
}
```

Data tests live in a container (`context`, `describe`, etc.), not a leaf. Each row becomes a reported test case. Lifecycle hooks fire per row.

## 12. Property-based testing

`kotest-property` generates random inputs.

```kotlin
class MathProperties : FunSpec({
    test("addition is commutative") {
        checkAll<Int, Int> { a, b -> a + b shouldBe b + a }
    }

    test("reversing twice is identity") {
        checkAll(Arb.string()) { s ->
            s.reversed().reversed() shouldBe s
        }
    }
})
```

Built-in generators (`Arb.*` for arbitraries, `Exhaustive.*` for complete enumerations):
```kotlin
Arb.int(min = 0, max = 100)
Arb.string(minSize = 1, maxSize = 20)
Arb.list(Arb.int(), range = 0..10)
Arb.bind(Arb.string(), Arb.int(), ::User)        // compose into a data class

Exhaustive.boolean()
Exhaustive.enum<Role>()
```

Shrinking: on failure, Kotest minimises the input to the smallest counterexample.

```kotlin
PropertyTesting.defaultIterationCount = 1000         // override default 1000
checkAll(iterations = 5000, Arb.int()) { ... }
```

## 13. Tags

```kotlin
object Slow : Tag()
object Integration : Tag()

test("heavy").config(tags = setOf(Slow, Integration)) { }
```

Run:
```bash
./gradlew test -Dkotest.tags="Slow & !Integration"
```

Tag expressions support `&`, `|`, `!`, and parentheses.

## 14. Extensions and listeners

A `TestListener` (or single-method equivalents) plugs into any lifecycle hook:

```kotlin
object DbCleaner : TestListener {
    override suspend fun beforeTest(testCase: TestCase) { db.clear() }
}

class MyTest : FunSpec({
    listener(DbCleaner)
    test("x") { /* ... */ }
})
```

Project-wide listeners in `AbstractProjectConfig.extensions`.

Common built-ins:
- `SpringExtension` — Spring context wiring.
- `TestContainerExtension` — manage a Testcontainers container lifecycle.
- `MockKExtension` — `clearMocks` between tests.

## 15. Coroutines

Every test body is a `suspend` lambda. `delay`, `async`, `launch` all just work.

```kotlin
test("suspending") {
    val x = async { compute() }
    x.await() shouldBe 42
}
```

`runTest` from kotlinx-coroutines is unnecessary — Kotest integrates `TestScope` via its test coroutine dispatcher.

```kotlin
class TimeTest : FunSpec({
    coroutineTestScope = true          // virtual time
    test("advances") {
        var done = false
        launch { delay(1.hours); done = true }
        testCoroutineScheduler.advanceUntilIdle()
        done shouldBe true
    }
})
```

## 16. Nested tests and structure

Not every style allows nesting — StringSpec and AnnotationSpec are flat. The nesting-capable styles are FunSpec (`context`), DescribeSpec (`describe`/`context`), ShouldSpec (`context`), BehaviorSpec (`given`/`when`), FreeSpec (`-`), WordSpec (`should`/`when`), FeatureSpec (`feature`), ExpectSpec (`context`).

## 17. Test ordering

```kotlin
class T : FunSpec({ }) {
    override fun testOrder() = TestCaseOrder.Sequential   // or Random, Lexicographic
}
```

Project-wide via `AbstractProjectConfig.testCaseOrder`.

## 18. Parallel execution

```kotlin
// ProjectConfig.kt
class ProjectConfig : AbstractProjectConfig() {
    override val parallelism = 4                         // specs in parallel
    override val concurrentTests = 8                     // tests in parallel within a spec
}
```

Spec-level:
```kotlin
override fun concurrency() = ProjectConfiguration.MaxConcurrency   // all tests in parallel
```

Tests inside the same spec share spec state — parallel + mutable state is asking for races. Use `InstancePerRoot` or stateless specs.

## 19. JUnit integration

`kotest-runner-junit5` registers as a JUnit Platform TestEngine, so IntelliJ, Gradle, Maven, Surefire all discover Kotest specs. Reports appear in the standard JUnit XML output.

## 20. Project config discovery

Create `src/test/kotlin/ProjectConfig.kt` extending `AbstractProjectConfig` and register via `META-INF/services/io.kotest.core.config.AbstractProjectConfig` — or, in 5.x, just put it in the test sources and it's auto-discovered by classpath scanning.

## 21. Anti-patterns

- Using `@Test`/`@BeforeEach` in a non-`AnnotationSpec` — the class extends `FunSpec`/etc. so annotations are ignored.
- Mutable state at spec level with `SingleInstance` isolation — tests leak state. Switch to `InstancePerRoot` or `beforeEach { reset() }`.
- `shouldBe` with floats — use `(x plusOrMinus epsilon)`.
- Stacking `shouldThrow` around a block that *might* throw the wrong type — prefer `shouldThrowExactly<T>` to catch base-class leaks.
- `runBlocking` inside a test — the test body is already a suspend lambda; `runBlocking` spawns a nested event loop and breaks `TestScope` / `coroutineTestScope`.
- Long `assertAll { }` blocks with unrelated assertions — keep scope narrow; one failing soft-assert still prints every other assertion's success which is noise.
- Data rows that differ only by argument names — use `withData(nameFn = { "${it.a}->${it.b}" }, ...)` so failing rows have meaningful names.
- Reusing a single `Arb` across tests without fresh seeds — tests become order-dependent. Let Kotest reseed per test or pin with `PropertyTesting.defaultSeed`.

## 22. Cheat sheet

```kotlin
// Basic FunSpec
class T : FunSpec({
    beforeTest { /* setup */ }
    afterTest  { /* teardown */ }

    test("x") { 1 + 1 shouldBe 2 }

    context("group") {
        test("y") { "a".length shouldBe 1 }
    }
})

// Data-driven
withData(row(1, 1), row(2, 4)) { (n, sq) -> n*n shouldBe sq }

// Property
checkAll(Arb.int(), Arb.int()) { a, b -> a + b shouldBe b + a }

// Exceptions
shouldThrow<IllegalStateException> { check(false) }.message shouldContain "false"

// Soft
assertSoftly { a shouldBe 1; b shouldBe 2 }
```

## 23. Related skills

- Testing conventions used in this project → `/testing-patterns`
- Architecture tests → `/konsist`
- Coverage enforcement → `/kover`
- Coroutine-specific test patterns → `/coroutines-suspend-functions`
