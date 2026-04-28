---
name: coroutines-scopes
description: >-
  Use when deciding where coroutines live — constructing custom
  `CoroutineScope`s tied to a class lifecycle, composing a context of
  `SupervisorJob` + dispatcher + exception handler + `CoroutineName`,
  using builders `launch`/`async`/`coroutineScope`/`supervisorScope`,
  and picking between plain Job and SupervisorJob. Covers the scope
  factory, scope ownership, and shutdown.
---

## When to use this skill

- A service, repository, or long-lived object needs to run coroutines
  beyond a single function call.
- You're tempted to reach for `GlobalScope` — don't. Build a scope.
- Fanning out work with `async` + `awaitAll` or `launch` + siblings.
- You need to understand why your children aren't being cancelled.

## When NOT to use this skill

- A suspend function only needs to do fan-out within itself — use
  `coroutineScope { ... }` inside (structured, no field needed).
- You're authoring a single suspend function with no child
  coroutines — you don't need a scope at all.

## The golden rule

**Every coroutine runs inside exactly one scope. Every scope has an
owner. The owner cancels the scope when its own lifecycle ends.**

Violations:

- `GlobalScope.launch { ... }` — no owner, runs until the process
  dies. Treat as forbidden.
- `CoroutineScope(Dispatchers.IO).launch { ... }` — anonymous scope
  with no handle to cancel it. Same defect as `GlobalScope`.

## Constructing a custom scope

The canonical factory:

```kotlin
CoroutineScope(context: CoroutineContext): CoroutineScope
```

Minimum context for a real scope:

```kotlin
private val scope = CoroutineScope(
    SupervisorJob() +
    Dispatchers.Default +
    CoroutineExceptionHandler { _, t -> logger.error(t) { "unhandled" } } +
    CoroutineName("uploader")
)
```

The `+` composes `CoroutineContext` elements. See
`coroutines-context-dispatchers` for the mechanics.

### Pieces explained

| Element                     | Purpose                                                          |
|-----------------------------|------------------------------------------------------------------|
| `SupervisorJob()`           | Root job. Child failure doesn't fail siblings. Cancels all descendants when cancelled. |
| `Dispatchers.Default` / `IO`| Where child coroutines run unless overridden.                    |
| `CoroutineExceptionHandler` | Last-resort net for uncaught exceptions in `launch` children.    |
| `CoroutineName("...")`      | Debug name surfaced in logs and thread dumps (enable `-Dkotlinx.coroutines.debug`). |

If you omit `SupervisorJob`, the default is a plain `Job()` — one
child's failure cancels *all* siblings. That's right for a pipeline,
wrong for a service.

### Scope class pattern

```kotlin
class UploadService(
    parentContext: CoroutineContext = EmptyCoroutineContext,
) : AutoCloseable {
    private val job = SupervisorJob(parent = parentContext[Job])
    private val scope = CoroutineScope(
        parentContext + job + Dispatchers.IO + CoroutineName("upload")
    )

    fun submit(file: File): Job = scope.launch {
        runCatching { uploader.upload(file) }
            .onFailure { logger.warn(it) { "upload failed: $file" } }
    }

    override fun close() {
        job.cancel()            // cancel descendants; don't wait
    }

    suspend fun closeAndJoin() {
        job.cancelAndJoin()     // wait for cleanup
    }
}
```

Why this shape:

- Accepts `parentContext` — callers can compose this service into a
  larger scope hierarchy. Tests can inject a `TestDispatcher`.
- `SupervisorJob(parent = parentContext[Job])` wires the new job as a
  child of the caller's job so cancelling the caller cancels the
  service, preserving structured concurrency.
- `close()` is synchronous (safe for `AutoCloseable`); `closeAndJoin`
  is the suspend variant when you need to wait for in-flight work.

## Platform scopes

Use the platform-provided scope when one exists:

- **Android ViewModel** — `viewModelScope` (tied to `onCleared()`).
- **Android lifecycle** — `lifecycleScope` (tied to Lifecycle).
- **Compose** — `rememberCoroutineScope()` (tied to the composition).
- **Ktor server** — `call.application.environment.application` /
  request scope.
- **Tests** — the scope `runTest { }` gives you.

Don't wrap these in another scope; use them directly.

## Coroutine builders

### `launch` — fire and forget, returns a `Job`

```kotlin
val j: Job = scope.launch {
    runCatching { work() }
        .onFailure { logger.warn(it) { "work failed" } }
}
```

Use when you don't need a return value. `Job` lets you cancel/join.
A failure in a `launch` child surfaces to the
`CoroutineExceptionHandler` (or crashes, if none is installed and the
parent isn't a supervisor).

### `async` — returns a `Deferred<T>`

```kotlin
val d: Deferred<String> = scope.async { compute() }
val value: String = d.await()
```

Use when you need the result. Exceptions are **stored** in the
`Deferred` and rethrown by `await()`. You must `await()` every
`async` — otherwise exceptions vanish.

Rule of thumb: **`async` belongs inside `coroutineScope { }` when you
need parallel decomposition of a single computation.** Standalone
`scope.async { ... }` is almost always wrong — the exception won't
reach the `CoroutineExceptionHandler` until someone `await`s it.

### `coroutineScope { }` — structured fan-out inside a suspend fn

```kotlin
suspend fun loadProfile(id: UserId): Profile = coroutineScope {
    val user    = async { userRepo.get(id) }
    val prefs   = async { prefsRepo.get(id) }
    val friends = async { socialRepo.friends(id) }
    Profile(user.await(), prefs.await(), friends.await())
}
```

- Suspends until all children finish.
- Any child failure cancels siblings and rethrows from the scope.
- Parent-child job wiring is automatic.

Prefer this over building ad-hoc scopes for one-shot fan-out.

### `supervisorScope { }` — fan-out where failures are independent

```kotlin
suspend fun uploadAll(files: List<File>) = supervisorScope {
    files.forEach { f ->
        launch {
            runCatching { uploader.upload(f) }
                .onFailure { logger.warn(it) { "skip $f" } }
        }
    }
}
```

One child's failure doesn't cancel siblings. Use for independent
jobs; plain `coroutineScope` for pipelines where failure must abort.

### `withContext(ctx) { }` — run a block in a different context

```kotlin
suspend fun readFile(p: Path): String = withContext(Dispatchers.IO) {
    Files.readString(p)
}
```

Not a new scope in the ownership sense — it reuses the caller's job
and only overrides context elements you pass. Ideal for dispatcher
switching at the edge.

### `runBlocking { }` — bridge from non-suspending code

```kotlin
fun main() = runBlocking {
    app.run()
}
```

Use **only** for `main()` and tests. Never inside a coroutine — it
defeats suspension by blocking the calling thread.

## SupervisorJob vs Job

| Choose                  | Because                                                          |
|-------------------------|------------------------------------------------------------------|
| `SupervisorJob()` (on a scope) | Children are independent. One failure must not kill peers. |
| `Job()` (default)       | Pipeline semantics. Any failure aborts siblings.                 |

Do **not** put `SupervisorJob` inside a `coroutineScope { }` (via
`launch(SupervisorJob()) { ... }`) — it detaches the child from the
parent. Use `supervisorScope { }` instead.

## Shutdown

### Fire-and-forget

```kotlin
override fun close() { job.cancel() }
```

Triggers cancellation down the tree; doesn't wait for cleanup.

### Wait for cleanup

```kotlin
suspend fun closeAndJoin() { job.cancelAndJoin() }
```

Cancels then suspends until every child has finished (including
`finally` blocks and `NonCancellable` cleanup).

### JVM shutdown hook pattern

```kotlin
Runtime.getRuntime().addShutdownHook(Thread {
    runBlocking { service.closeAndJoin() }
})
```

## Anti-patterns

```kotlin
// WRONG — no owner, never cancelled
GlobalScope.launch { forever() }

// WRONG — anonymous scope
CoroutineScope(Dispatchers.IO).launch { ... }

// WRONG — plain Job where SupervisorJob is needed
val scope = CoroutineScope(Dispatchers.IO)  // default = Job()
scope.launch { sometimesFails() }
scope.launch { mustKeepRunning() }  // gets cancelled by the first's failure

// WRONG — bare async on a long-lived scope
scope.async { work() }  // exception hides until someone awaits

// WRONG — passing a CoroutineScope parameter
suspend fun bad(scope: CoroutineScope) {
    scope.launch { ... }   // escapes structured concurrency
}
// RIGHT
suspend fun good() = coroutineScope {
    launch { ... }
}

// WRONG — runBlocking inside a coroutine
suspend fun nest() = runBlocking { inner() }  // just call inner()

// WRONG — supervisorScope for a pipeline (loses fail-fast)
supervisorScope {
    launch { stageA() }
    launch { stageB() }  // stageA crash should abort stageB, but doesn't
}
```

## Pitfalls

| Symptom                                  | Cause / fix                                                              |
|------------------------------------------|--------------------------------------------------------------------------|
| "Parent coroutine is failed"             | Child used plain `Job`; promote parent to `SupervisorJob` if siblings are independent. |
| `async` exception vanishes               | Didn't `await`. Always await, or switch to `launch`.                     |
| Coroutine keeps running after shutdown   | Scope's `close()` not called, or running on `GlobalScope`.               |
| Cancellation doesn't propagate           | Created child with `Job()` without passing parent; new root detaches.    |
| Test hangs                               | Production code uses a real scope; inject `TestDispatcher`'s scope.      |
| Crash with "Job is already cancelled"    | Resubmitting to a cancelled scope. Check `isActive` or use a fresh scope.|

## Reference points

- https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html
- https://kt.academy/book/coroutines — Ch 7 (starting), Ch 8 (structured concurrency),
  Ch 11 (Job), Ch 14 (constructing a scope)
- `kotlinx.coroutines.CoroutineScope` / `SupervisorJob` / builders —
  library reference.
