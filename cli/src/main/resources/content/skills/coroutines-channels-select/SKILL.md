---
name: coroutines-channels-select
description: >-
  Use when coroutines must exchange values directly — `Channel` types
  (RENDEZVOUS, BUFFERED, UNLIMITED, CONFLATED), capacity and
  backpressure, `produce` builders, fan-out/fan-in patterns, iteration,
  `close`/`cancel`, `trySend`, and `select` for racing multiple sources
  (receive, timeout, send).
---

## When to use this skill

- Producer(s) and consumer(s) running in different coroutines that
  must exchange values.
- Backpressure is essential — slow consumer must slow the producer.
- Racing multiple inputs (first-of-many) via `select`.
- Fan-out work queue where each item goes to **one** of N workers.

## When NOT to use this skill

- Broadcasting events to **many** subscribers — use `SharedFlow`.
- Current-value state observation — use `StateFlow`.
- Cold transformation pipelines — use `Flow`.
- Simple fan-out-fan-in for one computation — use `async/awaitAll`
  (see `coroutines-scopes`).

## `Channel` basics

A `Channel<T>` is a coroutine-aware queue. One `send`, one matching
`receive`, each call can suspend.

```kotlin
val ch = Channel<Int>(capacity = 10)

launch { repeat(100) { ch.send(it) }; ch.close() }
launch { for (v in ch) process(v) }
```

Key properties:

- `send` suspends if the channel is full.
- `receive` suspends if the channel is empty.
- `close()` signals no more values; iterating / receiving drains
  remaining items, then throws `ClosedReceiveChannelException`.
- `cancel(cause?)` discards remaining items and fails further calls.

## Capacity choices

| Constructor                         | Meaning                                                    |
|-------------------------------------|------------------------------------------------------------|
| `Channel()` or `Channel(RENDEZVOUS)`| No buffer. `send` waits until a matching `receive`.        |
| `Channel(n)` where `n >= 1`         | Buffered. `send` suspends only when full.                  |
| `Channel(UNLIMITED)`                | Unbounded. `send` never suspends. Careful — memory risk.   |
| `Channel(CONFLATED)`                | Holds one value; each new `send` replaces it.              |
| `Channel(BUFFERED)`                 | Default 64 capacity.                                       |

Rules of thumb:

- Default to `RENDEZVOUS` — strongest backpressure, no hidden buffering.
- `CONFLATED` for latest-wins semantics (status updates, UI state).
- `UNLIMITED` only when the producer is strictly bounded and fast.

### Overflow policy

```kotlin
Channel<Event>(capacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
```

`BufferOverflow.DROP_OLDEST` / `DROP_LATEST` turn `send` into a
non-suspending operation at the cost of losing values. Useful for
telemetry where freshness beats completeness.

## `produce { }` — the idiomatic producer

```kotlin
fun CoroutineScope.ticker(): ReceiveChannel<Int> = produce(capacity = 16) {
    var i = 0
    while (true) {
        send(i++)
        delay(100)
    }
}
```

- Returns a `ReceiveChannel`; consumer-only API.
- Launches a coroutine bound to the receiver's scope; cancelling the
  scope cancels the producer.
- Closes automatically when the block returns.

### `consumeEach` — safe consumer

```kotlin
val prices = ticker()
prices.consumeEach { println(it) }
// on exit, channel is cancelled for this consumer; exceptions propagate
```

For `for (v in channel)` iteration, manually `channel.cancel()` on
failure if you own the channel.

## Fan-out, fan-in

### One producer → many workers (fan-out)

Each item is received by **exactly one** worker:

```kotlin
suspend fun processAll(items: ReceiveChannel<Item>) = coroutineScope {
    repeat(4) { id ->
        launch {
            for (item in items) {
                runCatching { handle(item) }
                    .onFailure {
                        currentCoroutineContext().ensureActive()
                        logger.warn(it) { "worker $id failed: $item" }
                    }
            }
        }
    }
}
```

### Many producers → one consumer (fan-in)

All producers `send` on the same channel:

```kotlin
val results = Channel<Result>()
coroutineScope {
    sources.forEach { src ->
        launch { src.stream().forEach { results.send(it) } }
    }
    launch {
        for (r in results) persist(r)
    }
}
```

Close the channel once all producers finish — typically by awaiting
their jobs and then calling `close()`.

## `trySend` / `tryReceive`

Non-suspending variants. `trySend` returns a `ChannelResult<Unit>`
indicating success, failure (closed), or a full-buffer miss.

```kotlin
val r = channel.trySend(value)
if (r.isFailure) logger.warn { "dropped $value" }
```

Use when backpressure is not wanted and dropping is acceptable. Don't
use it to avoid suspension in normal flow — prefer buffered channels.

## Closing and cancelling

- `close()` after the last `send` — signals "no more values".
  Consumers exit their `for` loop after draining.
- `close(cause)` — propagates `cause` to consumers via the thrown
  `ClosedReceiveChannelException` (wrapped).
- `cancel(cause?)` — aborts; remaining items are discarded.

For producers you own, `close` in a `finally` after the producing loop.

## `select` — race multiple sources

```kotlin
suspend fun fastest(
    left: ReceiveChannel<String>,
    right: ReceiveChannel<String>,
): String = select {
    left.onReceive  { it }
    right.onReceive { it }
}
```

`select` runs the first clause that becomes available. Supported
clauses:

| Clause                          | On                                                 |
|---------------------------------|----------------------------------------------------|
| `channel.onReceive { v -> ... }`| A value is available to receive.                   |
| `channel.onReceiveCatching { r -> ... }` | Value or close signal.                    |
| `channel.onSend(value) { ... }` | Space is available to send.                        |
| `deferred.onAwait { v -> ... }` | A `Deferred<T>` completes.                         |
| `onTimeout(ms) { ... }`         | Nothing fired within the timeout.                  |

### Timeout via select

```kotlin
val latest = select<Event?> {
    channel.onReceive { it }
    onTimeout(1_000) { null }
}
```

For simple deadlines, `withTimeout { ... }` is clearer. Reach for
`select { onTimeout }` only when combining with other clauses.

### Racing replicas

```kotlin
suspend fun firstReply(vararg nodes: suspend () -> String): String = coroutineScope {
    val deferreds = nodes.map { async { it() } }
    select {
        deferreds.forEach { d ->
            d.onAwait { result ->
                deferreds.forEach { other -> if (other !== d) other.cancel() }
                result
            }
        }
    }
}
```

Cancel the losers; the scope cleanup does the rest if you forget,
but explicit cancellation is faster.

## Channel vs Flow

| Use `Channel` when                                     | Use `Flow` when                                    |
|--------------------------------------------------------|----------------------------------------------------|
| Sending and receiving coroutines are both live.        | Single cold transformation pipeline.               |
| You need exactly-one-delivery fan-out.                 | Each collector should re-run the producer.         |
| You need `select` clauses.                             | Simple sequential operators suffice.               |
| Multiple producers feed one consumer.                  | One producer, one or many collectors.              |

Convert Flow → Channel when you need `select`:

```kotlin
val ch = flow.produceIn(scope)
```

Convert Channel → Flow when you want cold semantics:

```kotlin
val f = channel.consumeAsFlow()   // collector consumes & cancels the channel
// or
val f = channel.receiveAsFlow()   // collector only reads
```

## Anti-patterns

```kotlin
// WRONG — unbounded channel for unbounded producer
val ch = Channel<Event>(UNLIMITED)  // memory grows forever on slow consumer

// WRONG — forgetting to close
val out = Channel<Int>()
launch { produceInto(out) }  // consumer loops forever

// WRONG — receiving after cancel
ch.cancel(); ch.receive()    // ClosedReceiveChannelException

// WRONG — shared mutable state in actor body leaking out
fun CoroutineScope.actor(): SendChannel<Msg> {
    val state = mutableListOf<Item>()
    launch { for (m in inbox) state.add(m.item) }
    return inbox
    // `state` must not be read from outside the actor coroutine
}

// WRONG — select with a non-select clause
select {
    delay(1000) { ... }       // delay is not a select clause; use onTimeout
}

// WRONG — using Channel when Flow would do
Channel<Int>().also { launch { it.send(1) } }
// Just flowOf(1).collect { ... }
```

## Pitfalls

| Symptom                                       | Cause / fix                                                                |
|-----------------------------------------------|----------------------------------------------------------------------------|
| `ClosedSendChannelException`                  | Sending after `close` or `cancel`. Guard with `isClosedForSend`.          |
| Consumer hangs forever                        | Producer never closes the channel. Close in `finally`.                     |
| Memory blows up                               | `UNLIMITED` with slow consumer. Switch to RENDEZVOUS or bounded capacity.  |
| Values out of order across consumers          | Fan-out on a single channel doesn't preserve per-source order. Use a per-source flow. |
| `select` biases to one branch                 | `select` is unbiased by default; use `selectUnbiased` if order of clauses in code matters. |
| Backpressure doesn't propagate                | `onBufferOverflow = DROP_*` discards silently. Remove the policy or log drops. |

## Reference points

- https://kotlinlang.org/docs/channels.html
- https://kt.academy/book/coroutines — Ch 17 (Channel), Ch 18 (Select)
- `kotlinx.coroutines.channels.Channel`, `produce`, `select`, `onTimeout`.
