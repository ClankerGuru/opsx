---
name: kotlin-dsl-builders
description: Kotlin DSL design — lambdas with receiver, function types with receiver, @DslMarker scope control, builder patterns, common pitfalls.
---

# Kotlin DSL Builders

Kotlin's DSL features turn configuration from string-keyed maps into compiler-checked, IDE-navigable code. Gradle Kotlin DSL, Ktor routing, Jetpack Compose, Exposed SQL, kotlinx.html, and kotest all use the same mechanic: function types with receiver + lambdas with receiver.

Source:
- https://kotlinlang.org/docs/type-safe-builders.html
- https://kotlinlang.org/docs/lambdas.html

## 1. When to apply

- Designing a configuration block for a Gradle plugin, library, or internal tool that should feel like a mini-language.
- Reading an existing DSL and needing to understand how `{ ... }` blocks resolve.
- Preventing accidental access to outer receivers in nested DSL scopes.
- Extending a DSL with a new nested block or new top-level verb.

## 2. The one mechanic

A function type with receiver is written `A.(B) -> C` — a function that runs with an `A` as implicit `this`, takes `B`, returns `C`. The common DSL-block shape is `A.() -> Unit`.

```kotlin
// Regular lambda — receiver passed as argument
val asArg: (StringBuilder) -> Unit = { sb -> sb.append("hi") }

// Lambda with receiver — receiver is implicit this
val asReceiver: StringBuilder.() -> Unit = { append("hi") }

val sb = StringBuilder()
asReceiver(sb)          // invoke with receiver as first arg
sb.asReceiver()         // or call as if it were an extension
```

A DSL entry point uses this shape to build an object, run user code on it, and return it:

```kotlin
fun buildString(block: StringBuilder.() -> Unit): String =
    StringBuilder().apply(block).toString()

val text = buildString {
    append("Hello, ")
    append("world")
}
```

`apply` itself is defined with `T.() -> Unit` and is the canonical "create, configure, return" helper.

## 3. Builder skeleton

```kotlin
class ServerConfig internal constructor() {
    var host: String = "localhost"
    var port: Int = 8080
    private val middleware = mutableListOf<Middleware>()

    fun use(m: Middleware) { middleware += m }
    internal fun build(): Server = Server(host, port, middleware.toList())
}

fun server(block: ServerConfig.() -> Unit): Server =
    ServerConfig().apply(block).build()

// usage
val s = server {
    host = "api.example.com"
    port = 443
    use(Logging)
    use(Auth)
}
```

Patterns:
- Private constructor — force creation via the entry point.
- `var` for simple knobs the user sets directly.
- `fun use(...)` for additions — don't expose the `MutableList`.
- `internal fun build()` — the result object is constructed once, after the block runs.
- The `apply(block)` trick: mutate via the lambda, then call `build()`.

## 4. Nested builders

```kotlin
class Route {
    var path: String = "/"
    var method: String = "GET"
    private var handler: Handler = NoOp

    fun handler(h: Handler) { this.handler = h }
    internal fun build() = RouteSpec(path, method, handler)
}

class Routing {
    private val routes = mutableListOf<RouteSpec>()

    fun route(block: Route.() -> Unit) {
        routes += Route().apply(block).build()
    }

    internal fun build() = RoutingSpec(routes.toList())
}

fun routing(block: Routing.() -> Unit): RoutingSpec =
    Routing().apply(block).build()

// usage
val r = routing {
    route {
        path = "/users"
        method = "GET"
        handler(ListUsers)
    }
    route { path = "/health"; handler(Health) }
}
```

Rules:
- Every builder's primary surface is a set of functions taking `Foo.() -> Unit`.
- Builders hold mutable state internally; the final object is immutable.
- The user never sees the builder type outside the lambda.

## 5. `@DslMarker` — scope control

Without scope control, nested blocks accidentally reach outer receivers:

```kotlin
html {
    head {
        head { }           // oops — outer Html.head silently valid
    }
}
```

Fix with a marker annotation on every DSL receiver type:

```kotlin
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class HtmlTagMarker

@HtmlTagMarker
abstract class Tag(val name: String)
```

After marking `Tag`, the compiler permits access only to the nearest marked receiver. Explicit qualification still works:

```kotlin
html {
    head {
        this@html.head { }    // explicit outer receiver — allowed
    }
}
```

One marker per DSL — define your own so unrelated DSLs don't block each other.

## 6. The HTML builder, step by step

```kotlin
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class HtmlTagMarker

interface Element { fun render(sb: StringBuilder, indent: String) }

@HtmlTagMarker
abstract class Tag(val name: String) : Element {
    val children = mutableListOf<Element>()
    val attrs    = mutableMapOf<String, String>()

    protected fun <T : Element> initTag(tag: T, init: T.() -> Unit): T {
        tag.init()
        children += tag
        return tag
    }

    override fun render(sb: StringBuilder, indent: String) {
        sb.append(indent).append("<").append(name)
        attrs.forEach { (k, v) -> sb.append(" $k=\"$v\"") }
        if (children.isEmpty()) sb.append("/>\n")
        else {
            sb.append(">\n")
            children.forEach { it.render(sb, "$indent  ") }
            sb.append(indent).append("</").append(name).append(">\n")
        }
    }
}

class Text(val value: String) : Element {
    override fun render(sb: StringBuilder, indent: String) { sb.append(indent).append(value).append('\n') }
}

abstract class TagWithText(name: String) : Tag(name) {
    // `+"raw"` appends a Text child
    operator fun String.unaryPlus() { children += Text(this) }
}

class Html  : TagWithText("html")  { fun head(init: Head.() -> Unit) = initTag(Head(), init); fun body(init: Body.() -> Unit) = initTag(Body(), init) }
class Head  : TagWithText("head")  { fun title(init: Title.() -> Unit) = initTag(Title(), init) }
class Body  : TagWithText("body")  { fun h1(init: H1.() -> Unit) = initTag(H1(), init); fun p(init: P.() -> Unit) = initTag(P(), init) }
class Title : TagWithText("title")
class H1    : TagWithText("h1")
class P     : TagWithText("p")

fun html(init: Html.() -> Unit): Html = Html().apply(init)
```

Usage:

```kotlin
val page = html {
    head { title { +"Hello" } }
    body {
        h1 { +"Welcome" }
        p  { +"DSLs are just functions." }
    }
}
```

Key moves:
- Every builder takes `T.() -> Unit` so child calls happen on the child receiver.
- `initTag` is a generic helper that centralises the create/init/attach/return ritual.
- `String.unaryPlus` gives literal text a non-verbose syntax.

## 7. Providing an entry point inside a receiver

For API ergonomics, put the top-level verb on a target type:

```kotlin
fun Project.myPlugin(block: MyPluginExtension.() -> Unit) {
    extensions.configure<MyPluginExtension>(block)
}

// build.gradle.kts
myPlugin {
    endpoint = "https://api"
}
```

This mirrors Gradle's standard extension accessor and gives the user a familiar shape.

## 8. Gradle Kotlin DSL: production example

A `build.gradle.kts` runs with an implicit `Project` receiver. Top-level functions like `dependencies { }`, `tasks { }`, `kotlin { }` are all extensions on `Project` that accept lambdas with receiver.

```kotlin
plugins {
    kotlin("jvm") version "2.0.21"
}

dependencies {                            // DependencyHandler receiver
    implementation("org.slf4j:slf4j-api:2.0.13")
    testImplementation(kotlin("test"))
}

tasks {                                   // TaskContainerScope receiver
    test {                                // Test task receiver
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
    }
}
```

When you write a Gradle plugin with its own extension, follow the same shape:

```kotlin
@MyPluginDsl
abstract class MyPluginExtension {
    abstract val endpoint: Property<String>
    abstract val retries:  Property<Int>

    fun retry(init: RetryConfig.() -> Unit) {
        retryConfig.apply(init)
    }
    internal val retryConfig = RetryConfig()
}

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class MyPluginDsl
```

## 9. Higher-order function ergonomics

### `inline` on DSL entry points

```kotlin
inline fun server(block: ServerConfig.() -> Unit): Server =
    ServerConfig().apply(block).build()
```

Benefits:
- Lambda parameters don't allocate a function object — the body is inlined at the call site.
- Non-local `return` works: `return` inside the lambda returns from the enclosing function.
- `reified` type parameters become available.

Don't inline large functions — it bloats bytecode. Inline for small, hot DSL entry points and helpers.

### `crossinline` for stored lambdas

```kotlin
inline fun onReady(crossinline action: () -> Unit) {
    future.thenRun { action() }           // stored; non-local return would break
}
```

`crossinline` forbids non-local returns from the lambda — needed when the lambda is captured by another object or executed asynchronously.

### `noinline` to opt out

```kotlin
inline fun <T> transact(crossinline setup: () -> Unit, noinline body: () -> T): T { ... }
```

Used when a lambda must be stored but the outer function is still inline.

## 10. Type-safe configuration with `Property<T>` (Gradle)

Inside Gradle plugin DSLs, prefer lazy Property fields over plain `var`:

```kotlin
abstract class MyExt {
    abstract val endpoint: Property<String>        // Provider-aware
    abstract val timeout: Property<Duration>
    abstract val tags: ListProperty<String>
}
```

The user can wire providers:

```kotlin
myPlugin {
    endpoint = providers.gradleProperty("api.endpoint")
    timeout = project.version.map { Duration.ofSeconds(30) }
    tags.add("prod")
}
```

See `/gradle-providers-properties` for the full API.

## 11. Operator overloading in DSLs

Judicious operators make DSLs read naturally:

```kotlin
class Matrix {
    operator fun get(i: Int, j: Int): Int = ...
    operator fun set(i: Int, j: Int, v: Int) { ... }
}

matrix[0, 0] = 1
```

```kotlin
class EventBus {
    operator fun <T : Event> invoke(event: T) = publish(event)
}

bus(UserLoggedIn(u))      // reads like a function call
```

Don't overload just because you can. `plus` that doesn't behave like addition, `invoke` that does something surprising — these confuse readers.

## 12. `context receivers` (preview)

Kotlin's context receivers (preview feature) let a function require a receiver without being an extension:

```kotlin
context(Logger)
fun doThing() { info("doing thing") }

with(logger) { doThing() }
```

Useful for injecting a DSL context into a function without threading it through parameters. Still unstable — use with caution and pin the Kotlin version.

## 13. Practical patterns

- Return `Unit` from nested blocks; return a result from the top-level entry point.
- Hide mutable state behind the builder — expose `add/use/register` verbs, not the `MutableList`.
- One `@DslMarker` per DSL. Mark every receiver type used inside `{ }` blocks.
- Factor the create/init/attach pattern (`initTag`) into a helper.
- Prefer a single entry verb per nesting level; don't add top-level functions inside a scoped block.
- Use `inline fun` on DSL entry points so users get non-local `return` and zero allocation.

## 14. Gotchas

- **Missing `@DslMarker`** — nested blocks silently see outer receivers; user bugs surface as mysterious runtime behaviour.
- **Mixing `T.() -> Unit` and `(T) -> Unit`** inconsistently confuses users. Pick receiver-form and stick with it.
- **`this` ambiguity** — inside a nested lambda, `this` is the innermost receiver. Use `this@outerBuilder` explicitly when needed.
- **Long `apply { }.apply { }` chains** — the DSL is missing a block and the user is compensating.
- **Mutable state leaked** via `val list: MutableList` — users mutate through references outside your builder flow. Expose read-only collections or builder methods.
- **Captured `this` from an enclosing class** inside a DSL body — pass explicit values in via the receiver.
- **Builder not `internal`** — users can construct arbitrary broken instances. Mark the constructor `internal` (or private with top-level factory).
- **Returning the mutable builder from the entry point** — callers start mutating after `build()`.

## 15. Testing DSLs

```kotlin
class ServerTest : FunSpec({
    test("builder collects middleware in order") {
        val s = server {
            host = "x"
            port = 1
            use(Logging)
            use(Auth)
        }
        s.host shouldBe "x"
        s.middleware shouldContainExactly listOf(Logging, Auth)
    }
})
```

Test the shape the user writes, not the internal builder API. If a test needs to reach into the builder, the DSL is leaking.

## 16. Related skills

- Language primer → `/kotlin-lang`
- Functional-first design → `/kotlin-functional-first`
- Gradle extensions specifically → `/kotlin-dsl` and `/gradle-custom-plugins`
- Lazy Property API used in Gradle DSLs → `/gradle-providers-properties`

## 17. References

- https://kotlinlang.org/docs/type-safe-builders.html
- https://kotlinlang.org/docs/lambdas.html
- https://kotlinlang.org/docs/fun-interfaces.html
- https://kotlinlang.org/docs/scope-functions.html
- https://docs.gradle.org/current/userguide/kotlin_dsl.html
