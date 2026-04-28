---
name: kotlinx-coroutines
description: Reference guide for Kotlin coroutines covering structured concurrency, suspend functions, dispatchers, cancellation, SupervisorJob, Flow, StateFlow/SharedFlow, coroutine context, exception handling, testing with runTest, and common pitfalls. Activates when writing async/concurrent Kotlin code.
---

# kotlinx.coroutines

> For deeper-dive sub-skills see:
> - `/coroutines-patterns` — project-specific patterns enforced in this codebase
> - `/kotlin-lang` — language prerequisites (`suspend`, inline/crossinline)
> - `/kotlin-functional-first` — composing pure functions with Flow
> - `/testing-patterns` — how coroutine tests fit into the suite
> - `/kotest` — runBlocking vs runTest inside Kotest

## When to use this skill

Any Kotlin code that performs I/O, waits on another computation, or exposes
an asynchronous stream. Coroutines replace callbacks, futures, `RxJava`, and
raw threads with suspend functions that read like sequential code but run
concurrently.

**Use coroutines for:**
- I/O: HTTP, database, file system, IPC.
- Concurrent computation: parallel fan-out / fan-in.
- Async streams: UI state, WebSocket messages, database change notifications.
- Cancellation-sensitive work: search-as-you-type, long-polling.

**Don't use coroutines for:**
- Pure CPU-bound algorithms that never block — ordinary functions suffice.
- Simple fire-and-forget `Runnable` in a framework that already has its
  own executor (a `@Scheduled` method in Spring, an Android `WorkManager`
  job) — let the framework manage concurrency.

## Structured concurrency — the mental model

Every coroutine runs inside a `CoroutineScope`. The scope's `Job` is the
parent of all coroutines launched from it. Three invariants follow:

1. **Parent waits for children** — a scope doesn't complete until every
   child completes.
2. **Parent failure cancels children** — an exception in the parent (or
   any child, by default) cancels siblings.
3. **Cancellation propagates down** — cancelling a parent cancels every
   descendant.

This eliminates leaks: a coroutine cannot outlive its scope.

```kotlin
suspend fun loadPage(): Page = coroutineScope {
    val header = async { fetchHeader() }
    val body = async { fetchBody() }
    Page(header.await(), body.await())
}
```

`coroutineScope { }` is a **suspending builder**: it suspends the caller
until every child finishes. If any child throws, it cancels siblings and
rethrows.

### coroutineScope vs CoroutineScope

- `coroutineScope { }` (function) — creates a short-lived child scope for
  a unit of work. Use inside suspend functions.
- `CoroutineScope(...)` (factory) — creates a long-lived scope tied to a
  component (Android ViewModel, server service, daemon). Cancel it on
  shutdown.
- `supervisorScope { }` — like `coroutineScope` but child failures don't
  cancel siblings.
- `withContext(ctx) { }` — run a block on a different context; does **not**
  launch a new coroutine, just switches context.

```kotlin
class OrderService : CoroutineScope by CoroutineScope(
    SupervisorJob() + Dispatchers.Default + CoroutineName("order-service")
) {
    fun shutdown() = cancel()
}
```

## Launching work: launch vs async vs withContext

| Builder | Returns | Use when |
|---|---|---|
| `launch { }` | `Job` | Fire-and-forget side effect; no return value |
| `async { }` | `Deferred<T>` | Run concurrently, combine results via `await()` |
| `withContext(ctx) { }` | `T` | Switch dispatcher for a block; sequential |
| `runBlocking { }` | `T` | Bridge non-suspend → suspend at a boundary (main, tests) |

```kotlin
// Fire and forget
scope.launch { repository.sync() }

// Concurrent fan-out / fan-in
coroutineScope {
    val a = async { computeA() }
    val b = async { computeB() }
    a.await() + b.await()
}

// Dispatcher switch (does NOT create a new coroutine)
suspend fun readConfig(): Config = withContext(Dispatchers.IO) {
    Files.readString(path).let(::parseConfig)
}
```

**Anti-pattern:** `async { ... }.await()` with nothing else running
concurrently — it's a more expensive suspend call. Use `withContext` or a
plain suspend call.

## Dispatchers

A `CoroutineDispatcher` decides which thread (or thread pool) runs a
coroutine when it's resumable.

| Dispatcher | Pool | Use for |
|---|---|---|
| `Dispatchers.Default` | CPU-count threads | CPU-bound work |
| `Dispatchers.IO` | Elastic, up to 64 threads by default | Blocking I/O — files, JDBC, Apache HttpClient |
| `Dispatchers.Main` | UI thread | Android / Swing / JavaFX UI |
| `Dispatchers.Main.immediate` | UI thread if already on it, else dispatch | Avoid redundant re-dispatch in UI |
| `Dispatchers.Unconfined` | Caller thread until first suspension | Rarely correct; tests and advanced cases only |

**Rules:**

- Route blocking I/O through `Dispatchers.IO`. Never put blocking calls on
  `Dispatchers.Default` — they starve CPU-bound coroutines.
- Switch dispatchers with `withContext`, not `launch` — `withContext` is
  sequential; `launch` starts a separate coroutine.
- `Dispatchers.Main` requires `kotlinx-coroutines-android` (or `-swing`,
  `-javafx`) on the classpath. Check `Dispatchers.Main.isSupported` in
  tests.
- You can size `Dispatchers.IO` via the `kotlinx.coroutines.io.parallelism`
  property or `Dispatchers.IO.limitedParallelism(n)`.

### Custom dispatchers

```kotlin
val dbDispatcher = Dispatchers.IO.limitedParallelism(8)

// Confine DB work to 8 threads max
suspend fun queryDb(sql: String): List<Row> = withContext(dbDispatcher) {
    connection.query(sql)
}
```

Prefer `limitedParallelism` over `Executors.newFixedThreadPool(n).asCoroutineDispatcher()` — the
former shares the underlying pool with `Dispatchers.IO`.

## CoroutineContext

A `CoroutineContext` is a map keyed by context-element types. Every
coroutine has one. Elements combine with `+`:

```kotlin
launch(Dispatchers.IO + CoroutineName("sync") + MDCContext()) { ... }
```

Common elements:

- `Job` — the cancellation primitive (usually implicit; the builder
  provides one).
- `CoroutineDispatcher` — where the work runs.
- `CoroutineName("sync")` — a label for logs and thread dumps.
- `CoroutineExceptionHandler` — top-level handler for unhandled
  exceptions from `launch` (ignored for `async`).
- `MDCContext()` (slf4j-mdc artifact) — propagates SLF4J MDC across
  suspensions.

Access the current context inside a suspend function:

```kotlin
suspend fun currentName(): String =
    coroutineContext[CoroutineName]?.name ?: "anon"
```

## Cancellation is cooperative

A coroutine only cancels at a **suspension point** or an explicit check.
Tight CPU loops must cooperate:

```kotlin
while (isActive) { crunch() }           // suspend-aware loop
while (true) { ensureActive(); crunch() }   // throws on cancellation
```

Blocking calls (`Thread.sleep`, legacy I/O, JDBC without timeouts) don't
cooperate. Wrap them in `withContext(Dispatchers.IO)` **and** prefer APIs
with real cancellation (interrupt-aware or non-blocking).

### Never swallow CancellationException

`CancellationException` is the signal that structured concurrency is
tearing your coroutine down. Catching and swallowing it breaks the
guarantee.

```kotlin
try {
    work()
} catch (e: CancellationException) {
    throw e               // rethrow
} catch (e: Exception) {
    logger.error("work failed", e)
}
```

`runCatching { }` catches `CancellationException` silently — use it with
care inside coroutines. Prefer:

```kotlin
val result = runCatching { work() }
    .onFailure { if (it is CancellationException) throw it }
```

Or use `kotlin.coroutines.coroutineContext.ensureActive()` after.

### withTimeout / withTimeoutOrNull

```kotlin
withTimeout(5.seconds) { fetch() }            // throws TimeoutCancellationException
withTimeoutOrNull(5.seconds) { fetch() }      // returns null on timeout
```

`TimeoutCancellationException` is a `CancellationException`, so the
cancellation rules apply.

## SupervisorJob and supervisorScope

A regular `Job` **cancels its siblings** when a child fails — this is the
"all-or-nothing" default. A `SupervisorJob` does not. Use it when
independent tasks shouldn't cascade failures.

```kotlin
supervisorScope {
    launch { mayFailA() }
    launch { mayFailB() }   // keeps running if A fails
}
```

A common pattern for long-lived scopes:

```kotlin
val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
```

Without `SupervisorJob`, one failing `launch { }` kills the whole scope.

### CoroutineExceptionHandler

For top-level unhandled exceptions in `launch`:

```kotlin
val handler = CoroutineExceptionHandler { _, ex ->
    logger.error("uncaught", ex)
}
val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
scope.launch { mayFail() }    // handler runs on failure
```

**`async` does NOT use the handler.** Exceptions from `async` are
delivered through `await()`. If you `async` without `await`, the
exception is silently held until GC — always `await` or use `launch`
instead.

## Flow — cold asynchronous streams

`Flow<T>` is a cold stream: nothing runs until `collect`. Every
terminal operation starts a fresh upstream execution.

```kotlin
fun ticks(): Flow<Int> = flow {
    var i = 0
    while (true) {
        emit(i++)
        delay(1_000)
    }
}

ticks()
    .map { it * it }
    .take(5)
    .collect { println(it) }
```

### Transform operators

- `map`, `filter`, `take`, `drop`, `distinctUntilChanged` — familiar.
- `onEach { }` — side effect without transforming.
- `scan(initial) { acc, v -> ... }` — running fold.
- `combine(other) { a, b -> ... }` — combine latest from each flow.
- `zip(other) { a, b -> ... }` — pairwise; waits for both sides.
- `merge(f1, f2, ...)` — interleaves emissions.

### flatMap variants

| Operator | Semantics |
|---|---|
| `flatMapConcat` | Sequential — wait for each inner flow before next |
| `flatMapMerge(concurrency = N)` | Concurrent fan-out, bounded by N |
| `flatMapLatest` | Cancel previous inner flow on each new outer value (search-as-you-type) |

### Context and threading

- `flowOn(Dispatchers.IO)` — shifts **upstream** emission to a dispatcher.
  Downstream and the collector stay on the caller's context.
- Never call `withContext` inside a `flow { }` builder — it violates the
  context-preservation invariant. Use `flowOn` downstream.

```kotlin
flow {
    emit(expensiveBlockingCall())        // runs on IO
}.flowOn(Dispatchers.IO)
 .map { lightweightTransform(it) }       // runs on caller's dispatcher
 .collect { render(it) }
```

### Backpressure

- `buffer(n)` — decouple producer and consumer with a bounded channel
  buffer. Enables concurrent pipelines.
- `conflate()` — drop intermediate values when the collector is slow.
  Keep only the latest.
- `collectLatest { action }` — cancel the action on each new value;
  restart it. Like `flatMapLatest` on the terminal side.

### Terminal operators

- `collect { }`, `collectLatest { }` — consume.
- `first()`, `firstOrNull()`, `single()` — take one.
- `toList()`, `toSet()` — materialize.
- `reduce { acc, v -> ... }`, `fold(initial) { acc, v -> ... }`.
- `launchIn(scope)` — start collection in `scope`, return the `Job`.
  Pair with `onEach` and `catch` upstream.

```kotlin
events
    .onEach { render(it) }
    .catch { logger.error("stream failed", it) }
    .launchIn(viewModelScope)
```

### Error handling

- `catch { e -> ... }` — catches **upstream** exceptions only; downstream
  errors pass through.
- `retry(n) { e -> predicate }` — retry on matching exceptions.
- `onCompletion { cause -> ... }` — runs on normal completion, error, or
  cancellation (`cause` is null on success).

```kotlin
flow { emit(fetch()) }
    .retry(3) { it is IOException }
    .catch { logger.error("gave up", it) }
    .onCompletion { cause -> metrics.record(success = cause == null) }
    .collect { cache.put(it) }
```

## StateFlow and SharedFlow

Convert cold `Flow` into hot shareable flows.

### StateFlow

Always has a current value. Conflates — only the latest matters. Use for
UI state.

```kotlin
val state: StateFlow<UiState> = repository.observe()
    .map { UiState.from(it) }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState.Loading,
    )
```

- `SharingStarted.Eagerly` — start immediately, keep alive forever.
- `SharingStarted.Lazily` — start on first subscriber, keep alive forever.
- `SharingStarted.WhileSubscribed(timeoutMillis)` — start on first
  subscriber, stop `timeoutMillis` after the last subscriber disappears.
  The 5-second version is the idiom for Android ViewModels surviving
  configuration changes.

### SharedFlow

Hot flow without an initial value. You control replay, buffer, and
overflow.

```kotlin
val events = MutableSharedFlow<Event>(
    replay = 0,
    extraBufferCapacity = 64,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)

suspend fun emit(e: Event) = events.emit(e)
```

- `replay = N` — last N values replayed to new subscribers.
- `extraBufferCapacity` — buffer on top of replay.
- `onBufferOverflow` — `SUSPEND` (backpressure), `DROP_OLDEST`,
  `DROP_LATEST`.

Use `SharedFlow` for events (navigation, snackbars); use `StateFlow` for
state (current user, current screen). Events are conceptually transient;
state is current.

### Channels

`Channel<T>` is the coroutine analogue of a blocking queue. Use for
hand-written producer/consumer setups:

```kotlin
val channel = Channel<Int>(capacity = Channel.BUFFERED)
launch { for (i in 1..100) channel.send(i); channel.close() }
launch { for (item in channel) process(item) }
```

Prefer `Flow` for anything that resembles a pipeline; reach for `Channel`
when you genuinely need hot, multi-producer, single-consumer semantics
that `SharedFlow` doesn't capture.

## runBlocking — only at boundaries

`runBlocking` bridges non-suspend → suspend by blocking a thread. Valid
uses:

- `fun main()` in a CLI / script.
- A top-level entry point where no other async machinery exists.
- Tests (but prefer `runTest` — see below).

```kotlin
fun main() = runBlocking {
    val config = loadConfig()
    runServer(config)
}
```

**Never** call `runBlocking` from:

- A suspend function (use `coroutineScope { }` instead).
- An Android UI callback (freezes the UI).
- A server request handler (wastes a thread, can deadlock single-threaded
  dispatchers).
- Inside another coroutine — you already have a scope.

## Shared mutable state

Prefer immutability and message passing. When you must share:

```kotlin
val mutex = Mutex()
var counter = 0
suspend fun increment() = mutex.withLock { counter++ }
```

Or confine state to an actor via `Channel`. Atomics (`AtomicInteger`,
`@Volatile`) only protect individual reads/writes; multi-step invariants
need a `Mutex` or a single-threaded dispatcher:

```kotlin
val serialDispatcher = Dispatchers.Default.limitedParallelism(1)
suspend fun updateState(f: (State) -> State) = withContext(serialDispatcher) {
    state = f(state)
}
```

## Testing

Use `kotlinx-coroutines-test` and `runTest` — never `runBlocking` in
tests.

```kotlin
// build.gradle.kts
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
```

```kotlin
@Test
fun `retries on IOException`() = runTest {
    val flow = flow {
        emit(fetch())              // delay(10_000) inside
    }.retry(3) { it is IOException }

    val result = flow.toList()
    assertEquals(1, result.size)
    // runTest uses virtual time: 30 seconds of `delay` pass instantly
}
```

Key `runTest` features:

- **Virtual time**: `delay(10.minutes)` returns immediately.
- `TestScope` is the scope; `testScheduler` controls virtual time.
- `advanceTimeBy(duration)`, `advanceUntilIdle()`, `runCurrent()` let you
  step through time.
- `StandardTestDispatcher()` queues work for explicit advancement;
  `UnconfinedTestDispatcher()` runs eagerly.
- Swap `Dispatchers.Main` for tests:

```kotlin
@BeforeEach
fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
}
@AfterEach
fun tearDown() {
    Dispatchers.resetMain()
}
```

## Interop

- **CompletableFuture**: `future { ... }` (from `kotlinx-coroutines-jdk8`)
  wraps a suspend block as a `CompletableFuture`. `CompletableFuture.await()`
  suspends until completion.
- **RxJava**: `kotlinx-coroutines-rx3` provides `Flow.asObservable()`,
  `Observable.asFlow()`, `Single.await()`, etc.
- **Reactive Streams**: `Flow.asPublisher()`, `Publisher.asFlow()`.
- **JavaScript Promises**: `Promise.await()` (JS target).
- **Apple callbacks**: wrap via `suspendCancellableCoroutine { cont -> ... }`.

## Common gotchas

- **`GlobalScope`**: don't use. Not tied to anything, not cancellable,
  leaks. Always launch from a real scope.
- **Blocking in `Dispatchers.Default`**: starves CPU coroutines. Move
  blocking I/O to `Dispatchers.IO`.
- **`withContext` inside `flow { }`**: violates context preservation —
  use `flowOn` downstream.
- **`async { }` without `await()`**: swallows exceptions silently until
  GC. Always await, or use `launch` if you don't need the result.
- **`Dispatchers.Main` without the artifact**: throws at runtime.
- **`Flow` without a terminal operator**: does nothing. `collect` it or
  `launchIn(scope)`.
- **`runCatching` in coroutines**: catches `CancellationException`. Use
  with care; rethrow cancellation explicitly if wrapping suspend calls.
- **Swallowing `CancellationException`**: breaks structured concurrency.
  Always rethrow.

## Anti-patterns

```kotlin
// WRONG: GlobalScope
GlobalScope.launch { syncAll() }

// Correct: pass or own a scope
class SyncService(private val scope: CoroutineScope) {
    fun start() = scope.launch { syncAll() }
}
```

```kotlin
// WRONG: runBlocking inside a suspend function
suspend fun fetch() = runBlocking { apiClient.get() }

// Correct: just call the suspend
suspend fun fetch() = apiClient.get()
```

```kotlin
// WRONG: blocking I/O on Default
withContext(Dispatchers.Default) { Thread.sleep(1_000) }

// Correct: IO dispatcher
withContext(Dispatchers.IO) { Thread.sleep(1_000) }
```

```kotlin
// WRONG: async without await
val d = async { mayFail() }   // exception lost!

// Correct
val d = async { mayFail() }
val r = d.await()             // or use launch
```

```kotlin
// WRONG: swallow cancellation
try { work() } catch (e: Exception) { log(e) }

// Correct
try { work() } catch (e: CancellationException) { throw e }
catch (e: Exception) { log(e) }
```

## References

- Repository — https://github.com/Kotlin/kotlinx.coroutines
- Coroutines guide — https://kotlinlang.org/docs/coroutines-guide.html
- Structured concurrency — https://kotlinlang.org/docs/coroutines-basics.html
- Flow — https://kotlinlang.org/docs/flow.html
- Exception handling — https://kotlinlang.org/docs/exception-handling.html
- StateFlow / SharedFlow — https://kotlinlang.org/docs/flow.html#stateflow-and-sharedflow
- Testing — https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-test/README.md
- Coroutine context — https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html
