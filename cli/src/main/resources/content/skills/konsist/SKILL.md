---
name: konsist
description: Konsist architecture tests — scopes, declaration queries, filters, assertions, layered architecture verification, Kotest/JUnit integration.
---

# Konsist

Konsist is a structural linter for Kotlin — the Kotlin-native answer to ArchUnit. It reads Kotlin source with a PSI-aware API and lets you write tests that enforce naming rules, package layout, annotation presence, and layered architecture boundaries.

Source:
- https://docs.konsist.lemonappdev.com/
- https://docs.konsist.lemonappdev.com/llms-full.txt

## 1. Setup

```kotlin
// build.gradle.kts
dependencies {
    testImplementation("com.lemonappdev:konsist:0.17.3")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")     // or JUnit 5 directly
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

Common pattern: a dedicated `slopTest` / `archTest` source set so architecture checks don't run on every unit-test invocation. Wire it into `check`.

## 2. Workflow

Every Konsist test is three or four steps:

1. **Create a scope** — which files to analyse.
2. **Query declarations** — what kinds of things to filter.
3. **Filter** with `withX` / `withoutX`.
4. **Assert** with `assertTrue`/`assertFalse`/`assertEmpty`/`assertNotEmpty`.

Architecture tests add a layer-rules step before the final assert.

## 3. Scope creation

```kotlin
Konsist.scopeFromProject()            // entire build (all modules, all source sets)
Konsist.scopeFromProduction()         // production code only (excludes tests)
Konsist.scopeFromTest()               // test code only
Konsist.scopeFromModule("app")        // one module
Konsist.scopeFromModule("app/feature") // nested module path
Konsist.scopeFromSourceSet("test")    // all modules, one source set
Konsist.scopeFromProject(moduleName = "app", sourceSetName = "test")

Konsist.scopeFromPackage("com.app.domain..")   // `..` matches sub-packages
Konsist.scopeFromDirectory("app/domain")
Konsist.scopeFromFile("app/main/domain/UseCase.kt")
Konsist.scopeFromFile(listOf("a.kt", "b.kt"))
```

Scopes compose:

```kotlin
val combined = scope1 + scope2
val subset   = moduleScope - excludedScope

val sliced = koScope.slice { it.relativePath.contains("/internal/") }
```

Cache the scope at class level — parsing is the expensive step:

```kotlin
class ArchTest : FreeSpec({
    val scope = Konsist.scopeFromProduction()

    "..." { scope.classes().assertTrue { ... } }
})
```

## 4. Declaration queries

Walk a scope down to the level you need.

```kotlin
koScope.files                                 // KoFile
koScope.declarations()                        // everything: classes, funcs, props, etc.

koScope.classes(includeNested = true)         // KoClassDeclaration
koScope.interfaces()
koScope.objects()
koScope.enumClasses()
koScope.dataClasses()
koScope.valueClasses()
koScope.sealedClasses()
koScope.annotationClasses()
koScope.typeAliases

koScope.functions(includeNested = true, includeLocal = true)
koScope.properties(includeNested = true, includeLocal = true)
koScope.imports
koScope.companionObjects()

// navigate deeper
koScope.classes().functions()
koScope.classes().properties()
koScope.classes().primaryConstructor
koScope.classes().parameters
```

## 5. Filters

Paired `withX` / `withoutX` throughout. Chain freely.

### By name

```kotlin
.withName("UserService")
.withNameStartingWith("Local")
.withNameEndingWith("UseCase")
.withNameContaining("Repository")
.withNameMatching(Regex("^[A-Z][a-zA-Z]+UseCase$"))
.withoutName("Deprecated")
```

### By package

```kotlin
.withPackage("com.app.domain..")             // resides in
.withoutPackage("com.app.legacy..")
.resideInPackage("com.app.api..")            // on an individual declaration (in assert)
```

### By modifier / visibility

```kotlin
.withPublicModifier()
.withoutInternalModifier()
.withInternalModifier()
.withPrivateModifier()
```

### By annotation

```kotlin
.withAnnotationOf(Service::class)
.withoutAnnotationOf(Deprecated::class)
.withAllAnnotationsOf(Service::class, Transactional::class)
.withSomeAnnotationsOf(Inject::class, Autowired::class)
.withAnnotation { it.name == "Entity" }
```

### By parent / inheritance

```kotlin
.withParentOf(BaseService::class)
.withAllParentsOf(Disposable::class, Serializable::class)
.withParentClass()                           // has any superclass
.withoutParentClass()
.withParentInterface { it.name == "Repository" }
```

### By structure (functions, props, constructors)

```kotlin
.classes().withFunction { it.name == "toString" }
.withPrimaryConstructor()
.withoutPrimaryConstructor()
.functions().withParameter { it.hasTypeOf(String::class) }
.properties().withType { it.name == "LocalDateTime" }
```

## 6. Assertions

```kotlin
koScope.classes().assertTrue  { it.hasPublicModifier }
koScope.classes().assertFalse { it.hasAnnotationOf(Deprecated::class) }

koScope.interfaces()
    .withNameEndingWith("Mutable")
    .assertEmpty()

koScope.classes()
    .withPackage("..domain..")
    .assertNotEmpty()
```

Parameterised assert:

```kotlin
.assertTrue(
    testName = "UseCases live under domain.usecase",   // for @Suppress
    strict = true,                                      // fail if collection is empty
    additionalMessage = "Move this class into com.app.domain.usecase",
) { it.resideInPackage("..domain.usecase..") }
```

`strict = true` makes an empty collection fail — a common mistake is a filter that matches nothing, so the test silently passes. Use it when you're checking "there SHOULD be some of these and they all must …".

## 7. Common declaration predicates

### Classes / interfaces

```kotlin
it.hasPublicModifier
it.hasInternalModifier
it.hasAbstractModifier
it.hasDataModifier
it.hasSealedModifier
it.hasValueModifier
it.hasCompanionObject { obj -> obj.hasNameEndingWith("Companion") }
it.hasParentOf(BaseService::class)
it.hasAllParentsOf(A::class, B::class)
it.resideInPackage("..domain..")
it.hasNameEndingWith("Service")
it.hasKDoc
```

### Functions

```kotlin
it.hasPublicOrDefaultModifier
it.hasBlockBody
it.hasExpressionBody
it.hasReturnType
it.returnType?.name == "Flow"
it.hasParameter { p -> p.hasTypeOf(String::class) }
it.hasTypeParameters()
it.isTopLevel
it.isExtension
it.isSuspend
it.isInline
```

### Properties

```kotlin
it.isVal
it.isVar
it.hasLateinitModifier
it.hasGetter
it.hasSetter
it.isInitialized
it.hasDelegate("lazy")
it.type?.name == "LocalDateTime"
```

### Generics

```kotlin
// Declared type parameters
koScope.classes().typeParameters.assertTrue { it.name.length == 1 }

// Use-site type arguments
koScope.properties().assertTrue { p ->
    p.type?.typeArguments?.any { it.name == "UserDto" } == true
}
```

### Source-declaration drilling

Look up the actual class referenced by a type:

```kotlin
koScope.properties().assertTrue {
    it.type?.sourceDeclaration
        ?.asClassDeclaration()
        ?.hasInternalModifier == true
}
```

## 8. Architecture verification

Layered rules in one block:

```kotlin
Konsist.scopeFromProduction().assertArchitecture {
    val domain = Layer("Domain", "com.app.domain..")
    val data = Layer("Data", "com.app.data..")
    val presentation = Layer("Presentation", "com.app.presentation..")

    domain.dependsOnNothing()
    data.dependsOn(domain)
    presentation.dependsOn(domain)

    presentation.doesNotDependOn(data)     // explicit forbid
}
```

Primitives:

| Call                                   | Meaning                                                    |
|----------------------------------------|------------------------------------------------------------|
| `Layer(name, packagePattern)`          | Define a layer by package glob (`..` = any sub-package)    |
| `layer.dependsOnNothing()`             | No outbound dependencies allowed                           |
| `layer.dependsOn(other)`               | Outbound edge to `other` permitted                         |
| `layer.dependsOn(other, strict = true)`| Must depend on `other` (fails if no edge exists)           |
| `layer.doesNotDependOn(other)`         | Forbids the edge                                           |
| `layer.include()`                      | Include in the check without adding outbound rules         |

Reusable architecture definition:

```kotlin
val cleanArchitecture = architecture {
    val domain = Layer("Domain", "..domain..")
    val data = Layer("Data", "..data..")
    val ui = Layer("UI", "..ui..")
    domain.dependsOnNothing()
    data.dependsOn(domain)
    ui.dependsOn(domain)
}

class AllModulesArchTest : FunSpec({
    test("app module") {
        Konsist.scopeFromModule("app").assertArchitecture(cleanArchitecture)
    }
    test("feature module") {
        Konsist.scopeFromModule("feature").assertArchitecture(cleanArchitecture)
    }
})
```

Unmentioned layers are unconstrained. The check reports the concrete import edge that violated the rule.

## 9. Kotest integration

The idiom: create the scope once, write one `it { ... }` per rule.

```kotlin
class ArchSpec : FreeSpec({
    val scope = Konsist.scopeFromProduction()

    "all use cases end with UseCase" {
        scope.classes()
            .withPackage("..domain.usecase..")
            .assertTrue(testName = "UseCase suffix") { it.hasNameEndingWith("UseCase") }
    }

    "repositories are internal" {
        scope.classes()
            .withAnnotationOf(Repository::class)
            .assertTrue(testName = "internal repositories") { it.hasInternalModifier }
    }

    "architecture" {
        scope.assertArchitecture {
            val domain = Layer("Domain", "..domain..")
            val data = Layer("Data", "..data..")
            val presentation = Layer("Presentation", "..presentation..")
            domain.dependsOnNothing()
            data.dependsOn(domain)
            presentation.dependsOn(domain)
        }
    }
})
```

Always pass `testName = "..."` to `assertTrue` so `@Suppress` works — see §11.

## 10. JUnit integration

```kotlin
class ArchTests {
    private val scope = Konsist.scopeFromProduction()

    @Test
    fun `all use cases end with UseCase`() {
        scope.classes()
            .withPackage("..domain.usecase..")
            .assertTrue { it.hasNameEndingWith("UseCase") }
    }

    @Test
    fun `clean architecture`() {
        scope.assertArchitecture {
            val domain = Layer("Domain", "..domain..")
            val data = Layer("Data", "..data..")
            domain.dependsOnNothing()
            data.dependsOn(domain)
        }
    }
}
```

## 11. Suppression

Opt a single declaration out of a rule via `@Suppress("konsist.<test name>")`:

```kotlin
@Suppress("konsist.UseCase suffix")
class LegacyLookup { }
```

For Kotest, Konsist can't discover the test name automatically — pass it explicitly:

```kotlin
it("some rule") {
    scope.classes().assertTrue(testName = "some rule") { /* … */ }
}
```

File-level:

```kotlin
@file:Suppress("konsist.every api declaration has KDoc")
package com.app.api
```

Keep the suppression list audited — grep the codebase for `@Suppress("konsist.` periodically.

## 12. Debugging

```kotlin
scope.print()                                     // dump all declarations
scope.classes().print()                           // filtered
scope.classes().print { it.fullyQualifiedName }   // custom formatter

// in the IDE, evaluate at a breakpoint:
//   scope.classes().filter { it.name == "X" }.first().name
```

On a failing assert, Konsist reports the offending declaration with file + line + column.

## 13. Recipe catalog

### All DTOs end with `Dto` and are `data class`
```kotlin
scope.classes()
    .withPackage("..api.dto..")
    .assertTrue { it.hasNameEndingWith("Dto") && it.hasDataModifier }
```

### Controllers sit under `presentation.controller`
```kotlin
scope.classes()
    .withAnnotationOf(RestController::class)
    .assertTrue { it.resideInPackage("..presentation.controller..") }
```

### No public properties on a ViewModel
```kotlin
scope.classes()
    .withNameEndingWith("ViewModel")
    .properties()
    .assertFalse { it.hasPublicOrDefaultModifier && it.isVar }
```

### Every interface has at least one implementation
```kotlin
scope.interfaces()
    .withPackage("..domain..")
    .assertTrue { iface ->
        scope.classes().any { it.hasParentOf(iface) }
    }
```

### Suspending functions don't return `Flow`
```kotlin
scope.functions()
    .filter { it.isSuspend }
    .assertFalse { it.returnType?.name == "Flow" }
```

### No use of `runBlocking` outside `main`
```kotlin
scope.functions()
    .withoutName("main")
    .assertFalse { it.text.contains("runBlocking") }
```

### Repositories depend only on domain
```kotlin
scope.assertArchitecture {
    val domain = Layer("Domain", "..domain..")
    val repo = Layer("Repo", "..data.repository..")
    repo.dependsOn(domain)
    repo.doesNotDependOn(Layer("UI", "..presentation..")!!)
}
```

## 14. Performance

- Parsing the whole project takes seconds on large codebases. Reuse a scope across tests in one spec (declare as a class-level `val`).
- A dedicated source set (`slopTest` / `archTest`) keeps normal `test` fast; run it on `check` or on CI only.
- `scopeFromProduction()` is faster than `scopeFromProject()` because it skips tests.

## 15. Anti-patterns

- Using a new `Konsist.scopeFromProject()` in every test — re-parses each time. Hoist it.
- Filtering so tightly that the collection is empty, then asserting something — the test passes vacuously. Add `strict = true` or follow the filter with `.assertNotEmpty()`.
- `assertTrue` without a `testName` — `@Suppress` won't work. Always name your rules.
- Checking `it.text.contains("X")` on arbitrary source — fragile. Use declaration predicates (`hasAnnotationOf`, `withParameter`) where possible.
- Architecture rule that names every layer — if you add a module you forget to include, it's silently unconstrained. Start with `dependsOnNothing` on the core layer so the check fails loudly when a new outbound edge appears.
- Konsist rules that duplicate detekt / ktlint — let the cheaper tool handle formatting; reserve Konsist for structural rules those linters can't express (cross-package, inheritance, architecture).
- Writing a single mega-test with dozens of asserts — one failure masks the rest. One rule per test, each with a testName.

## 16. When to reach for Konsist vs detekt vs ktlint

| Concern                                    | Tool                                  |
|--------------------------------------------|---------------------------------------|
| Indentation, spacing, import order         | ktlint                                |
| Complexity, magic numbers, long methods    | detekt (built-in rules)               |
| "No `runBlocking` in production code"       | Konsist (semantic check)              |
| "UseCases live under domain.usecase"       | Konsist                               |
| "Data layer doesn't import UI"             | Konsist (`assertArchitecture`)        |
| "All repositories have `@Transactional`"   | Konsist                               |
| "No System.out.println"                    | detekt (`ForbiddenMethodCall`)        |

Konsist wins when the rule depends on package/type/graph relationships or on a rule specific to your architecture.

## 17. Related skills

- Test spec styles and assertions → `/kotest`
- Detekt rule config and custom rules → `/detekt`
- Ktlint style rules → `/ktlint`
- Package layout conventions → `/package-structure`
- Naming conventions enforced here → `/naming-conventions`
