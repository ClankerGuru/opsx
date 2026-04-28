---
name: kotlin-lang
description: Kotlin language reference — types, null safety, classes, functions, scope functions, collections, generics, delegation, exceptions.
---

# Kotlin Language

A working-grade reference for idiomatic Kotlin on the JVM. Covers the language features you reach for daily and the subtleties that separate fluent Kotlin from Java-in-Kotlin.

Source:
- https://kotlinlang.org/docs/home.html
- https://kotlinlang.org/docs/coding-conventions.html

## 1. Basics

### Declarations

```kotlin
val x = 42                          // read-only (immutable reference)
var y = "mut"                       // mutable reference
const val MAX = 100                 // compile-time constant, top-level or in object

val n: Int = 1                      // explicit type
val xs: List<Int> = listOf(1, 2, 3)
val f: (Int) -> Int = { it * 2 }    // function type
```

Rule: `val` everywhere; `var` only when mutation is essential and local.

### Type inference

Kotlin infers everywhere — `val x = 1` is `Int`, `val s = "x"` is `String`, `val fn = { n: Int -> n + 1 }` is `(Int) -> Int`. Annotate types on public API for stability; keep locals inferred.

### Nothing and Unit

- `Unit` — the void-like return, a singleton. `fun f(): Unit {}` == `fun f() {}`.
- `Nothing` — "no value, ever". The type of `throw`, `error()`, `TODO()`. Subtype of every type, so `fun fail(): Nothing = throw X()` can appear anywhere a value is expected.

### Primitive types

`Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `Boolean`, `Char`. Unsigned: `UByte`, `UShort`, `UInt`, `ULong`. On the JVM these box when used as generic type arguments (`List<Int>` is `List<Integer>`). For performance-critical homogeneous arrays, use `IntArray`, `LongArray`, etc.

## 2. Null safety

### Nullable and non-null

```kotlin
val s: String = "x"          // non-null
val t: String? = null        // nullable
// t.length                  // error — must handle null
```

Operators:

```kotlin
t?.length                    // safe call: Int?, null if t is null
t?.length ?: 0               // Elvis: default value
t!!                          // non-null assert; throws NPE if null (avoid)
t?.let { process(it) }       // block runs only if non-null
t?.also { log(it) }          // side-effect, returns t
val l = t?.length ?: return  // early-return on null
val l = t?.length ?: error("need t")
```

### Smart casts

After a null check, the compiler narrows the type:

```kotlin
fun describe(s: String?) {
    if (s != null) {
        println(s.length)    // s: String here
    }
    s?.let { println(it.length) }    // it: String inside
}
```

Smart casts only work for stable declarations (`val`, local `var`, private `var`). Non-private mutable properties require `s?.let` or a local copy.

### `lateinit` and `by lazy`

```kotlin
lateinit var service: Service               // non-null var, set later (DI, setUp)
val config by lazy { loadConfig() }         // computed on first access, cached
```

`lateinit` cannot be a primitive or nullable. `::prop.isInitialized` tests if set.

### Platform types

Java types appear as `String!` in compiler messages — nullability unknown. Annotate them: treat JDK `String` as `String?` unless the Java API guarantees non-null (`@NotNull`).

## 3. Classes and objects

### Classes

```kotlin
class Person(val name: String, var age: Int) {
    fun greet() = "hi, $name"

    init { require(age >= 0) }                 // runs after primary constructor
}
```

Primary constructor parameters annotated `val`/`var` become properties. Others are constructor-only.

### Secondary constructors

```kotlin
class Person(val name: String) {
    constructor(name: String, title: String) : this("$title $name")
}
```

Prefer default params on the primary constructor over overloads.

### Visibility

`public` (default), `internal` (module-visible), `protected` (subclasses), `private` (file- or class-scoped). `internal` is module-level — typical for libraries to hide impl while allowing tests.

### `data class`

```kotlin
data class User(val id: String, val name: String)
val u = User("1", "Sam")
val v = u.copy(name = "Ada")
val (id, name) = u              // destructuring
u == User("1", "Sam")           // structural equality
```

Auto-generated: `equals`, `hashCode`, `toString`, `copy`, `componentN`. Requires at least one primary-constructor property.

### `object` (singleton)

```kotlin
object Clock {
    fun now() = System.currentTimeMillis()
}
```

Instantiated once, thread-safely on first access. Use for stateless helpers, registries, pure singletons.

### `companion object`

Static-like members tied to a class:

```kotlin
class User(val id: String) {
    companion object {
        fun parse(raw: String) = User(raw.trim())
    }
}
User.parse("x")                  // User.Companion.parse
```

### `data object`

```kotlin
sealed interface Status {
    data object Ready : Status
    data object Busy : Status
}
```

Gets a readable `toString()` and structural `equals` — mainly useful in sealed hierarchies.

### `enum class`

```kotlin
enum class Direction(val dx: Int, val dy: Int) {
    NORTH(0, -1), SOUTH(0, 1), EAST(1, 0), WEST(-1, 0);

    fun opposite() = when (this) {
        NORTH -> SOUTH; SOUTH -> NORTH; EAST -> WEST; WEST -> EAST
    }
}
Direction.values()              // array of all
Direction.valueOf("NORTH")      // parse
```

### `sealed class` / `sealed interface`

Closed hierarchies with exhaustive `when`:

```kotlin
sealed interface Result<out T> {
    data class Ok<T>(val value: T) : Result<T>
    data class Err(val msg: String) : Result<Nothing>
    data object Loading : Result<Nothing>
}

fun show(r: Result<String>) = when (r) {       // no `else` required
    is Result.Ok -> r.value
    is Result.Err -> "error: ${r.msg}"
    Result.Loading -> "loading"
}
```

Subclasses must live in the same module and package (sealed classes) or module (sealed interfaces, Kotlin 1.5+).

### `value class`

```kotlin
@JvmInline
value class UserId(val raw: String)
```

Zero-allocation wrapper over a single property. Compiled to the underlying type at call sites where possible. Use for domain primitives (ID types, money, paths) to gain type safety without runtime cost.

Restrictions: no `init` block with side effects on a serializable type, primary constructor with exactly one `val` property, no mutable state.

### `abstract class` vs interface

Interfaces can have default methods and `val` (abstract properties). Classes can have state. Prefer interface + composition; reach for `abstract class` only when you need constructor parameters or stored state.

### Nested and inner classes

```kotlin
class Outer {
    class Nested                   // no reference to Outer (like Java static class)
    inner class Inner              // has reference to Outer via this@Outer
}
```

Default `class` inside `class` is nested (static). Prefix `inner` to capture the outer instance.

## 4. Functions

### Syntax

```kotlin
fun add(a: Int, b: Int): Int = a + b
fun greet(name: String = "world"): String = "hi, $name"
fun log(msg: String, tag: String = "INFO") { println("[$tag] $msg") }

log(msg = "boot", tag = "SYS")          // named args
```

Default params + named args replace Java-style overloads in almost all cases.

### Expression body

```kotlin
fun double(n: Int) = n * 2
fun User.fullName() = "$firstName $lastName"
```

Shorter, forces a single expression. Prefer for one-liners.

### Top-level functions

Functions live in files, not classes. Use top-level functions for utilities:

```kotlin
// Strings.kt
fun String.capitalizeAscii(): String = if (isEmpty()) this else first().uppercase() + drop(1)
```

No `StringUtils` helpers needed.

### Extension functions

```kotlin
fun String.countVowels(): Int = count { it.lowercaseChar() in "aeiou" }
"hello".countVowels()
```

Resolution is static — based on declared type, not runtime class. Extensions can shadow members in callers but never override them.

### Infix functions

```kotlin
infix fun Int.pow(n: Int): Int { var r = 1; repeat(n) { r *= this }; return r }
2 pow 10                         // 1024
```

Only for naturally binary, side-effect-free ops.

### Operators

```kotlin
data class Vec(val x: Int, val y: Int) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)
    operator fun get(i: Int) = if (i == 0) x else y
    operator fun component1() = x
    operator fun component2() = y
}
```

Named operators: `plus`, `minus`, `times`, `div`, `rem`, `inc`, `dec`, `unaryMinus`, `not`, `compareTo`, `contains`, `get`, `set`, `invoke`, `rangeTo`, `iterator`, `plusAssign`, …

### `inline`, `noinline`, `crossinline`, `reified`

```kotlin
inline fun <reified T> Any.castTo(): T = this as T
inline fun measure(block: () -> Unit) {
    val t0 = System.nanoTime()
    block()
    println("${(System.nanoTime() - t0) / 1_000} us")
}
```

- `inline` — inlines the function body and lambda parameters at call sites. Enables `reified` and non-local `return`.
- `noinline` — opts a specific lambda out of inlining (needed to store it).
- `crossinline` — bans non-local returns from a lambda that's called indirectly.
- `reified` — makes the type parameter available as a real `Class<T>` at runtime. Only inside `inline fun`.

Use `inline` for small, hot higher-order functions and DSLs. Don't inline large functions — it bloats bytecode.

### `tailrec`

```kotlin
tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
```

Compiler rewrites self-tail-calls into a loop. The recursive call must be in tail position.

### Local functions

```kotlin
fun process(items: List<Int>) {
    fun normalize(x: Int) = x.coerceIn(0, 100)
    items.map(::normalize)
}
```

Shorthand for helpers that aren't reusable.

## 5. Scope functions

| Fn      | Receiver | Returns       | Typical use                              |
|---------|----------|---------------|-------------------------------------------|
| `let`   | `it`     | lambda result | Transform / null-guard                    |
| `also`  | `it`     | receiver      | Side effect, keep value                   |
| `apply` | `this`   | receiver      | Configure a mutable object                |
| `run`   | `this`   | lambda result | Compute from receiver's members           |
| `with`  | `this`   | lambda result | Same as `run`, non-extension              |

```kotlin
val len = raw?.let { it.trim().length }
val ids = load().also { logger.info("n=${it.size}") }
val req = Request().apply { url = "x"; method = "POST" }
val key = config.run { host.hashCode() xor port }
val summary = with(user) { "$name <$email>" }
```

Rules:
- One scope per block. Nested `apply { apply { } }` is a smell.
- `?.let` over `if (x != null) ...` when result is consumed; otherwise plain `if`.
- `also` vs `apply`: `apply` for fluent config, `also` for peek/log.

## 6. Control flow as expressions

```kotlin
val label = if (n > 0) "pos" else if (n < 0) "neg" else "zero"

val s = when (n) {
    0 -> "zero"
    in 1..9 -> "small"
    !in 0..100 -> "out of range"
    else -> "big"
}

val first = try { parse(raw) } catch (e: Exception) { null }
```

`if`, `when`, `try` all return values.

### `when`

```kotlin
when (x) {
    null -> "nothing"
    is String -> x.length          // smart cast
    in 0..10 -> "low"
    else -> "other"
}

when {                              // no subject: boolean branches
    x > 10 -> "big"
    x > 0 -> "pos"
    else -> "other"
}
```

Exhaustive on sealed types and enums without `else`.

### Ranges and loops

```kotlin
for (i in 0..9) { }              // 0..9 inclusive
for (i in 0 until 10) { }        // 0..9 exclusive upper
for (i in 10 downTo 1) { }
for (i in 0..10 step 2) { }
for ((k, v) in map) { }
```

`while` and `do...while` as usual. `break@label` / `continue@label` with named loops.

## 7. Collections

### Immutable / mutable

```kotlin
val ro: List<Int> = listOf(1, 2, 3)
val rw: MutableList<Int> = mutableListOf(1, 2, 3)
val s: Set<Int> = setOf(1, 2)
val m: Map<String, Int> = mapOf("a" to 1, "b" to 2)

buildList { add(1); add(2) }     // read-only result
buildMap { put("a", 1) }
```

Prefer read-only types in APIs. Returning `MutableList` invites shared mutation.

### Operators

```kotlin
xs.filter { it > 0 }
xs.map { it * 2 }
xs.flatMap { it.children }
xs.groupBy { it.key }
xs.associate { it.id to it.name }
xs.associateBy { it.id }
xs.sortedBy { it.age }
xs.sortedByDescending { it.age }
xs.partition { it.active }           // (List<T>, List<T>)
xs.windowed(3, 1)
xs.chunked(10)
xs.zip(ys) { a, b -> a + b }
xs.fold(0) { acc, x -> acc + x }
xs.reduce { acc, x -> acc + x }
xs.sumOf { it.cost }
xs.maxByOrNull { it.score }
xs.firstOrNull { it.matches }
xs.count { it.isValid }
xs.any { it.error != null }
xs.all { it.ok }
xs.none { it.banned }
xs.distinct() / .distinctBy { }
xs.takeWhile { }; xs.dropWhile { }
xs + other; xs - other
```

### Sequences

```kotlin
xs.asSequence()
  .filter { it.ok }
  .map { transform(it) }
  .take(10)
  .toList()
```

Lazy, one-pass, no intermediate allocations. Use for long pipelines or large sources; plain operators beat sequences for short pipelines on small lists.

### `buildList` / `buildMap` / `buildSet`

```kotlin
val xs = buildList {
    add(1)
    addAll(source)
    if (extra) add(99)
}
```

Constructs a read-only result via a mutable builder. Cleaner than mapping a mutable-list-and-`toList`.

## 8. Generics

### Variance

```kotlin
interface Source<out T> { fun next(): T }       // covariant (produces T)
interface Sink<in T> { fun consume(value: T) }  // contravariant (consumes T)
interface Cache<T>                              // invariant (both)
```

- `out T` — producer; `T` can appear only in return positions. `Source<Cat>` is assignable to `Source<Animal>`.
- `in T` — consumer; `T` only in parameter positions. `Sink<Animal>` is assignable to `Sink<Cat>`.
- No modifier — invariant.

Use-site projections:

```kotlin
fun copy(from: List<out Any>, to: MutableList<in Any>) { }
```

### Reified

Works in `inline fun` only:

```kotlin
inline fun <reified T> List<*>.filterIsInstanceKt(): List<T> =
    filter { it is T }.map { it as T }
```

### Bounded

```kotlin
fun <T : Comparable<T>> max(a: T, b: T) = if (a > b) a else b
fun <T> T.requireOneOf(vararg values: T) where T : Any {
    require(this in values)
}
```

## 9. Delegation

### Class delegation

```kotlin
class LoggingList<T>(private val inner: MutableList<T>) : MutableList<T> by inner {
    override fun add(element: T): Boolean {
        println("add $element")
        return inner.add(element)
    }
}
```

Generates forwarding methods to `inner` for everything in `MutableList<T>`; override the ones you want to customise.

### Property delegation

```kotlin
class Model {
    var value: String by MyDelegate()
    val lazily by lazy { compute() }
    val args: String by map                   // delegate to a Map
    var name: String by Delegates.observable("") { _, old, new -> log(old, new) }
    var count: Int by Delegates.vetoable(0) { _, _, new -> new >= 0 }
}
```

Standard delegates: `lazy`, `Delegates.observable`, `Delegates.vetoable`, `Delegates.notNull` (lateinit alternative for generics). Write your own by implementing `getValue`/`setValue` (operator functions).

## 10. Destructuring

```kotlin
val (x, y) = point
val (name, age) = user
for ((k, v) in map) { }
fun parse(): Pair<Int, Int> = 1 to 2
val (a, b) = parse()
```

Any type with `componentN` operators works — `data class` gets them for free.

## 11. Exceptions and `Result`

### Preconditions

```kotlin
require(amount > 0) { "amount must be positive: $amount" }     // IllegalArgumentException
check(state == Ready)                                           // IllegalStateException
requireNotNull(x) { "x must not be null" }
checkNotNull(x)
error("unreachable")                                            // IllegalStateException, Nothing
```

`require` for inputs; `check` for invariants; `error` / `throw` where `Nothing` is wanted.

### `runCatching`

```kotlin
val r: Result<Config> = runCatching { loadConfig() }
r.getOrNull()
r.getOrElse { e -> fallback }
r.getOrDefault(Config.Empty)
r.fold(onSuccess = { it }, onFailure = { Config.Empty })
r.onFailure { log.warn("load failed", it) }
r.onSuccess { init(it) }
r.map { it.port }
```

Prefer `runCatching` over `try/catch` when the control flow is "compute a value, maybe fail, decide what to do". Keep `try`/`catch` for narrow technical handling (resource cleanup, specific exception types).

Caveat: `runCatching` captures `CancellationException` too. Inside coroutines, rethrow it:

```kotlin
runCatching { work() }.onFailure { if (it is CancellationException) throw it }
```

### try / catch / finally

```kotlin
try { risky() } catch (e: IOException) { handle(e) } finally { cleanup() }

val x: Int = try { raw.toInt() } catch (_: NumberFormatException) { 0 }
```

### `use`

```kotlin
Files.newBufferedReader(path).use { reader ->
    reader.lineSequence().forEach(::println)
}
```

Closes the `Closeable` on exit, including on exception. The Kotlin answer to try-with-resources.

## 12. Strings

```kotlin
"hello, $name"                               // template
"sum = ${a + b}"
"""triple quoted
spans lines""".trimIndent()

"a".repeat(3)                                // "aaa"
"abc".substring(0, 2)
"abc".take(2); "abc".drop(2)
"abc".split(",")
"abc".padStart(5, '0')                       // "00abc"
"""
    |line 1
    |line 2
""".trimMargin()
```

Regex:

```kotlin
val r = Regex("""\d{3}-\d{4}""")
val m = r.matchEntire("555-1234")
m?.groupValues
r.find("x 555-1234 y")
```

## 13. Numbers

```kotlin
1_000_000                                    // underscore separators allowed
0xFF; 0b1010
1.toLong(); 2.0.toInt()                      // explicit narrowing
Int.MAX_VALUE; Double.NaN
n.coerceIn(0, 100)
n.coerceAtLeast(0); n.coerceAtMost(100)
(1..100).random()
```

No implicit widening — `val x: Long = 1` fails; write `1L` or `.toLong()`.

## 14. Type checks and casts

```kotlin
if (x is String) x.length                     // smart cast
val s = x as String                           // throws ClassCastException
val s = x as? String                          // null on failure
val list = xs as List<String>                 // unchecked (erasure)
```

## 15. `typealias`

```kotlin
typealias UserMap = Map<UserId, User>
typealias Handler<T> = (T) -> Unit
typealias ID = String                         // domain alias; not a new type
```

A type alias is a compile-time rename — not a distinct type. For real type safety use `value class`.

## 16. Annotations

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Experimental(val since: String)

@Experimental(since = "1.2")
class Thing
```

JVM targets: `@JvmStatic`, `@JvmField`, `@JvmOverloads`, `@JvmName`, `@Throws(IOException::class)` for Java interop.

## 17. Reflection

```kotlin
val cls: KClass<User> = User::class
val prop: KProperty1<User, String> = User::name
prop.get(user)
val fn: KFunction1<User, String> = User::greet
fn.call(user)
```

Requires `kotlin-reflect.jar` on the classpath for full reflection. `KClass`, `KProperty`, `KFunction` are the Kotlin-native reflection types; bridge to Java via `.java`.

## 18. Java interop

- JDK collections map transparently: Java `List<String>` is Kotlin `List<String>!` or `(Mutable)List<String>`.
- SAM conversions — a Kotlin lambda where a Java `Runnable`/`Callable` is expected works automatically.
- `@JvmStatic` in companion objects promotes to a real static method.
- `@file:JvmName("Utils")` renames the generated class for a top-level-function file.
- `@JvmOverloads` on a function with defaults generates Java-visible overloads.
- Properties with `lateinit` and `@JvmField` appear as real fields in Java.

## 19. Coroutines (pointer)

`suspend fun`, `CoroutineScope`, `Flow` — covered in depth under `/kotlinx-coroutines` and the `coroutines-*` sub-skills. In short: a `suspend` function can pause at `delay`, `withContext`, or another `suspend` call without blocking a thread; scopes enforce structured concurrency.

## 20. Idioms and anti-patterns

### Do
- `val` everywhere; `var` only when truly local mutation.
- `data class` for value carriers, `sealed` for closed hierarchies, `object` for stateless singletons.
- Default args + named args over overloads.
- Top-level functions over `FooUtils` classes.
- `?.let` / `?:` / `?: return` / `?: error` instead of `if (x != null)` chains.
- `runCatching` for composable error handling; rethrow `CancellationException` inside coroutines.
- Expression bodies for one-liners.
- `expect`/`actual` only for multiplatform primitives; DI via interfaces everywhere else.

### Don't
- `!!` to silence the compiler — fix the type.
- `lateinit var` unless DI-injected or test-setUp.
- `MutableList` / `MutableMap` in public API without reason.
- `when` without exhaustiveness check (not sealed, no `else`).
- Manager/Handler/Service/Processor class names — rename the abstraction.
- `Exception` as a normal control-flow signal; use `Result` or a sealed type.
- Mixing `Stream` (Java) and Kotlin collection ops in the same chain.
- `runBlocking` inside suspend code or request handlers.
- `GlobalScope` — always launch from a real scope.

## 21. Related skills

- Functional style defaults → `/kotlin-functional-first`
- DSL design (receivers, `@DslMarker`) → `/kotlin-dsl-builders`
- Project-specific conventions (forbidden patterns, naming) → `/kotlin-conventions`
- Coroutines → `/kotlinx-coroutines` + `/coroutines-*`
- Serialization → `/kotlinx-serialization`
- Multiplatform → `/kotlin-multiplatform`
