---
name: coroutines-stateflow-sharedflow
description: >-
  Use when exposing hot state or broadcast events — `MutableStateFlow`
  for current-value observation, `MutableSharedFlow` for fan-out
  broadcast with replay/buffer, `stateIn`/`shareIn` to turn a cold
  `Flow` hot, `SharingStarted` (Eagerly/Lazily/WhileSubscribed),
  `update { }` for atomic state mutation, and the read-only pattern
  `asStateFlow()`/`asSharedFlow()`.
---

## When to use this skill

- A ViewModel, service, or component exposes state that UI or
  subscribers observe.
- Multiple subscribers must see the same event/value (broadcast).
- A cold flow must be shared across consumers without re-running.
- Building a cache backed by an upstream flow.

## When NOT to use this skill

- One-to-one producer/consumer with backpressure — use `Channel`.
- Cold, once-per-collector pipeline — use `Flow`.
- Purely synchronous state — use a normal property guarded by a
  `Mutex` (see `coroutines-shared-state`).

## Two hot flow primitives

| Primitive               | Semantic                                                |
|-------------------------|---------------------------------------------------------|
| `MutableStateFlow<T>`   | One current value; new subscribers see it immediately; emits only on change. |
| `MutableSharedFlow<T>`  | Broadcast with configurable replay/buffer; can represent "events". |

Both extend `Flow<T>` — use them with every Flow operator.

## `StateFlow`

```kotlin
class CounterViewModel {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    fun increment() {
        _count.update { it + 1 }
    }
}
```

Properties:

- Requires an initial value.
- `value` property exposes the current value (non-suspending read).
- **Conflating**: a fast producer may skip values that were never
  observed. Observers always see the latest.
- Only emits when `newValue != oldValue` (structural equality).
- Thread-safe; `value = ...` and `update { }` can be called from any
  context.

### Updating state

```kotlin
_state.value = newValue           // atomic assign
_state.update { it.copy(...) }    // atomic CAS with retry on contention
_state.compareAndSet(expected, newValue)
```

Use `update { }` for any derivation from current state — it retries
until the update is applied atomically. `value = ... ` is correct
only when the new value doesn't depend on the old one.

### Why `StateFlow` over `LiveData`

- Non-nullable type.
- Full coroutine / Flow operator support (`map`, `combine`, `filter`).
- Platform-agnostic; usable outside Android.

### Converting to UI

```kotlin
// Android
lifecycleScope.launch {
    viewModel.state.collect { render(it) }
}
// Or with flowWithLifecycle to auto-pause/resume.

// Desktop / CLI
scope.launch { viewModel.state.collect { render(it) } }
```

### Deriving state

```kotlin
val hasErrors: StateFlow<Boolean> = state
    .map { it.errors.isNotEmpty() }
    .stateIn(scope, SharingStarted.Eagerly, initialValue = false)
```

Use `stateIn` to cache the derived value.

## `SharedFlow`

```kotlin
class EventBus {
    private val _events = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<Event> = _events.asSharedFlow()

    suspend fun publish(e: Event) = _events.emit(e)
    fun tryPublish(e: Event) = _events.tryEmit(e)
}
```

Parameters:

- `replay` — how many past values new subscribers receive. 0 for
  events, 1 to mimic StateFlow (but prefer StateFlow for that).
- `extraBufferCapacity` — how many values can buffer beyond replay.
- `onBufferOverflow` — `SUSPEND` (default), `DROP_OLDEST`,
  `DROP_LATEST`. With a drop policy, `tryEmit` becomes non-suspending
  and always returns `true` (after dropping if necessary).

Semantics:

- Multiple subscribers each receive every emission.
- No initial value required.
- Never completes; cancel the scope owning the flow to stop it.

### When StateFlow vs SharedFlow

| Need                                | Use                    |
|-------------------------------------|------------------------|
| Current value observable + updates  | `StateFlow`            |
| One-shot events (snackbar, navigation) | `SharedFlow(replay = 0)` |
| Broadcast with replay of recent items| `SharedFlow(replay = N)`|
| Multiple subscribers, already have a Flow | `flow.shareIn(...)` |
| Single subscriber consuming an event stream | `Channel` (exactly-once semantics) |

Rule: **events that must be delivered once go through a `Channel`,
not a `SharedFlow`.** SharedFlow broadcasts; each subscriber sees
every event. A Channel delivers each value to exactly one receiver.

## Cold → Hot: `shareIn` and `stateIn`

### `shareIn` — general sharing

```kotlin
val shared: SharedFlow<Event> = sourceFlow
    .shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        replay = 1,
    )
```

- Starts the upstream in `scope`.
- All downstream collectors share the same upstream.
- Replay controls what new subscribers see.

### `stateIn` — shareIn + StateFlow

```kotlin
val state: StateFlow<UserProfile> = sourceFlow
    .stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserProfile.Empty,
    )
```

Suspend variant takes no `initialValue` and `started` — it suspends
until the first emission.

### `SharingStarted` strategies

| Strategy                    | Upstream runs when...                                              | Use when                                   |
|-----------------------------|---------------------------------------------------------------------|--------------------------------------------|
| `Eagerly`                   | Scope is created. Never stops (until scope cancels).                | State must be warm immediately; memory is cheap. |
| `Lazily`                    | First subscriber appears. Never stops.                              | Start-on-demand; values persist afterward. |
| `WhileSubscribed(stopTimeoutMs, replayExpirationMs)` | First subscriber arrives; stops after `stopTimeoutMs` without subscribers. | UI tied to lifecycle; Android ViewModel standard. |

The canonical Android pattern:

```kotlin
val state = repo.data()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
```

5 s grace period keeps upstream alive during configuration changes.

## Read-only exposure

Never expose the mutable variant:

```kotlin
private val _state = MutableStateFlow(UiState.Initial)
val state: StateFlow<UiState> = _state.asStateFlow()
// or
val state: StateFlow<UiState> = _state
```

`.asStateFlow()` / `.asSharedFlow()` return views without the
`MutableXxx` API — the compiler prevents external mutation.

## Combining state flows

```kotlin
val combined: StateFlow<DashboardState> = combine(
    userRepo.user(),
    prefsRepo.prefs(),
    notifRepo.unreadCount(),
) { user, prefs, unread ->
    DashboardState(user, prefs, unread)
}.stateIn(scope, SharingStarted.WhileSubscribed(5_000), DashboardState.Empty)
```

`combine` emits whenever any source emits. Use `stateIn` to keep the
last combined value cached.

## Collecting

```kotlin
scope.launch {
    state.collect { render(it) }   // infinite; cancels with scope
}

scope.launch {
    state
        .filter { it.isLoaded }
        .take(1)                   // collect one value then complete
        .collect { onReady(it) }
}
```

### Android lifecycle

```kotlin
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.state.collect { render(it) }
    }
}
```

`repeatOnLifecycle(STARTED)` cancels collection when the view goes
below STARTED and restarts on return. Combine with `WhileSubscribed`
in the ViewModel.

## Event handling

Don't use `StateFlow` for events (toasts, navigation) — the
configuration-change replay duplicates the event.

```kotlin
private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
val events: SharedFlow<UiEvent> = _events.asSharedFlow()

// Emit
_events.tryEmit(UiEvent.ShowToast("saved"))
```

Or, for exactly-once delivery (events that must be handled by one
consumer), use a `Channel<UiEvent>` and expose `receiveAsFlow()`.

## Anti-patterns

```kotlin
// WRONG — exposing mutable
val _state = MutableStateFlow(0)
fun state(): MutableStateFlow<Int> = _state

// WRONG — SharedFlow for events with StateFlow-style replay
MutableSharedFlow<Event>(replay = 1)  // new subscribers re-fire the last event

// WRONG — StateFlow for UI events
val _toast = MutableStateFlow<String?>(null)
// Every configuration change re-emits the same toast.

// WRONG — shareIn/stateIn without specifying started
sourceFlow.shareIn(scope, replay = 0)        // compile error actually, but logically:
// pick one: Eagerly, Lazily, or WhileSubscribed(...)

// WRONG — update { } that reads another flow inside
_state.update { current ->
    val other = otherFlow.first()            // suspending call in update; retries fire it N times
    current.copy(other = other)
}

// WRONG — MutableSharedFlow with unbounded growth
MutableSharedFlow<Log>(replay = Int.MAX_VALUE)   // memory bomb

// WRONG — creating a new SharedFlow per call
fun bus(): SharedFlow<Event> = MutableSharedFlow()   // each caller gets a different bus
```

## Pitfalls

| Symptom                                            | Cause / fix                                                              |
|----------------------------------------------------|--------------------------------------------------------------------------|
| Observer doesn't update                            | Equal value assigned — StateFlow conflates. Emit a distinct value, or use SharedFlow. |
| Observer sees skipped intermediate values          | StateFlow is conflated. Switch to SharedFlow with buffer if you need every value. |
| Replayed events cause duplicate navigation         | StateFlow used for events. Switch to SharedFlow(replay=0) or Channel.    |
| Upstream keeps running after UI gone               | Using `Eagerly`. Switch to `WhileSubscribed(...)`.                       |
| `stateIn` blocks UI on first subscribe             | Using suspend variant with slow upstream. Provide `initialValue`.        |
| Backend cache not shared across services           | Each service built its own flow. Create once, expose via `shareIn` on a common scope. |
| `update { }` seems to call lambda multiple times   | That's CAS retrying. Keep the lambda pure; no side effects or suspension.|

## Reference points

- https://kotlinlang.org/docs/flow.html#stateflow-and-sharedflow
- https://kt.academy/book/coroutines — Ch 25 (SharedFlow and StateFlow)
- `kotlinx.coroutines.flow.MutableStateFlow`, `MutableSharedFlow`,
  `shareIn`, `stateIn`, `SharingStarted`.
