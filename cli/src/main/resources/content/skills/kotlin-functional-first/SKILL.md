---
name: kotlin-functional-first
description: Functional-first Kotlin — top-level functions, unidirectional data flow, sealed hierarchies, value classes, pure functions, composition over inheritance.
---

# Kotlin Functional-First

The bias: any flavour of FP beats OO; unidirectional data flow beats object graphs. This skill gives the design defaults for structuring new Kotlin code.

## 1. When to apply

- Introducing a new module, file, or type.
- Refactoring code that leans on Manager/Service/Helper/Util classes, Impl suffixes, or mutable object graphs.
- Deciding between `class`, `data class`, `sealed interface`, `object`, and `value class`.
- Designing an API that will be consumed by other Kotlin modules — you want values in, values out, pure transforms.

## 2. Core rule: function before class

If there's no mutable state to carry across calls, it's a function.

```kotlin
// Good
fun parseUserId(raw: String): UserId? =
    raw.takeIf { it.isNotBlank() }?.let(::UserId)

// Bad
class UserIdParser {
    fun parse(raw: String): UserId? { /* ... */ }
}
```

Stateless groupings belong in a file, not a class:

```kotlin
// Paths.kt
fun normalize(path: String): String = ...
fun resolveAgainst(base: String, rel: String): String = ...
fun isAbsolute(path: String): Boolean = ...
```

No `PathUtils`, no `PathHelper`. Kotlin has no need for the Java static-class workaround.

## 3. Unidirectional data flow

Data in → pure transforms → data out. Push effects to the edge; the core stays pure and testable.

```kotlin
// Core (pure)
fun renderInvoice(order: Order, now: Instant): Invoice = ...

// Edge (effects)
suspend fun emitInvoice(orderId: OrderId) {
    val order   = orders.load(orderId)            // IO in
    val invoice = renderInvoice(order, now())     // pure transform
    mailer.send(invoice)                          // IO out
}
```

The pure function is trivially testable with concrete inputs. The edge function is small and rarely changes once the pipeline is right.

Pipelines compose with extension functions, `let`, and `Flow`:

```kotlin
fun Flow<RawEvent>.toDomain(): Flow<DomainEvent> =
    filterNot { it.isHeartbeat }
        .map(::parseEvent)
        .filterIsInstance<DomainEvent>()
```

## 4. Pure functions first

A pure function:
- Does not mutate arguments.
- Has no hidden I/O — no reads from `System.currentTimeMillis()`, no file access, no global state. (Use `Clock`, pass dependencies explicitly.)
- Returns the same output for the same input.

```kotlin
// Pure
fun discount(total: Money, coupon: Coupon): Money =
    (total - coupon.amount).coerceAtLeast(Money.ZERO)

// Effectful — clearly marked suspend
suspend fun applyAndCharge(cart: Cart, coupon: Coupon): Receipt { ... }
```

### Inject time, randomness, IO

```kotlin
class Signer(private val clock: Clock = Clock.System, private val rng: Random = Random.Default) {
    fun sign(req: Request): Signed = Signed(req, clock.now(), rng.nextLong())
}
```

Tests pass a fixed `Clock` and deterministic `Random` — no mocks needed.

## 5. Sealed hierarchies over enums when cases carry data

Enums work for flat, dataless alternatives. The moment a case needs a payload, use `sealed`.

```kotlin
sealed interface FetchResult<out T> {
    data class Ok<T>(val value: T) : FetchResult<T>
    data class NotFound(val key: String) : FetchResult<Nothing>
    data class Failed(val cause: Throwable) : FetchResult<Nothing>
}

fun <T> FetchResult<T>.orNull(): T? = when (this) {
    is FetchResult.Ok -> value
    is FetchResult.NotFound, is FetchResult.Failed -> null
}
```

Exhaustive `when` replaces polymorphism: the compiler forces you to handle every case at every branch.

### `sealed interface` beats `sealed class`

Interfaces allow multi-hierarchy membership and don't reserve the constructor. Use `sealed class` only if you need stored state or a non-empty primary constructor shared across all variants.

## 6. Value classes for domain primitives

Any primitive that carries a domain identity gets wrapped in `@JvmInline value class`. Zero runtime cost, full type safety.

```kotlin
@JvmInline value class UserId(val raw: String)
@JvmInline value class OrderId(val raw: String)
@JvmInline value class Money(val cents: Long) {
    operator fun plus(o: Money) = Money(cents + o.cents)
}

fun load(user: UserId): Order? = ...
// load(order.raw)   // compile error — prevents a whole class of bugs
```

Add `init { require(...) }` for invariants where useful:

```kotlin
@JvmInline
value class Email(val raw: String) {
    init { require("@" in raw) { "invalid email: $raw" } }
}
```

## 7. Data classes as the default value carrier

```kotlin
data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

val authed = req.copy(headers = req.headers + ("Authorization" to token))
```

Rules:
- `copy()` for derivation — never a builder class.
- Prefer read-only types (`Map<K,V>`, `List<T>`) in fields.
- `ByteArray` equals is identity-based — override `equals`/`hashCode` if you need structural equality on bytes.

## 8. Functional interfaces and function types

Prefer a function-type alias for simple callbacks; a `fun interface` for named single-method types.

```kotlin
typealias Validator<T> = (T) -> Result<T>

fun interface Transform<T, R> {
    fun apply(value: T): R
}

fun <T, R> List<T>.mapWith(t: Transform<T, R>) = map { t.apply(it) }
```

Don't reach for `fun interface` when a plain function type reads better. Use it when SAM conversions to a Java consumer matter or when the type is referenced by name widely.

## 9. Composition over inheritance

- Extension functions first. Add behaviour to a type without subclassing.
- Delegation (`by`) when state delegation is genuinely needed.
- Decorators as functions returning the wrapped function.

```kotlin
// Decorator as a higher-order function
fun <T, R> ((T) -> R).timed(label: String, log: (String) -> Unit): (T) -> R = { input ->
    val t0 = System.nanoTime()
    val out = this(input)
    log("$label=${(System.nanoTime() - t0) / 1_000}us")
    out
}

val lookup: (UserId) -> User? = ::loadUser
val timedLookup = lookup.timed("loadUser", logger::info)

// Delegation where it earns its keep
class CachingLoader(
    private val inner: Loader,
    private val cache: Cache,
) : Loader by inner {
    override fun load(id: Id) = cache[id] ?: inner.load(id).also { cache[id] = it }
}
```

`Loader by inner` generates forwarding methods for everything in `Loader`; override just the ones you customise.

## 10. Result types over exceptions

For recoverable errors, return a `Result<T>` or a domain-specific sealed type. Exceptions only for truly exceptional, unrecoverable situations (programmer error, OOM, interruption).

### `Result<T>` — the built-in

```kotlin
fun parse(input: String): Result<Parsed> = runCatching { parseOrThrow(input) }

parse(x).fold(
    onSuccess = { handle(it) },
    onFailure = { log.warn("bad input", it) },
)
```

Limitations of `Result`:
- Only one failure type (`Throwable`).
- Can't model expected outcomes like "not found" vs "forbidden" distinctly.

### Sealed error types

```kotlin
sealed interface ParseError {
    data object Empty : ParseError
    data class Malformed(val at: Int, val reason: String) : ParseError
}

sealed interface Parsed {
    data class Ok(val value: Expr) : Parsed
    data class Err(val error: ParseError) : Parsed
}

fun parse(input: String): Parsed =
    if (input.isEmpty()) Parsed.Err(ParseError.Empty)
    else runCatching { parseOrThrow(input) }
        .fold({ Parsed.Ok(it) }, { Parsed.Err(ParseError.Malformed(0, it.message.orEmpty())) })
```

### Arrow

For richer error handling (monadic `Either`, validation accumulation), Arrow's `Either<L, R>` is the standard.

```kotlin
fun parse(input: String): Either<ParseError, Parsed> = either {
    ensure(input.isNotEmpty()) { ParseError.Empty }
    parseOrFail(input).bind()
}
```

## 11. Collections as transforms

Pipelines over loops, `fold` over mutable accumulators.

```kotlin
// Good
val topSpenders: List<String> = orders
    .filter { it.total > 100.usd }
    .groupBy { it.customerId }
    .mapValues { (_, v) -> v.sumOf { it.total } }
    .entries
    .sortedByDescending { it.value }
    .take(10)
    .map { it.key }

// Bad
val m = mutableMapOf<CustomerId, Money>()
for (o in orders) {
    if (o.total > 100.usd) m.merge(o.customerId, o.total) { a, b -> a + b }
}
// ... continue with more mutation ...
```

`asSequence()` when the pipeline is long or the source is large. Plain `.filter { }.map { }` is simpler and faster for short pipelines on small lists.

## 12. Immutability defaults

- `val` everywhere. `var` only when mutation is local and essential.
- Read-only types (`List`, `Set`, `Map`) in APIs. Never return `MutableList` unless the caller is supposed to mutate.
- `data class` + `copy()` over builders.
- Append-only state reductions over mutable accumulators:

```kotlin
// Good
fun reduce(state: LogState, e: Event): LogState =
    state.copy(events = state.events + e)

// Bad
class EventLog {
    val events: MutableList<Event> = mutableListOf()
    fun record(e: Event) { events += e }
}
```

## 13. Top-level functions over stateless classes

```kotlin
// Bad: stateless service
class UserValidationService {
    fun validate(user: User): Boolean = user.email.contains("@")
}

// Good: extension function, no object to wire
fun User.isValid(): Boolean = email.contains("@")
```

If DI is the only reason a class exists, it's probably not worth the class — inject the function type directly:

```kotlin
class SignIn(private val validate: (User) -> Boolean) {
    fun go(u: User) = if (validate(u)) ok() else error()
}
```

## 14. Object identity vs value identity

Use value equality (`==` / `data class`) for values. Use reference equality (`===`) only for singletons and interned handles.

```kotlin
user1 == user2        // structural (data class gives you this)
user1 === user2       // same instance (rare)
```

Avoid mutable types as map keys — their `hashCode` changes as they mutate.

## 15. Testing benefits

Functional-first code tests itself without mocks:

```kotlin
test("discount floors at zero") {
    val total  = Money(5_00)
    val coupon = Coupon(amount = Money(10_00))
    discount(total, coupon) shouldBe Money.ZERO
}
```

When `discount` is a pure function, the test is data → result → assertion. No test doubles, no fixtures, no lifecycle.

## 16. When OO does earn its keep

- Long-lived stateful components with a well-defined lifecycle (ViewModels, connection pools, HTTP servers).
- Implementations behind an interface where multiple strategies exist (encoders, loggers, transports).
- Inheritance from a framework type you don't control (`AbstractVerticle`, `ViewModel`, `ApplicationListener`).

In all three cases, the public surface should still be interface-first and the implementation should delegate where possible.

## 17. Avoid (non-exhaustive)

- Abstract base classes carrying default impls when an extension function would do.
- Public `MutableList` / `MutableMap` properties — expose read-only views, or expose builder methods that mutate internally.
- Builder patterns when `copy()` on a data class covers it.
- `Manager`, `Handler`, `Service`, `Processor` classes with no per-instance state — these are placeholders for missing abstractions.
- Throwing exceptions across module boundaries for expected outcomes.
- `lateinit var` in a data class or a value carrier — the object is now half-constructed and hashcode/equals become footguns.

## 18. Anti-pattern gallery

```kotlin
// Bad: enum + when that wants payload
enum class PaymentStatus { PAID, FAILED, PENDING }
fun message(s: PaymentStatus, amount: Money?, err: String?) = ...

// Good: sealed interface carries its own payload
sealed interface PaymentStatus {
    data class Paid(val amount: Money) : PaymentStatus
    data class Failed(val reason: String) : PaymentStatus
    data object Pending : PaymentStatus
}
```

```kotlin
// Bad: builder with mutation
class RequestBuilder {
    var url: String = ""
    var method: String = "GET"
    fun build() = HttpRequest(method, url)
}

// Good: data class + defaults + factory
data class HttpRequest(val method: String = "GET", val url: String)
fun get(url: String) = HttpRequest(url = url)
```

```kotlin
// Bad: service class that could be a function
class CurrencyConverter(private val rates: Rates) {
    fun convert(amount: Money, to: Currency): Money = ...
}
// (used from one place)

// Good: pass the function
fun convert(amount: Money, to: Currency, rates: Rates): Money = ...
```

## 19. References

- Kotlin coding conventions — https://kotlinlang.org/docs/coding-conventions.html
- Kotlin idioms — https://kotlinlang.org/docs/idioms.html
- Value classes — https://kotlinlang.org/docs/inline-classes.html
- Sealed classes and interfaces — https://kotlinlang.org/docs/sealed-classes.html
- Functional (SAM) interfaces — https://kotlinlang.org/docs/fun-interfaces.html
- Arrow (Either, Option, Validated) — https://arrow-kt.io/

## 20. Related skills

- Language primer → `/kotlin-lang`
- DSL design → `/kotlin-dsl-builders`
- Project conventions → `/kotlin-conventions`
- Serialization → `/kotlinx-serialization`
