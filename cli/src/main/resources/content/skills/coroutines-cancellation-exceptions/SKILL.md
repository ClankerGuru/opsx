---
name: coroutines-cancellation-exceptions
description: >-
  Use when getting cancellation and exception semantics right — the
  `Job` lifecycle, cooperative cancellation, `CancellationException`
  as control flow, `ensureActive`/`yield`, `NonCancellable` cleanup,
  structured propagation, `runCatching` over `try/catch`,
  `CoroutineExceptionHandler` placement, and `supervisorScope` for
  independent failures.
---

## When to use this skill

- A coroutine "doesn't cancel" — audit cooperation.
- Deciding where to catch, what to rethrow, and where to handle.
- Composing `runCatching` with suspending code without swallowing
  `CancellationException`.
- Designing cleanup that runs even after cancellation.

## When NOT to use this skill

- Scope construction — see `coroutines-scopes`.
- Dispatcher selection — see `coroutines-context-dispatchers`.

## Hard rule: never `try/catch`, always `runCatching`

Project convention: suspending code uses `runCatching`. A plain
`try { ... } catch (e: Exception) { ... }` catches
`CancellationException` (since CE extends `Exception` via `IllegalStateException`),
which **breaks structured concurrency** — the cancellation signal is
silently swallowed and the coroutine thinks it's still alive.

`runCatching { ... }` wraps the block in a `Result<T>`. Use the
`onFailure` / `getOrElse` / `recover` variants, and **never** catch
cancellation:

```kotlin
val result: Result<User> = runCatching { fetchUser(id) }

result
    .onFailure { e ->
        currentCoroutineContext().ensureActive()   // rethrow if cancelled
        logger.warn(e) { "fetchUser failed" }
    }
    .getOrNull()
```

The `ensureActive()` inside `onFailure` is the safety belt: it
rethrows `CancellationException` if this coroutine was cancelled,
preventing silent swallow.

### One-liners

```kotlin
// Default on failure
val v = runCatching { load() }.getOrElse { fallback() }

// Transform failure
val v = runCatching { load() }.recover { 0 }.getOrThrow()

// Log and continue
runCatching { step() }.onFailure { logger.warn(it) { "step failed" } }
```

Even `runCatching` can swallow cancellation if you call
`.getOrNull()` / `.getOrElse { }` without checking active state.
**Always re-check cancellation** after examining the failure:

```kotlin
suspend fun safe() {
    runCatching { work() }
        .onFailure {
            currentCoroutineContext().ensureActive()   // ← key line
            logger.warn(it) { "work failed" }
        }
}
```

## Job lifecycle

```
         +--------+       +--------+       +------------+
  New -->| Active |------>| Completing |-->| Completed  |
         +--------+       +--------+       +------------+
              |                |
              | cancel()       | child fails (non-supervisor)
              v                v
         +-----------+    +----------+
         | Cancelling|--->| Cancelled|
         +-----------+    +----------+
```

- `isActive` — currently running, not cancelled.
- `isCompleted` — reached a terminal state (either Completed or Cancelled).
- `isCancelled` — was cancelled (CE propagated).

Read via `Job`:

```kotlin
val job: Job = scope.launch { ... }
job.isActive; job.isCompleted; job.isCancelled
```

Terminal actions:

- `cancel(cause?)` — transitions to Cancelling; children receive CE.
- `join()` — suspends until Completed or Cancelled.
- `cancelAndJoin()` — both, in order.

## Cooperative cancellation

Cancellation is delivered by throwing `CancellationException` at the
**next suspension point**. A tight CPU loop never suspends, so it
never cancels.

Suspending functions that check:

- `delay`, `yield`, `withContext`, `awaitAll`, `join`, `Flow.collect`,
  `Channel.send`/`receive`, `select`, `ensureActive` — all cooperate.

Explicit checks for loops:

```kotlin
suspend fun crunch(batches: List<Batch>) {
    batches.forEach { b ->
        currentCoroutineContext().ensureActive()   // throws CE if cancelled
        process(b)
        yield()                                     // also a suspension point
    }
}
```

`ensureActive()` vs `isActive`:

- `ensureActive()` throws `CancellationException` if cancelled — use
  in imperative code.
- `isActive` returns a boolean — use when you want to cleanly exit a
  loop, e.g. `while (isActive) { ... }`.

## CancellationException is normal control flow

`CancellationException` (CE) is **not** an error. It's the mechanism by
which cancellation travels. Rules:

1. A coroutine that catches CE and doesn't rethrow is considered
   "handled" — cancellation stops at that catch. Almost never what
   you want.
2. Throwing a CE from a child does **not** fail the parent.
3. Throwing any other exception from a child **does** fail the parent
   (unless the parent is a `SupervisorJob` / `supervisorScope`).

```kotlin
// Rethrow CE explicitly
runCatching { maybeCancellable() }
    .onFailure { if (it is CancellationException) throw it else log(it) }
```

Prefer the cleaner form:

```kotlin
runCatching { maybeCancellable() }
    .onFailure {
        currentCoroutineContext().ensureActive()  // rethrows CE via active check
        log(it)
    }
```

## `finally` and `NonCancellable`

A `finally` block in a coroutine runs even on cancellation — but the
coroutine is already cancelled, so **further suspensions throw CE**.
Wrap suspending cleanup in `NonCancellable`:

```kotlin
suspend fun useResource() {
    val r = open()
    runCatching { work(r) }
        .also {
            withContext(NonCancellable) {
                r.flushSuspending()    // allowed to suspend despite cancellation
                r.close()
            }
        }
        .onFailure {
            currentCoroutineContext().ensureActive()
            logger.error(it) { "work failed" }
        }
}
```

Keep `NonCancellable` regions small — they defeat cancellation.

## Structured propagation

| Child outcome                | Regular parent (`Job`) | Supervisor parent (`SupervisorJob` / `supervisorScope`) |
|------------------------------|------------------------|----------------------------------------------------------|
| Completes normally           | Parent unaffected.     | Parent unaffected.                                       |
| Throws `CancellationException` | Parent unaffected.    | Parent unaffected.                                       |
| Throws any other exception   | **Parent fails**; all siblings cancelled. | Failure is **stored locally** (for `async`) or routed to `CoroutineExceptionHandler` (for `launch`). Siblings unaffected. |

Picking:

- `coroutineScope { async; awaitAll }` — fail-fast pipeline.
- `supervisorScope { launch; launch }` — independent fan-out.

## `CoroutineExceptionHandler`

A last-resort net for uncaught exceptions from `launch` children.

```kotlin
val handler = CoroutineExceptionHandler { _, t ->
    logger.error(t) { "unhandled coroutine exception" }
}
val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
```

Rules:

- Must be on the **root** scope (or installed via scope context). A
  handler on a child coroutine is ignored.
- Fires only for `launch` (or outer-most coroutine) — **not** for
  `async`. `async` failures are stored in the `Deferred` and rethrown
  by `await()`.
- Does not fire if the exception is caught earlier.

Use it as observability: log, emit metric, alert. Don't use it to
"handle" errors — that's the job of `runCatching` in the coroutine
body.

## `async` failure semantics

```kotlin
// Failure vanishes here:
val d = scope.async { mightFail() }
// never awaited → no report, no handler invocation

// Safe patterns:
val result: Result<T> = runCatching { d.await() }
// or
val values = coroutineScope {
    val a = async { x() }
    val b = async { y() }
    runCatching { a.await() to b.await() }
}.getOrElse { defaultPair }
```

Always `await`. Exceptions only materialize at `await()`.

## Cancelling one branch without killing the rest

Inside a `coroutineScope`, you can't easily keep siblings alive when
one branch fails (that's the semantics). Move the cancellable branch
to its own `supervisorScope` or wrap it in `runCatching`:

```kotlin
suspend fun loadWithOptional(id: UserId): Profile = coroutineScope {
    val required = async { userRepo.get(id) }
    val optional = async {
        runCatching { recRepo.get(id) }.getOrNull()   // failures become null
    }
    Profile(required.await(), optional.await())
}
```

## Racing with `withTimeout`

```kotlin
val r = runCatching {
    withTimeout(5.seconds) { fetchRemote() }
}
r.onFailure {
    currentCoroutineContext().ensureActive()
    if (it is TimeoutCancellationException) useCache() else throw it
}
```

`withTimeout` cancels the block by throwing `TimeoutCancellationException`
(subtype of CE). `withTimeoutOrNull` returns `null` instead.

## Anti-patterns

```kotlin
// WRONG — try/catch swallows CE
try { work() } catch (e: Exception) { log(e) }

// RIGHT
runCatching { work() }.onFailure {
    currentCoroutineContext().ensureActive()
    log(it)
}

// WRONG — runCatching on a single non-suspending call is noise
runCatching { "x".toInt() }     // just use toIntOrNull() / try/catch for non-coroutine code
// runCatching is specifically a coroutine-safe guard for suspending code.

// WRONG — tight CPU loop never cancels
while (true) { crunch() }
// RIGHT
while (coroutineContext.isActive) { crunch() }

// WRONG — CoroutineExceptionHandler expecting async failures
val h = CoroutineExceptionHandler { _, _ -> /* async error? */ }
CoroutineScope(h).async { fail() }   // never fires; surfaces on await()

// WRONG — catching CE without rethrow
try { delay(1000) } catch (e: CancellationException) { /* ignored */ }

// WRONG — cleanup that suspends without NonCancellable
finally { slowClose() }  // may abort midway
// RIGHT
finally { withContext(NonCancellable) { slowClose() } }
```

## Pitfalls

| Symptom                                        | Cause / fix                                                         |
|------------------------------------------------|---------------------------------------------------------------------|
| Cancel call returns but coroutine keeps running | Tight CPU loop. Add `ensureActive()` / `yield()` / check `isActive`. |
| "Already cancelled" on next launch             | Scope was cancelled; can't reuse. Create a fresh scope.              |
| Exception handler never fires                  | Installed on child; move to root scope.                              |
| `async` error silently disappears              | Not awaited. `await` all `Deferred`.                                 |
| `CancellationException` shows up as ERROR log  | Wide catch that didn't re-check active. Use `ensureActive()` in onFailure. |
| Cleanup corrupts state                         | Cleanup suspended outside `NonCancellable` and was cancelled midway. |
| Timeout swallowed                              | `withTimeoutOrNull` returned `null`; missing retry/fallback branch.  |

## Reference points

- https://kotlinlang.org/docs/cancellation-and-timeouts.html
- https://kotlinlang.org/docs/exception-handling.html
- https://kt.academy/book/coroutines — Ch 11 (Job), Ch 12 (Cancellation), Ch 13 (Exception handling)
- `kotlinx.coroutines.CancellationException`, `NonCancellable`, `CoroutineExceptionHandler`
