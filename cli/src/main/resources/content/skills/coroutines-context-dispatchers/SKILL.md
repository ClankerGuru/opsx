---
name: coroutines-context-dispatchers
description: >-
  Use when composing or overriding a `CoroutineContext` — picking a
  `Dispatcher` (Default, IO, Main, Unconfined, custom), switching
  context with `withContext`, reading context elements like
  `CoroutineName`, and understanding how `+` composition, inheritance,
  and override work. Covers dispatcher choice, confinement, and
  `limitedParallelism`.
---

## When to use this skill

- Deciding which dispatcher a block of work should run on.
- Injecting a dispatcher for testability.
- Switching dispatchers at module boundaries with `withContext`.
- Building a custom dispatcher with bounded parallelism.

## When NOT to use this skill

- Construction of a whole scope — see `coroutines-scopes`.
- Propagation of `CancellationException` / exception handling — see
  `coroutines-cancellation-exceptions`.

## CoroutineContext: an indexed map of elements

`CoroutineContext` is a persistent map keyed by `CoroutineContext.Key`.
Common elements:

| Element                      | Key                      | What it does                                          |
|------------------------------|--------------------------|-------------------------------------------------------|
| `Job`                        | `Job.Key`                | Lifecycle, parent-child linkage, cancellation.        |
| `ContinuationInterceptor`    | —                        | Dispatcher base type.                                 |
| `CoroutineDispatcher`        | `CoroutineDispatcher.Key`| Where coroutine code runs.                            |
| `CoroutineName`              | `CoroutineName.Key`      | Debug label.                                          |
| `CoroutineExceptionHandler`  | `CoroutineExceptionHandler.Key` | Last-resort uncaught-exception sink.          |

Compose with `+`:

```kotlin
val ctx = Dispatchers.IO + CoroutineName("upload") + SupervisorJob()
```

Read an element:

```kotlin
val name = currentCoroutineContext()[CoroutineName]?.name
val job  = currentCoroutineContext()[Job]
```

`coroutineContext` (inside a suspend function) is
`currentCoroutineContext()`.

### Override semantics

Context elements are overridden by key. `a + b` returns a context
where `b`'s element wins for any key present in both:

```kotlin
val parent = Dispatchers.IO + CoroutineName("parent")
val child  = parent + CoroutineName("child")    // IO + child
```

This is exactly how `launch(ctx) { ... }` merges with the scope's
context: `scope.coroutineContext + ctx + Job()`. The new `Job()` is
always added so the new coroutine gets its own child job.

## Dispatchers

### `Dispatchers.Default`

A shared pool sized to CPU count. Use for CPU-bound work:
deserialization, parsing, pure computation.

### `Dispatchers.IO`

A shared elastic pool sized for blocking I/O (default cap ≈ 64).
Use for filesystem, JDBC, any blocking call you can't avoid.

`Dispatchers.IO` and `Default` share threads under the hood; the cap
is enforced per-dispatcher via `limitedParallelism` internally.

### `Dispatchers.Main` / `Main.immediate`

Platform UI thread (Android, JavaFX, Swing). `Main.immediate`
short-circuits when already on the UI thread — use it in UI handlers
to avoid an unnecessary dispatch.

### `Dispatchers.Unconfined`

Runs on the calling thread until the first suspension, then on the
resumer's thread. **Avoid in production.** Useful only for tests and
tiny adapters where dispatch overhead matters.

### Custom dispatchers

Wrap an `Executor`:

```kotlin
val scheduled = Executors.newScheduledThreadPool(2).asCoroutineDispatcher()
```

Remember to close: `scheduled.close()` shuts the executor.

Bounded parallelism:

```kotlin
val dbDispatcher = Dispatchers.IO.limitedParallelism(4, name = "db")
```

Reserves 4 slots out of the IO pool for DB work; other IO callers
can't saturate them. Prefer this over a separate pool when you just
need to cap concurrency.

## `withContext` — switch, don't leak

```kotlin
class UserRepo(private val io: CoroutineDispatcher = Dispatchers.IO) {
    suspend fun get(id: UserId): User = withContext(io) {
        blockingJdbcLookup(id)
    }
}
```

Rules:

- `withContext` suspends until its block returns; the outer coroutine
  resumes on its original dispatcher.
- Uses the **same Job** as the outer coroutine — it's a context swap,
  not a new coroutine. Cancellation propagates in both directions.
- Ideal place to hide blocking APIs: switch at the edge, keep the
  rest of the code dispatcher-agnostic.

Don't nest `withContext(Dispatchers.IO) { withContext(Dispatchers.IO) { ... } }` —
it's a no-op when the inner matches the outer but still costs a dispatch.

## Injecting dispatchers

```kotlin
class Svc(
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val cpu: CoroutineDispatcher = Dispatchers.Default,
) { /* ... */ }

// In tests
val scheduler = TestCoroutineScheduler()
Svc(io = StandardTestDispatcher(scheduler), cpu = StandardTestDispatcher(scheduler))
```

**Never hardcode `Dispatchers.IO` inside a shared class.** The dispatcher
is a dependency. Inject it; tests substitute a `TestDispatcher`.

## `CoroutineName`

Labels the coroutine in logs and debugger output. Enable with
`-Dkotlinx.coroutines.debug` JVM flag.

```kotlin
launch(CoroutineName("payment-processor")) { /* ... */ }
```

Name every long-lived coroutine. Debugging without names is miserable.

## Reading and using context inside code

```kotlin
suspend fun logProgress(msg: String) {
    val name = currentCoroutineContext()[CoroutineName]?.name ?: "anon"
    logger.info { "[$name] $msg" }
}
```

`currentCoroutineContext()` is preferred in suspend functions over
implicit `coroutineContext` — it's explicit and searchable.

## Composition patterns

### Scope context as defaults, builder context as overrides

```kotlin
val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("svc"))
scope.launch(Dispatchers.IO) { /* IO override */ }
scope.launch { /* Default from scope */ }
```

### Pass parent context through

```kotlin
class Component(parentContext: CoroutineContext = EmptyCoroutineContext) {
    private val scope = CoroutineScope(parentContext + SupervisorJob(parentContext[Job]))
}
```

Lets the caller plug in name, dispatcher, handler without the
component knowing.

## Anti-patterns

```kotlin
// WRONG — hardcoded dispatcher
class Svc { fun go(scope: CoroutineScope) = scope.launch(Dispatchers.IO) { ... } }
// RIGHT — inject
class Svc(private val io: CoroutineDispatcher = Dispatchers.IO) { ... }

// WRONG — blocking on Default
launch(Dispatchers.Default) { Thread.sleep(1000) }
// RIGHT
launch { delay(1000) }
// or if unavoidable:
launch(Dispatchers.IO) { runInterruptible { legacy() } }

// WRONG — withContext to same dispatcher
withContext(Dispatchers.IO) { withContext(Dispatchers.IO) { io() } }  // pointless hop

// WRONG — newSingleThreadContext without close
val ctx = newSingleThreadContext("dbx")  // leaks the thread forever
// RIGHT
ctx.use { launch(it) { ... } }
// or prefer
Dispatchers.IO.limitedParallelism(1, "db")
```

## Pitfalls

| Symptom                                   | Cause / fix                                                              |
|-------------------------------------------|--------------------------------------------------------------------------|
| UI freezes despite `suspend`              | Body blocks on wrong dispatcher. Wrap blocking call in `withContext(IO)`. |
| Tests are flaky / sleep-dependent         | Hardcoded real dispatcher. Inject `TestDispatcher`.                      |
| "IO starvation" under load                | One subsystem saturates `Dispatchers.IO`. Give it `limitedParallelism(n)`. |
| Exception handler not firing              | `CoroutineExceptionHandler` placed on child not scope; must be on root.  |
| Context change "lost"                     | `withContext` returns — caller resumes on original dispatcher; that's correct. |
| Thread leak                               | `newSingleThreadContext` never closed. Prefer `limitedParallelism`.      |

## Reference points

- https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html
- https://kt.academy/book/coroutines — Ch 9 (Coroutine context), Ch 10 (Dispatchers)
- `kotlinx.coroutines.CoroutineDispatcher.limitedParallelism` — API doc.
