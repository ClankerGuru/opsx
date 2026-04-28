---
name: coroutines-shared-state
description: >-
  Use when multiple coroutines touch the same mutable state — picking
  between `Mutex`, `AtomicReference`/atomics, single-thread
  confinement via `limitedParallelism(1)`, actor-style ownership, and
  `StateFlow` updates with `update { }`. Covers deadlock pitfalls,
  withLock semantics, and why `synchronized` is the wrong tool.
---

## When to use this skill

- Two or more coroutines write to the same data structure.
- A counter, cache, or queue is shared across concurrent producers.
- Deciding whether a `Mutex`, an atomic, or confinement is the right
  primitive.
- Auditing suspicious "works on my machine" bugs that involve shared
  state.

## When NOT to use this skill

- The state is confined to one coroutine already — no primitive
  needed.
- The problem is best expressed as a stream — use `Channel` or
  `StateFlow`/`SharedFlow` instead of a shared mutable variable.
- Non-suspending code on the JVM — use regular concurrency tools
  (though many rules here still apply).

## The problem

Coroutines on a shared dispatcher can execute on any worker thread.
Two coroutines that touch the same `var x` or `MutableList` without
coordination race the same way threads do.

```kotlin
var counter = 0
coroutineScope {
    repeat(1_000) { launch(Dispatchers.Default) { counter++ } }
}
// counter is almost never 1000
```

The suspending nature doesn't save you; `Dispatchers.Default` is
multi-threaded.

## Primitive 1: `Mutex` — the default

`kotlinx.coroutines.sync.Mutex` is the coroutine-aware lock. Always
use `withLock { }`:

```kotlin
class Counter {
    private val mutex = Mutex()
    private var value = 0
    suspend fun incr() = mutex.withLock { value++ }
    suspend fun read() = mutex.withLock { value }
}
```

Rules:

- `withLock` suspends (cheap) instead of blocking the thread.
- `Mutex` is **not re-entrant**. Recursive `withLock` deadlocks.
- Lock order matters — document it when multiple mutexes can be
  taken together.

Use `Mutex` when:

- The critical section is short.
- The state is one small struct.
- You need fairness (`Mutex(locked = false)` grants FIFO).

## Primitive 2: Atomics for primitives and single references

`java.util.concurrent.atomic.*` is first-class:

```kotlin
class Counter {
    private val value = AtomicInteger(0)
    fun incr() = value.incrementAndGet()
    fun read() = value.get()
}
```

- No suspension; lock-free on modern CPUs.
- Fine for counters, flags, or CAS-based update of an immutable
  snapshot (`AtomicReference<ImmutableList<T>>`).
- Bad for complex invariants across multiple fields — any multi-step
  update needs `Mutex` or single-thread confinement.

Kotlin multiplatform: `atomicfu` provides `atomic { }` expressions
that compile to the same primitives on JVM.

## Primitive 3: single-thread confinement

Restrict state mutation to a single-thread dispatcher. All access
goes through `withContext(dbDispatcher) { ... }`. The data is only
ever touched on that one thread — no lock needed.

```kotlin
class Cache {
    private val dispatcher = Dispatchers.IO.limitedParallelism(1, "cache")
    private val entries = mutableMapOf<Key, Value>()

    suspend fun put(k: Key, v: Value) = withContext(dispatcher) {
        entries[k] = v
    }
    suspend fun get(k: Key): Value? = withContext(dispatcher) {
        entries[k]
    }
}
```

- No `Mutex` bookkeeping; the dispatcher serializes.
- Throughput capped at one core — not for high contention.
- Composes poorly across classes; only the owning class may touch the
  state.

Prefer `limitedParallelism(1)` over `newSingleThreadContext` — it
shares threads with the IO pool, no dedicated thread leak.

## Primitive 4: `StateFlow.update { }` for observable state

When state is already exposed as a `StateFlow`, use `update`:

```kotlin
private val _state = MutableStateFlow(UiState.Empty)
val state: StateFlow<UiState> = _state.asStateFlow()

fun addItem(item: Item) {
    _state.update { current -> current.copy(items = current.items + item) }
}
```

`update { }` retries the lambda until the CAS succeeds — safe for
concurrent callers without external locks. See
`coroutines-stateflow-sharedflow` for the full StateFlow model.

## Primitive 5: actor pattern (message passing)

When a piece of state should have exactly one owner and be mutated
only via requests, express it as a coroutine consuming a `Channel`:

```kotlin
sealed interface CounterMsg {
    data object Incr : CounterMsg
    data class Read(val reply: CompletableDeferred<Int>) : CounterMsg
}

fun CoroutineScope.counterActor(): SendChannel<CounterMsg> {
    val inbox = Channel<CounterMsg>(capacity = Channel.UNLIMITED)
    launch {
        var value = 0
        for (msg in inbox) {
            when (msg) {
                CounterMsg.Incr -> value++
                is CounterMsg.Read -> msg.reply.complete(value)
            }
        }
    }
    return inbox
}
```

- No locks; the actor coroutine owns the state.
- Natural fit for complex state machines.
- The deprecated `actor { }` builder did exactly this; roll it by hand
  today.

## Choosing

| Need                                  | Pick                                |
|---------------------------------------|-------------------------------------|
| Counter, flag, one reference          | Atomic                              |
| Small critical section, few fields    | `Mutex.withLock`                    |
| Complex state touched by many callers | Single-thread confinement           |
| Observable UI/view state              | `MutableStateFlow` + `update { }`   |
| State machine with a lot of logic     | Actor (coroutine + `Channel`)       |

Don't reach for `synchronized` / `ReentrantLock`:

- They block the thread; on `Dispatchers.Default` this starves the
  pool.
- `Mutex` is the coroutine-equivalent and cheaper under contention.

## Deadlock and ordering

```kotlin
suspend fun transfer(a: Account, b: Account, amount: Int) {
    a.mutex.withLock {
        b.mutex.withLock { /* ... */ }   // reversed elsewhere => deadlock
    }
}
```

Fixes:

- Take locks in a **total order** (e.g., by `id`).
- Serialize through a single owner (actor) so the question doesn't
  arise.
- Use an immutable snapshot + atomic swap for the combined state.

## `withLock` + `runCatching`

Keep failures inside the lock so state stays consistent:

```kotlin
suspend fun consume(item: Item): Result<Unit> = mutex.withLock {
    runCatching { apply(item) }
        .onFailure { revertPartial() }
}
```

## Anti-patterns

```kotlin
// WRONG — Thread.sleep / blocking inside withLock
mutex.withLock { Thread.sleep(1000) }   // blocks the thread while holding the lock

// WRONG — re-entering the same Mutex
mutex.withLock { inner() }               // where inner() also withLock { } on the same mutex → deadlock

// WRONG — synchronized on a coroutine dispatcher
synchronized(this) { work() }            // blocks the thread; doesn't cooperate with cancellation

// WRONG — atomic for multi-field invariant
val name = AtomicReference("")
val age  = AtomicInteger(0)
// Updating both atomically needs a Mutex, not two atomics.

// WRONG — assuming one dispatcher == single-threaded
launch(Dispatchers.Default) { counter++ }  // Default is multi-threaded

// WRONG — unbounded channel for an actor in a hot path
Channel<Msg>(Channel.UNLIMITED)   // memory grows without bound; pick RENDEZVOUS or a cap
```

## Pitfalls

| Symptom                                       | Cause / fix                                                       |
|-----------------------------------------------|-------------------------------------------------------------------|
| Counter skips values                          | Missing primitive — add `Mutex`, `Atomic`, or confinement.         |
| Occasional deadlock under load                | Inconsistent lock order; reentrant use; or `withLock` around blocking I/O. |
| Throughput cliff at high concurrency          | Single-thread confinement can't keep up; switch to sharded confinement or `Mutex` with fine-grained state. |
| State observed as a torn read                 | Multiple atomics where a `Mutex`/confinement is needed.            |
| Actor blocks producers                        | Unbounded inbox hides backpressure; cap capacity or use RENDEZVOUS. |
| `Mutex` used across cancellation              | `withLock` releases on cancellation automatically — but hold regions must not corrupt state if interrupted. Wrap rollback in `NonCancellable`. |

## Reference points

- https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html
- https://kt.academy/book/coroutines — Ch 15 (Synchronizing access to mutable state)
- `kotlinx.coroutines.sync.Mutex`, `CoroutineDispatcher.limitedParallelism`
