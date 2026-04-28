---
name: coroutines-flow
description: >-
  Use when modeling a cold asynchronous stream — `flow { }` builder,
  intermediate operators (map/filter/take/transform), terminal
  operators (collect/first/toList/reduce), lifecycle hooks
  (onStart/onEach/onCompletion/catch/retry),
  context/concurrency operators (flowOn/buffer/conflate/flatMapMerge/flatMapConcat/flatMapLatest),
  `Flow<T>` vs `Sequence<T>`, and exception transparency.
---

## When to use this skill

- You have a sequence of values that arrive over time, emitted by a
  producer that may suspend.
- You want a *cold*, reusable, composable pipeline (every collector
  re-runs the producer).
- You need operators like `debounce`, `flatMapMerge`, `retry`,
  `sample`.

## When NOT to use this skill

- A single value — use a `suspend fun` returning `T`.
- Shared hot state — use `StateFlow` / `SharedFlow` (see that skill).
- In-memory synchronous iteration — use `Sequence`.
- Direct coroutine-to-coroutine exchange — use `Channel`.

## Core model

A `Flow<T>` is a **cold** stream:

- Nothing happens until you `collect`.
- Each `collect` runs the producer block from scratch.
- Cancellation of the collecting coroutine cancels the flow.
- Exceptions propagate through operators and terminate the flow.

```kotlin
val numbers: Flow<Int> = flow {
    for (i in 1..3) {
        delay(100)
        emit(i)
    }
}

// Nothing emitted yet. Start by collecting:
numbers.collect { println(it) }        // runs producer
numbers.collect { println("again: $it") } // runs producer AGAIN
```

### Flow vs Sequence

| `Sequence<T>`                                  | `Flow<T>`                                           |
|------------------------------------------------|-----------------------------------------------------|
| Synchronous, blocking.                         | Suspending, asynchronous.                           |
| No coroutine awareness.                        | Cooperates with cancellation, context, dispatchers. |
| Single-threaded.                               | Producer may switch context (`flowOn`).             |
| `.map`, `.filter`, `.toList` only.             | Plus `flatMapMerge`, `debounce`, `retry`, `flowOn`, ... |

Use `Sequence` for in-memory lazy iteration. Use `Flow` when
suspension, time, or IO is involved.

## Building flows

### `flow { }` — the builder

```kotlin
fun tickerFlow(period: Duration): Flow<Long> = flow {
    var t = 0L
    while (true) {
        emit(t++)
        delay(period)
    }
}
```

Rules inside a `flow { }` block:

- Call `emit(value)` to send a value.
- You must emit from the same coroutine that called `collect` — no
  launching children that emit. (See "Exception transparency" below.)
- Suspension is fine (`delay`, other suspend calls). The collector's
  dispatcher drives the producer by default.

### Convenience builders

```kotlin
flowOf(1, 2, 3)
listOf(1, 2, 3).asFlow()
(1..10).asFlow()
emptyFlow<Int>()
```

### `callbackFlow` / `channelFlow` — when you need to emit from other coroutines

When the producer interacts with a callback API or multiple producer
coroutines, `flow { }` won't suffice (emit is not thread-safe across
contexts). Use `callbackFlow`:

```kotlin
fun locations(): Flow<Location> = callbackFlow {
    val listener = object : LocationListener {
        override fun onLocation(loc: Location) { trySend(loc) }
    }
    locationApi.register(listener)
    awaitClose { locationApi.unregister(listener) }
}
```

- `trySend` is safe from any callback thread.
- `awaitClose { }` is mandatory — it runs cleanup when the collector
  cancels or the flow closes.
- `channelFlow` is the more general form without callback affordance.

## Operators

### Intermediate (return a new `Flow`)

```kotlin
numbers
    .map { it * 2 }
    .filter { it > 2 }
    .take(3)
    .onEach { println("got $it") }
    .drop(1)
    .distinctUntilChanged()
    .transform { v -> emit(v); emit(-v) }
```

Intermediate operators are cold and cheap — they just compose.

### Terminal (suspend, trigger execution)

```kotlin
flow.collect { println(it) }
flow.first()                // first value; cancels after
flow.firstOrNull()          // or null
flow.single()               // exactly one, else error
flow.toList()               // collect to List
flow.fold(0) { a, v -> a + v }
flow.reduce { a, v -> a + v }
flow.count()
flow.launchIn(scope)        // collect in scope, returns Job
```

### Lifecycle hooks

| Operator                         | Fires                                                       |
|----------------------------------|-------------------------------------------------------------|
| `.onStart { emit(...) }`         | Before first upstream emission.                             |
| `.onEach { ... }`                | For every value (side-effect).                              |
| `.onCompletion { cause -> ... }` | When flow finishes (normal or exception) — `cause` is null on success. |
| `.onEmpty { emit(...) }`         | When upstream finished with zero emissions.                 |

Always pair UI "loading/loaded/error" shaping with `onStart` +
`onCompletion` rather than wrapping collection in imperative code.

### Errors — `catch` and `retry`

```kotlin
flow
    .map { riskyParse(it) }
    .retry(retries = 3) { e -> e is IOException }
    .catch { e -> emit(Fallback(e)) }
    .collect { ... }
```

- `.catch { }` catches upstream exceptions only. Never catches
  exceptions thrown by the downstream collector — "exception
  transparency".
- `.retry` retries from the start of the upstream (any operator
  before `retry`). The flow is cold; retry is natural.
- `.retryWhen { e, attempt -> ... }` lets you add delays/backoff.

Catch on the collector side uses `runCatching`, not `try`:

```kotlin
runCatching { flow.collect { ... } }
    .onFailure {
        currentCoroutineContext().ensureActive()
        logger.warn(it) { "flow failed" }
    }
```

### Context and threading — `flowOn`

```kotlin
flow { heavyWork() }
    .map { parse(it) }
    .flowOn(Dispatchers.Default)   // shifts UPSTREAM to Default
    .collect { updateUi(it) }      // runs on collector's dispatcher
```

`flowOn(ctx)` applies **upstream** of where it appears. Operators
below `flowOn` still run on the collector's context. Multiple
`flowOn` sections partition the pipeline — each segment runs on the
specified dispatcher with a dedicated channel between.

Rules:

- Never call `withContext` inside `flow { emit(...) }`. It violates
  emit-from-same-coroutine — use `flowOn` instead.
- Place `flowOn` right after the expensive block; values cross a
  channel to the collector.

### Concurrency operators

| Operator                           | Concurrency model                                                  |
|------------------------------------|--------------------------------------------------------------------|
| `buffer(capacity)`                 | Decouple producer and consumer; producer runs ahead.               |
| `conflate()`                       | Drop intermediate values if consumer is slow (latest-wins).        |
| `collectLatest { }`                | Cancel in-flight collector body when a new value arrives.          |
| `flatMapConcat { ... }`            | Serial: wait for each inner flow.                                  |
| `flatMapMerge(concurrency = N)`    | Parallel: up to N inner flows in flight; order NOT preserved.      |
| `flatMapLatest { ... }`            | Cancel the previous inner flow when a new value arrives.           |
| `combine`, `zip`                   | Two-arg combination across flows.                                  |

Parallel map pattern:

```kotlin
urls.asFlow()
    .flatMapMerge(concurrency = 8) { url ->
        flow { emit(fetch(url)) }
    }
    .collect { save(it) }
```

Never do `map { async { ... } }` on a flow — leaks async jobs past
the collector. `flatMapMerge` is the right tool.

## Running flows in a scope

### `launchIn(scope)`

```kotlin
flow
    .onEach { updateUi(it) }
    .catch { logger.warn(it) { "flow error" } }
    .launchIn(scope)
```

Equivalent to `scope.launch { flow.collect { } }` — more idiomatic
when every line in the pipeline is an operator.

### `stateIn` / `shareIn` — cold to hot

Converts a cold Flow to a hot `StateFlow` / `SharedFlow`. Covered in
`coroutines-stateflow-sharedflow`.

## Cancellation in flows

Flows are cancellable at every suspension point:

- `emit` checks cancellation.
- Built-in operators check cancellation.
- A tight CPU loop inside `flow { }` won't check — call `yield()` or
  `currentCoroutineContext().ensureActive()`.

`.cancellable()` inserts explicit cancellation checks in operators
that don't suspend on their own (e.g. `map` over a tight source).
Rarely needed but available.

## Distinguishing Flow from Channel one more time

| You need                                       | Flow | Channel |
|------------------------------------------------|------|---------|
| Fresh producer run per subscriber              | ✔    |         |
| Operator library (debounce, flatMap, retry)    | ✔    |         |
| Hand-off with backpressure between coroutines  |      | ✔       |
| One value delivered to exactly one receiver    |      | ✔       |
| `select { channel.onReceive }`                 |      | ✔       |

## Testing flows

Use `runTest`:

```kotlin
@Test fun emits_in_order() = runTest {
    val values = flowOf(1, 2, 3).toList()
    values shouldBe listOf(1, 2, 3)
}
```

For time-based flows, the test scheduler virtualizes `delay`:

```kotlin
@Test fun ticks() = runTest {
    val emitted = mutableListOf<Long>()
    val job = tickerFlow(100.milliseconds).onEach(emitted::add).launchIn(this)
    advanceTimeBy(350)
    runCurrent()
    job.cancel()
    emitted shouldBe listOf(0L, 1L, 2L, 3L)
}
```

For declarative step-by-step assertions, reach for `turbine`.

## Anti-patterns

```kotlin
// WRONG — withContext inside flow { }
flow { withContext(Dispatchers.IO) { emit(load()) } }   // violates emit-from-same-coroutine
// RIGHT
flow { emit(load()) }.flowOn(Dispatchers.IO)

// WRONG — try/catch around collect
try { flow.collect { ... } } catch (e: Exception) { ... }
// RIGHT
runCatching { flow.collect { ... } }.onFailure {
    currentCoroutineContext().ensureActive(); logger.warn(it) { "failed" }
}

// WRONG — operator chain swallows cancellation
flow.catch { log(it) }   // catches CancellationException silently
// RIGHT
flow.catch { e ->
    currentCoroutineContext().ensureActive()
    log(e)
}

// WRONG — async inside map
flow.map { async { fetch(it) }.await() }
// RIGHT
flow.flatMapMerge(concurrency = N) { flow { emit(fetch(it)) } }

// WRONG — emitting from a launched child inside flow { }
flow { launch { emit(1) } }   // runtime error
// RIGHT — callbackFlow or channelFlow

// WRONG — treating Flow like a value container
val f: Flow<User> = flowOf(user)  // prefer returning User from a suspend fn
```

## Pitfalls

| Symptom                                    | Cause / fix                                                         |
|--------------------------------------------|---------------------------------------------------------------------|
| Values appear nowhere                      | Cold flow never collected. Add `.launchIn(scope)` or `collect`.     |
| Producer re-runs unexpectedly              | That's cold semantics. Move to `shareIn`/`stateIn`.                 |
| UI stalls while flow runs                  | Heavy work on Main. Use `flowOn(Default)` / `flowOn(IO)`.           |
| `flowOn` has no effect                     | Placed downstream of heavy block; place it immediately after.       |
| Duplicate work on retry                    | `retry` re-runs the whole upstream — that's intended; narrow the retriable segment. |
| Flow emits out of order                    | `flatMapMerge` does not preserve order; use `flatMapConcat` or sort. |
| Test hangs                                 | Collector runs forever; add `.take(n)` or cancel via `launchIn(this); job.cancel()`. |

## Reference points

- https://kotlinlang.org/docs/flow.html
- https://kt.academy/book/coroutines — Ch 19–24 (Hot/cold, Flow
  introduction, Understanding Flow, Flow building, Flow lifecycle
  functions, Flow processing)
- `kotlinx.coroutines.flow` — operator reference.
