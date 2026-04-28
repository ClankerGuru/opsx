---
name: coroutines-suspend-functions
description: >-
  Use when writing, designing, or reasoning about `suspend` functions —
  what suspension actually is, the Continuation machinery, bridging
  callbacks with `suspendCancellableCoroutine`, composing suspend
  functions, and the rules about where they may be called. Covers
  state-machine compilation, cooperative handoff, and why a suspend
  function is not a coroutine.
---

## When to use this skill

- Authoring a `suspend fun` for real I/O, computation, or coordination.
- Wrapping a callback-based API (HTTP client, DB driver, platform SDK)
  to expose a suspending surface.
- Deciding whether a function should be `suspend` or plain.
- Debugging "suspension point" or continuation-related errors.

## When NOT to use this skill

- You need the coroutine *builders* (`launch`/`async`/`runBlocking`) —
  see `coroutines-scopes`.
- You need Flow / Channel / StateFlow — see those skills.
- The work is CPU-bound with no I/O, no callbacks, no coordination —
  a plain function is enough; `suspend` adds no value.

## Core model

### A suspend function is just a function with extra powers

Nothing about `suspend` makes a function concurrent on its own.
`suspend` grants two capabilities:

1. The function **may pause** execution without blocking the thread.
2. The function **may call other suspend functions**.

A `suspend fun` must be invoked from another `suspend fun` or from a
coroutine builder (`launch`, `async`, `runBlocking`, `runTest`,
`coroutineScope`). The compiler enforces this.

A suspend function is **not** a coroutine. "Suspending functions are
not coroutines, just functions that can suspend a coroutine." The
coroutine is the thing launched by a builder; the suspend function is
a step inside that coroutine.

### Suspension vs blocking

| Blocking                         | Suspension                                      |
|----------------------------------|-------------------------------------------------|
| The OS thread is parked.         | The coroutine is parked; the thread is free.   |
| Thread count ≈ concurrent waits. | Coroutine count is unbounded; threads are few. |
| Stack frames held in memory.     | A small `Continuation` object holds state.     |

Suspended coroutines cost roughly the size of their captured locals,
not a thread stack.

### The Continuation

Every suspend function is compiled into a state machine. At each
suspension point, the compiler snapshots local variables plus the
resume label into a `Continuation<T>`. When the coroutine resumes,
`continuation.resumeWith(Result<T>)` drives the machine forward to
the next suspension point or to return.

You rarely see `Continuation` directly. You meet it when bridging
non-suspending code with `suspendCancellableCoroutine`.

## Writing suspend functions

### Just mark and return

```kotlin
suspend fun fetchUser(id: UserId): User {
    val raw = httpClient.get("/users/$id")   // already suspending
    return Json.decodeFromString(raw)
}
```

No ceremony. You call other suspending functions; you return a value.
There is no "suspension block".

### Return types follow ordinary rules

- Return `T` when you have one value.
- Return `List<T>` when you have a finite collection.
- Return `Flow<T>` when values stream over time — see `coroutines-flow`.
- Throw an exception for failures — callers use `runCatching` to
  recover (see below).

### Errors: return `runCatching { ... }` or throw, never `try/catch`

```kotlin
suspend fun loadProfile(id: UserId): Result<Profile> = runCatching {
    val user    = fetchUser(id)
    val prefs   = fetchPrefs(id)
    Profile(user, prefs)
}
```

- `runCatching` captures success or failure as `Result<T>`.
- It intentionally lets `CancellationException` propagate when you
  call `.getOrElse`, `.onFailure`, etc. — **provided** you don't
  convert it back to a throwable form that catches CE.
- Never wrap a whole suspend function body in `try { ... } catch (e:
  Exception) { ... }` — it swallows `CancellationException` and breaks
  structured concurrency. Use `runCatching` (see the
  `coroutines-cancellation-exceptions` skill for the precise rules).

### Place dispatcher switches at the boundary

Don't hardcode `withContext(Dispatchers.IO)` inside a shared helper.
Switch dispatcher at the *edge* — a service method or adapter:

```kotlin
class UserRepo(private val io: CoroutineDispatcher = Dispatchers.IO) {
    suspend fun get(id: UserId): User = withContext(io) {
        blockingJdbcLookup(id)
    }
}
```

Injecting the dispatcher lets tests substitute `StandardTestDispatcher`.

## Bridging callbacks with `suspendCancellableCoroutine`

The canonical pattern for turning a callback API into a suspend
function:

```kotlin
suspend fun requestUser(id: UserId): User =
    suspendCancellableCoroutine { cont ->
        val call = api.requestUser(id)
        call.enqueue(
            onSuccess = { user ->
                cont.resume(user) { _, _, _ -> /* optional cleanup on cancel */ }
            },
            onError = { error -> cont.resumeWithException(error) },
        )
        cont.invokeOnCancellation { call.cancel() }
    }
```

Rules:

- The lambda runs **immediately**, synchronously. Inside it, you
  capture `cont` and arrange for it to be resumed later.
- Resume **exactly once**. A second `resume` throws. Missing resume
  leaks the coroutine forever.
- Register an `invokeOnCancellation` handler so cancellation
  propagates back to the underlying call (close the handle, abort the
  request). Without it you leak the resource.
- Use `resume`/`resumeWithException` — the type parameter on
  `suspendCancellableCoroutine<T>` must match the value type.

Converting futures:

```kotlin
suspend fun <T> CompletableFuture<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        whenComplete { value, error ->
            if (error == null) cont.resume(value)
            else cont.resumeWithException(error)
        }
        cont.invokeOnCancellation { cancel(true) }
    }
```

(kotlinx-coroutines-jdk8 already ships this — reach for the library
extension first.)

## Composition

### Sequential

```kotlin
suspend fun loadDashboard(id: UserId): Dashboard {
    val user   = fetchUser(id)      // 1
    val prefs  = fetchPrefs(id)     // 2 — runs after 1 returns
    return Dashboard(user, prefs)
}
```

Readable, totally ordered. Use when the next step depends on the
previous one.

### Parallel decomposition

For fan-out-fan-in, use `coroutineScope { async ... awaitAll }` —
see `coroutines-scopes`. Do **not** reach for multiple parallel
`launch` when you need the values.

### `yield()` to cooperate with cancellation and fairness

```kotlin
suspend fun crunch(batches: List<Batch>) {
    batches.forEach { batch ->
        currentCoroutineContext().ensureActive()  // cancel check
        process(batch)
        yield()                                    // hand back
    }
}
```

`yield()` is a suspension point. It also throws
`CancellationException` if the coroutine is cancelled. Use it in tight
loops that otherwise wouldn't check.

## Calling rules

| From                    | May call `suspend fun`? | How                                       |
|-------------------------|-------------------------|-------------------------------------------|
| Another `suspend fun`   | Yes                     | Direct call.                              |
| A coroutine builder body (`launch {}`, `async {}`, `runBlocking {}`, `runTest {}`) | Yes | Direct call. |
| `coroutineScope {}` / `supervisorScope {}` / `withContext(...)` block | Yes | Direct call. |
| A regular function      | **No**                  | Build a coroutine (`runBlocking`) or make the caller suspend. |

The compiler error "suspend function can be called only from a
coroutine or another suspend function" means: the nearest enclosing
context isn't one of the above.

## Design rules

1. **Mark as `suspend` only if the body actually suspends** (calls
   another suspend fn, or will). Gratuitous `suspend` clutters call
   sites.
2. **Don't block inside a suspend function.** `Thread.sleep`,
   synchronous I/O on a non-IO dispatcher, and hot CPU loops all
   violate the contract. Use `delay`, `withContext(IO)`, and
   `yield`/`ensureActive`.
3. **Prefer pure suspend signatures over accepting a
   `CoroutineScope` parameter.** If a function needs to launch
   multiple child coroutines, use `coroutineScope { ... }` inside and
   stay suspending. Only accept a scope when the returned handle must
   outlive the call.
4. **Return `Flow<T>` for streams, `T` for snapshots.** A suspend
   function that returns `Flow` is unusual — the flow itself is cold
   and already suspending at collection time.
5. **Cancellation is part of the contract.** Your suspend function
   must react to cancellation — either by calling other suspending
   functions (which do) or by checking `ensureActive()`/`yield()` in
   tight loops.
6. **Document suspensions.** KDoc should mention any long-running
   suspension (network, disk, wait-for-event) so callers know where
   the boundary is.

## Pitfalls

| Symptom                                       | Cause / fix                                                                 |
|-----------------------------------------------|-----------------------------------------------------------------------------|
| "Suspend function ... can be called only from a coroutine" | Caller isn't suspending. Bridge with a builder, or make caller `suspend`.   |
| Coroutine hangs indefinitely                  | `suspendCancellableCoroutine` never called `resume`. Audit all paths.       |
| "Already resumed" crash                       | `resume` called more than once. Gate with `if (!cont.isCompleted)`.         |
| Cancellation doesn't propagate to network call | Missing `invokeOnCancellation { call.cancel() }`.                          |
| Swallowed failures                            | `try/catch (Exception)` caught `CancellationException`. Use `runCatching`. |
| UI thread blocked despite `suspend`           | Body did blocking work. Wrap in `withContext(IO)` or use an async API.      |
| Leaked continuation references                | Stored `cont` outside lambda and never resumed. Always resume or cancel.    |

## Reference points

- https://kotlinlang.org/docs/composing-suspending-functions.html
- https://kt.academy/book/coroutines — Ch 3–6 (sequence builder,
  suspension, coroutines under the hood)
- `kotlin.coroutines.Continuation` — stdlib, the contract every
  suspend function is compiled against.
