---
name: mosaic
description: >-
  Use when building a terminal UI in Kotlin with JakeWharton/mosaic — the
  Compose-for-terminal runtime. Covers `runMosaic`/`runMosaicBlocking`
  entrypoints, the composable tree (`Text`, `Row`, `Column`, `Box`,
  `Spacer`, `Filler`, `Static`), modifier system, state management
  (`remember`, `mutableStateOf`, `produceState`), effects (`LaunchedEffect`,
  `DisposableEffect`), keyboard input via `LocalTerminal`, ANSI styling
  (`SpanStyle`, `Color`, `TextStyle`), the Compose-compiler Gradle plugin
  (`com.jakewharton.mosaic`), testing with `runMosaicTest`, and the common
  pitfalls (redraw thrash, blocking the render coroutine, composition-order
  bugs).
---

## When to use this skill

- Replacing an ad-hoc ANSI print/`\r`-rewind loop with a declarative
  tree — dashboards, progress renderers, interactive prompts, live
  tailers.
- Porting a Compose mental model (state, recomposition, effects) to a
  terminal program.
- Building a CLI TUI that plays well with coroutines and suspending
  I/O — Mosaic's runtime is a suspending render loop.

## When NOT to use this skill

- One-shot CLI output. If you `println` once and exit, Mosaic is
  overkill — just `println`.
- Full-screen TUIs with mouse support, modal panes, scroll regions —
  use Lanterna, jexer, or a real ncurses binding. Mosaic is
  line-oriented; it writes the same region over and over with ANSI
  cursor moves.
- JVM-only when you need KMP: Mosaic targets JVM and Kotlin Native
  (Linux x64, macOS, mingw). For JS/WasmJs or Android the
  ecosystem is not there.

## Installation

Mosaic is a Gradle plugin that applies the Compose compiler to a
non-Android Kotlin module. Declare the plugin, then add the
runtime dependency.

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// build.gradle.kts
plugins {
    kotlin("jvm") version "2.1.0"
    id("com.jakewharton.mosaic") version "0.17.0"
}

dependencies {
    implementation("com.jakewharton.mosaic:mosaic-runtime:0.17.0")
    testImplementation("com.jakewharton.mosaic:mosaic-testing:0.17.0")
}
```

For Kotlin Multiplatform, apply the plugin on a JVM or Native
target and add `mosaic-runtime` to the relevant source set.

The plugin wires the Compose compiler into `compileKotlin` so any
`@Composable` function in the module is processed. **Do not** also
apply `org.jetbrains.compose` — that brings in Compose UI and
conflicts with Mosaic's runtime.

## Minimum viable program

```kotlin
import com.jakewharton.mosaic.runMosaicBlocking
import com.jakewharton.mosaic.ui.Text

fun main() = runMosaicBlocking {
    Text("hello, terminal")
}
```

`runMosaicBlocking` blocks the main thread until the block returns
(i.e. the composition finishes). Use it for short programs and
`main` functions. For long-running rendering inside a coroutine
scope, use `runMosaic { ... }` (suspend).

## Runtime entry points

| Function | Purpose |
|---|---|
| `runMosaic { ... }` | Suspending. Starts a render loop on a child `CoroutineScope`; returns when the `content` composition completes. |
| `runMosaicBlocking { ... }` | Same, but blocks the calling thread. Use from `fun main()`. |
| `runMosaicTest { ... }` | Headless. Captures each frame into a list for assertions. See §Testing. |

The `content` lambda is `@Composable () -> Unit`. The render loop
starts a new frame whenever a `State<*>` that was read during the
last composition changes — exactly the Compose contract.

## Core composables

```kotlin
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.*

@Composable
fun Dashboard() {
    Column {
        Text("opsx status")
        Spacer(Modifier.height(1))
        Row {
            Text("in-progress: ")
            Text("3")
        }
        Box(Modifier.width(20)) {
            Text("left")
            Text("right", Modifier.align(Alignment.End))
        }
    }
}
```

| Composable | Use |
|---|---|
| `Text(text, modifier, style, color)` | A styled string. `text` can be `String` or `AnnotatedString` for multi-style runs. |
| `Row { ... }` | Children laid out left-to-right. Accepts `mainAxisAlignment` and `crossAxisAlignment`. |
| `Column { ... }` | Children laid out top-to-bottom. Same alignment API. |
| `Box { ... }` | Z-stacks children at the same position; use `Modifier.align(...)` per child. |
| `Spacer(Modifier.height(n))` | Fixed empty cells. |
| `Filler(...)` | Paints a single cell repeated to fill parent; useful for backgrounds. |
| `Static { ... }` | Children render **once** above the dynamic area and are never redrawn. Use for scrollback-safe log lines. See §Static block. |

## Modifiers

Mosaic reuses a modifier system close to Compose UI's. Common
modifiers:

```kotlin
Text(
    "label",
    Modifier
        .padding(horizontal = 1)
        .background(Color.Blue)
        .border(BorderStroke(1, Color.White)),
)
```

- `Modifier.width(n)` / `height(n)` — fixed cell dimensions.
- `Modifier.fillMaxWidth()` / `fillMaxHeight()` — take all parent space.
- `Modifier.padding(...)` — outside the content.
- `Modifier.background(color)` — ANSI background fill.
- `Modifier.border(BorderStroke)` — box-drawing border.
- `Modifier.align(Alignment)` — inside `Box`; edge of `Row`/`Column`.

Don't invent modifiers by extending the class — Mosaic's
`Modifier` is a chain interface; custom entries require a
layout-node implementation and are almost always unnecessary.

## State and recomposition

`@Composable` functions read from `State<T>` objects. When a read
value changes the runtime recomposes — same rules as Jetpack Compose.

```kotlin
@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            count++
        }
    }

    Text("elapsed: ${count}s")
}
```

- `remember { ... }` keeps a value across recompositions of the
  same call site.
- `mutableStateOf(initial)` returns a `MutableState<T>` whose
  reads and writes are tracked.
- `by` delegate on `State<T>` lets you read as a plain `T`.
- `derivedStateOf { ... }` memoizes a derived value whose inputs
  are other tracked reads.
- `produceState(initial, key)` launches a coroutine that sets
  the state and cancels when `key` changes.

## Effects

Use effects to start, stop, and coordinate coroutines around the
composition lifecycle — not to mutate state directly from
`@Composable` bodies.

```kotlin
@Composable
fun Tailer(path: Path) {
    val lines = remember { mutableStateListOf<String>() }

    LaunchedEffect(path) {
        path.tailFlow().collect { lines += it }
    }

    Column {
        lines.takeLast(10).forEach { Text(it) }
    }
}
```

- `LaunchedEffect(key1, ...) { ... }` — launches a suspend block
  inside the composition's `CoroutineScope`. Cancels + restarts
  when keys change; cancels when the composable leaves.
- `DisposableEffect(key1, ...) { ...; onDispose { ... } }` — for
  non-suspend setup/teardown (attach/detach listeners).
- `rememberCoroutineScope()` — for callback-driven launches.

## Keyboard input

Mosaic gives you access to key events via `LocalTerminal` or the
`Modifier.onKeyEvent` handler (on focusable composables).

```kotlin
@Composable
fun Menu(items: List<String>) {
    var index by remember { mutableStateOf(0) }
    val terminal = LocalTerminal.current

    LaunchedEffect(Unit) {
        terminal.keys.collect { event ->
            when (event.key) {
                Key.ArrowUp   -> index = (index - 1).coerceAtLeast(0)
                Key.ArrowDown -> index = (index + 1).coerceAtMost(items.lastIndex)
                Key.Enter     -> { /* commit */ }
                else -> {}
            }
        }
    }

    Column {
        items.forEachIndexed { i, label ->
            Text(if (i == index) "> $label" else "  $label")
        }
    }
}
```

Terminal input requires the stdin stream to be a real TTY. When
running through Gradle or a pipe you will not receive key events —
use `System.console()` checks if your program must degrade.

## Styling and colors

Mosaic ships an ANSI color palette and bold/italic/underline
text styles. Text can be styled three ways:

```kotlin
// 1. Parameters on Text
Text("warn", color = Color.Yellow, background = Color.Black)

// 2. TextStyle
Text("bold", style = TextStyle(fontWeight = FontWeight.Bold))

// 3. AnnotatedString for mixed runs in one line
Text(
    buildAnnotatedString {
        append("ok ")
        withStyle(SpanStyle(color = Color.Green)) { append("✓") }
    }
)
```

- `Color` — named ANSI colors (`Red`, `Green`, ...), 256-color
  indexes (`Color.fromAnsi256(208)`), or truecolor
  (`Color(0xAA, 0x55, 0x11)`).
- `TextStyle(fontWeight, fontStyle, textDecoration)` — bold,
  italic, underline, strikethrough.
- Truecolor is emitted as 24-bit ANSI and falls back to 256-color
  automatically when the detected terminal doesn't support it.

## The `Static` block

`Static { ... }` emits its children **above** the region Mosaic
keeps redrawing. Children are printed once and scroll off the
top as the dynamic region grows. Use this for log lines you
want preserved in the terminal's scrollback.

```kotlin
@Composable
fun Progress(completed: List<String>) {
    Static(completed) {
        it.forEach { line -> Text(line) }
    }
    Text("working...")
}
```

The `items` parameter to `Static` is the **append-only** list of
content to emit. When its size grows, the new items print once
into scrollback. Shrinking the list is a bug — Mosaic cannot
erase what it already flushed.

## Testing

`mosaic-testing` renders a composition into a captured list of
frames for assertion.

```kotlin
import com.jakewharton.mosaic.testing.runMosaicTest

@Test
fun counterIncrements() = runBlocking {
    runMosaicTest {
        setContent { Counter() }

        val first = awaitFrame()
        assertThat(first).contains("elapsed: 0s")

        advanceTimeBy(1_000)
        val second = awaitFrame()
        assertThat(second).contains("elapsed: 1s")
    }
}
```

- `setContent { ... }` starts the composition.
- `awaitFrame()` suspends until the next rendered frame.
- Inside `runMosaicTest`, the clock is virtual — `advanceTimeBy`
  jumps `delay(...)` calls forward without real waiting.
- Frames are plain strings with ANSI escapes stripped, so
  assertions stay readable.

## Gradle wiring example

```kotlin
// build.gradle.kts for the opsx CLI's status subcommand
plugins {
    kotlin("jvm")
    id("com.jakewharton.mosaic") version "0.17.0"
    application
}

dependencies {
    implementation("com.jakewharton.mosaic:mosaic-runtime:0.17.0")
    implementation("com.github.ajalt.clikt:clikt:5.0.1")
}

application {
    mainClass = "zone.clanker.opsx.cli.MainKt"
}
```

Mosaic's compose-compiler plugin shows up as a Gradle build
dependency automatically when the `com.jakewharton.mosaic`
plugin is applied.

## Anti-patterns

- **Writing to `println` from inside a composable.** Composables
  must be side-effect-free. Use `Text(...)` for output and
  `LaunchedEffect` for anything else. A stray `println` tears
  Mosaic's ANSI positioning.
- **Blocking inside `LaunchedEffect`.** Anything that blocks the
  dispatcher (e.g. `Thread.sleep`, JDBC call on `Dispatchers.Main`
  equivalent) starves the render coroutine. Suspend properly.
- **Mutating a `MutableState` from multiple threads.** `State`
  reads/writes are not thread-safe across dispatchers; hop onto
  the composition's scope or use a `Channel` + `LaunchedEffect`.
- **Relying on `println` for `Static` content.** `Static { ... }`
  is the Mosaic-blessed way to push lines into scrollback;
  `println` from a coroutine races the render loop.
- **Calling `runMosaicBlocking` from inside a coroutine.** It
  blocks a thread; you want `runMosaic { }` (suspend).
- **Applying `org.jetbrains.compose` alongside the Mosaic
  plugin.** Two compose-compiler registrations cause obscure
  `ComposableFunction0` link errors at runtime.

## Pitfalls

- The runtime assumes ANSI + UTF-8. Windows consoles need a
  modern Windows Terminal or `WT_SESSION` env; old `cmd.exe` gets
  garbled box-drawing.
- Terminal width is polled on each frame. Rapidly resizing the
  window during a frame can race and produce one clipped frame;
  the next frame recovers. Don't assert exact width in tests.
- `Static` content is write-once. If you want to amend an emitted
  row, keep it out of `Static` and inside the dynamic region.
- `runMosaicBlocking` sinks `SIGINT` by default. Wrap your call
  in a shutdown hook if your CLI needs to clean up on Ctrl-C.
- Recomposing on every tick of a millisecond-resolution clock is
  cheap but **not free** — the ANSI diff is O(cells). Throttle
  high-frequency state (`StateFlow.sample(100.milliseconds)`)
  before pushing it into composition.

## References

- Repo — https://github.com/JakeWharton/mosaic
- Samples — `samples/` directory in the repo (counter, robot,
  jest, etc.) show idiomatic composables.
- Compose runtime docs — Mosaic piggybacks on the same
  `androidx.compose.runtime` semantics (state, recomposition,
  effects); see https://developer.android.com/jetpack/compose/
  mental-model for background.
- Release notes — `CHANGELOG.md` in the repo tracks API breaks
  between `0.1x` releases; the API is still pre-1.0.
