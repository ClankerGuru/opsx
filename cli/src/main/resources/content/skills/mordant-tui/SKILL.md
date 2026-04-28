---
description: "Building TUI apps with Mordant 3.x and Clikt 5.x. Terminal, animations, widgets, layout, input, progress bars."
globs:
  - "**/*.kt"
alwaysApply: false
---

## When to use this skill

- Building a terminal UI with styled output, animations, progress bars,
  or interactive input.
- Rendering tables, panels, lists, or custom widget layouts to the
  terminal.
- Integrating Clikt commands with Mordant's terminal for styled output
  and interactive prompts.

## When NOT to use this skill

- Plain `println` output with no styling or layout.
- GUI applications (Compose, Swing, JavaFX).
- Web-based dashboards or REST APIs.

---

# Terminal

## Obtaining a Terminal

```kotlin
import com.github.ajalt.mordant.terminal.Terminal

val terminal = Terminal()
```

Constructor parameters:

| Parameter          | Type               | Default        | Purpose                                        |
|--------------------|--------------------|----------------|------------------------------------------------|
| `theme`            | `Theme`            | `DEFAULT`      | Color/style overrides for widgets              |
| `tabWidth`         | `Int`              | `8`            | Spaces per tab stop                            |
| `hyperlinks`       | `Boolean?`         | `null` (auto)  | Force hyperlink support on/off                 |
| `ansiLevel`        | `AnsiLevel?`       | `null` (auto)  | `NONE`, `ANSI16`, `ANSI256`, `TRUECOLOR`      |
| `width`            | `Int?`             | `null` (auto)  | Override detected terminal width               |
| `height`           | `Int?`             | `null` (auto)  | Override detected terminal height              |
| `interactive`      | `Boolean?`         | `null` (auto)  | Force interactive mode on/off                  |
| `terminalInterface`| `TerminalInterface` | platform default | Custom I/O backend (testing, etc.)            |

## Terminal properties

```kotlin
terminal.info.width          // Int — current column count
terminal.info.height         // Int — current row count
terminal.info.ansiLevel      // AnsiLevel — detected color support
terminal.info.interactive    // Boolean — true if stdin is a tty
terminal.info.ansiHyperLinks // Boolean — hyperlink support
```

## Printing

```kotlin
terminal.print("no newline")
terminal.println("with newline")
terminal.println(panel)           // any Widget
terminal.println(table { ... })   // any Widget
```

Styled inline:

```kotlin
terminal.println("${(bold + red)("error:")} something broke")
```

## Styled output with TextColors and TextStyles

```kotlin
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*

val warn = (bold + yellow)
terminal.println(warn("warning: check your config"))
```

---

# Colors and Styles

## Named colors (ANSI 16)

`black`, `red`, `green`, `yellow`, `blue`, `magenta`, `cyan`, `white`,
`gray`, `brightRed`, `brightGreen`, `brightYellow`, `brightBlue`,
`brightMagenta`, `brightCyan`, `brightWhite`.

Each is a `TextStyle` that can be used as foreground. Use `.bg` for
background:

```kotlin
terminal.println((white on red)("alert"))
terminal.println(red.bg("red background, default foreground"))
```

## Custom colors

```kotlin
import com.github.ajalt.mordant.rendering.TextColors

TextColors.rgb("#ff6600")         // from hex string
TextColors.rgb(255, 102, 0)       // from RGB ints
TextColors.hsv(24, 100, 100)      // from HSV
TextColors.hsl(24, 100, 50)       // from HSL
TextColors.color(196)             // ANSI 256 palette index
```

## TextStyles

`bold`, `dim`, `italic`, `underline`, `inverse`, `lineThrough`,
`strikethrough`, `overline`, `hyperlink("url")`.

## Composition

Combine with `+`:

```kotlin
val header = (bold + underline + brightCyan)
terminal.println(header("Section Title"))
```

Use `on` for foreground-on-background:

```kotlin
val badge = (white on blue)
```

---

# Cursor control

```kotlin
import com.github.ajalt.mordant.terminal.Terminal

val t = Terminal()

// Movement
t.cursor.up(n)
t.cursor.down(n)
t.cursor.right(n)
t.cursor.left(n)

// Positioning
t.cursor.setPosition(row, column)   // 1-based
t.cursor.startOfLine()

// Visibility
t.cursor.hide(showOnExit = true)
t.cursor.show()

// Clearing
t.cursor.clearScreen()
t.cursor.clearScreenAfterCursor()
t.cursor.clearScreenBeforeCursor()
t.cursor.clearLine()
t.cursor.clearLineAfterCursor()
t.cursor.clearLineBeforeCursor()

// Save / restore
t.cursor.save()
t.cursor.restore()
```

---

# Widgets

All widgets implement the `Widget` interface and can be passed to
`terminal.println(widget)` or composed inside layouts.

## Text

```kotlin
import com.github.ajalt.mordant.widgets.Text

Text("plain text")
Text("styled ${bold("bold")} text")
Text("long text that will be word-wrapped", whitespace = Whitespace.NORMAL)
```

`Whitespace` enum: `NORMAL`, `NOWRAP`, `PRE`, `PRE_WRAP`, `PRE_LINE`.

`TextAlign` enum: `LEFT`, `CENTER`, `RIGHT`, `NONE`.

`OverflowWrap` enum: `NORMAL`, `BREAK_WORD`, `ELLIPSES`, `TRUNCATE`.

```kotlin
Text("centered", align = TextAlign.CENTER, width = 40)
```

## Panel

```kotlin
import com.github.ajalt.mordant.widgets.Panel

Panel(
    content = Text("Hello"),
    title = "Greeting",
    titleAlign = TextAlign.CENTER,
    borderType = BorderType.ROUNDED,
    borderStyle = TextStyle(color = brightBlue),
    expand = false,
    padding = Padding(1),      // Padding(top, right, bottom, left)
)
```

`BorderType` constants: `NONE`, `ASCII`, `SQUARE`, `ROUNDED`,
`DOUBLE`, `HEAVY`, `SQUARE_DOUBLE_SECTION_SEPARATOR`,
`HEAVY_HEAD_FOOT`.

## HorizontalRule

```kotlin
import com.github.ajalt.mordant.widgets.HorizontalRule

HorizontalRule(title = "Section", titleStyle = bold, ruleChar = "─")
```

## Spinner

```kotlin
import com.github.ajalt.mordant.widgets.Spinner

Spinner.Dots()       // ⠋ ⠙ ⠹ ... cycle
Spinner.Lines()
Spinner.ASCII()

// Advance one frame
val s = Spinner.Dots()
s.advanceTick()
terminal.println(s)
```

## Viewport

```kotlin
import com.github.ajalt.mordant.widgets.Viewport

Viewport(
    content = someWidget,
    width = 40,
    height = 10,
    scrollX = 0,
    scrollY = 0,
)
```

## SelectList

Non-interactive (display only) select list:

```kotlin
import com.github.ajalt.mordant.widgets.SelectList

SelectList(
    entries = listOf(
        SelectList.Entry("First", selected = true),
        SelectList.Entry("Second"),
        SelectList.Entry("Third"),
    ),
    title = "Choose one:",
    cursorIndex = 0,
)
```

## OrderedList / UnorderedList

```kotlin
import com.github.ajalt.mordant.widgets.OrderedList
import com.github.ajalt.mordant.widgets.UnorderedList

OrderedList(
    Text("First item"),
    Text("Second item"),
    OrderedList(Text("Nested A"), Text("Nested B")),
)

UnorderedList(
    Text("Bullet one"),
    Text("Bullet two"),
)
```

---

# Layout

## Table

```kotlin
import com.github.ajalt.mordant.table.table

val t = table {
    borderType = BorderType.ROUNDED
    borderStyle = TextStyle(color = gray)
    align = TextAlign.CENTER

    header {
        style = bold
        row("Name", "Value", "Status")
    }
    body {
        rowStyles(TextStyle(), TextStyle(dim = true))  // alternating
        row("alpha", "1", "ok")
        row("beta", "2", "fail")
        row {
            cell("spans two") { columnSpan = 2 }
            cell("single")
        }
    }
    footer {
        row("Total", "", "2")
    }
    column(0) { width = ColumnWidth.Fixed(12) }
    column(2) { align = TextAlign.RIGHT }
    captionBottom("source: internal")
}
terminal.println(t)
```

Column width types: `ColumnWidth.Auto`, `ColumnWidth.Fixed(n)`,
`ColumnWidth.Expand(weight)`.

Table padding: `padding { top = 0; bottom = 0; left = 1; right = 1 }`
inside any section or cell.

## Grid

A table without borders:

```kotlin
import com.github.ajalt.mordant.table.grid

val g = grid {
    row("Label:", "Value")
    row("Other:", "Data")
}
```

## horizontalLayout / verticalLayout

```kotlin
import com.github.ajalt.mordant.layout.horizontalLayout
import com.github.ajalt.mordant.layout.verticalLayout

val h = horizontalLayout {
    spacing = 2
    cell(Text("left"))
    cell(Text("right"))
}

val v = verticalLayout {
    cell(Text("top"))
    cell(HorizontalRule())
    cell(Text("bottom"))
}
```

---

# Animation

## Basic animation

```kotlin
import com.github.ajalt.mordant.animation.animation

val anim = terminal.animation<Int> { frame ->
    Text("Step $frame of 10")
}

for (i in 1..10) {
    anim.update(i)
    Thread.sleep(100)
}
anim.stop()    // finalizes, leaves last frame visible
// anim.clear()  // removes animation from screen entirely
```

`terminal.animation<T> { state -> Widget }` creates an `Animation<T>`.
Call `update(state)` to redraw, `stop()` to finalize, `clear()` to
erase.

## textAnimation

Shorthand when you just want to render a string:

```kotlin
import com.github.ajalt.mordant.animation.textAnimation

val anim = terminal.textAnimation<String> { it }
anim.update("loading...")
anim.update("done!")
anim.stop()
```

## Coroutine-based animation

```kotlin
import com.github.ajalt.mordant.animation.coroutines.animateInCoroutine

val anim = terminal.animation<Int> { Text("frame $it") }

coroutineScope {
    val job = anim.animateInCoroutine(this)
    job.start()
    for (i in 1..20) {
        anim.update(i)
        delay(50)
    }
    anim.stop()
}
```

---

# Progress bars

## progressBarLayout

```kotlin
import com.github.ajalt.mordant.animation.progress.progressBarLayout
import com.github.ajalt.mordant.animation.progress.advance

val layout = progressBarLayout {
    text("Downloading")
    percentage()
    progressBar()
    completed()
    speed("B/s")
    timeRemaining()
}
```

Available cells: `text(str)`, `marquee(str, width)`, `percentage()`,
`progressBar()`, `completed(suffix, precision)`,
`speed(suffix, precision)`, `timeRemaining(prefix, compact)`,
`timeElapsed(compact)`, `spinner(spinner)`.

## Thread-based progress

```kotlin
import com.github.ajalt.mordant.animation.progress.progressBarLayout
import com.github.ajalt.mordant.animation.progress.animateOnThread
import com.github.ajalt.mordant.animation.progress.advance

val progress = progressBarLayout {
    text("Working")
    progressBar()
    completed()
}.animateOnThread(terminal, total = 100L)

progress.start()
repeat(100) {
    progress.advance(1)
    Thread.sleep(20)
}
progress.stop()
```

## Coroutine-based progress

```kotlin
import com.github.ajalt.mordant.animation.progress.progressBarLayout
import com.github.ajalt.mordant.animation.progress.animateInCoroutine
import com.github.ajalt.mordant.animation.progress.advance

val progress = progressBarLayout {
    text("Processing")
    progressBar()
    percentage()
    timeRemaining()
}.animateInCoroutine(terminal, total = 200L)

coroutineScope {
    launch { progress.execute() }
    repeat(200) {
        progress.advance(1)
        delay(10)
    }
    progress.stop()
}
```

## Multi-progress

```kotlin
import com.github.ajalt.mordant.animation.progress.MultiProgressBarAnimation
import com.github.ajalt.mordant.animation.progress.progressBarLayout
import com.github.ajalt.mordant.animation.progress.advance

val layout = progressBarLayout {
    text("task")
    progressBar()
    percentage()
}

val multi = MultiProgressBarAnimation(terminal).apply {
    // Each addTask returns a ProgressTask handle
}

val task1 = multi.addTask(layout, total = 100L, definition = layout)
val task2 = multi.addTask(layout, total = 50L, definition = layout)

multi.start()
// advance individual tasks
task1.advance(10)
task2.advance(5)
multi.stop()
```

---

# Input

## Raw key events (blocking)

```kotlin
import com.github.ajalt.mordant.input.receiveKeyEvents
import com.github.ajalt.mordant.input.KeyboardEvent

terminal.receiveKeyEvents { event: KeyboardEvent ->
    when {
        event.key == "q" -> InputReceiver.Status.Finished
        event.key == "ArrowUp" -> {
            // handle up arrow
            InputReceiver.Status.Continue
        }
        event.isCtrlC -> InputReceiver.Status.Finished
        else -> InputReceiver.Status.Continue
    }
}
```

`KeyboardEvent` properties: `key: String`, `ctrl: Boolean`,
`alt: Boolean`, `shift: Boolean`, `isCtrlC: Boolean`.

Common key names: `"ArrowUp"`, `"ArrowDown"`, `"ArrowLeft"`,
`"ArrowRight"`, `"Enter"`, `"Escape"`, `"Backspace"`, `"Tab"`,
`"Delete"`, `"Home"`, `"End"`, `"PageUp"`, `"PageDown"`, `"Insert"`,
`"F1"` through `"F12"`, single characters for printable keys.

## Key events as Flow

```kotlin
import com.github.ajalt.mordant.input.receiveKeyEventsFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile

terminal.receiveKeyEventsFlow().takeWhile { event ->
    event.key != "q"
}.collect { event ->
    // handle event
}
```

## Mouse events

```kotlin
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.input.MouseTracking

terminal.receiveMouseEvents(
    tracking = MouseTracking.Normal,  // Normal, Button, Any
    withCoordinates = true,
) { event: MouseEvent ->
    // event.x, event.y, event.button, event.motion
    InputReceiver.Status.Continue
}
```

## Interactive select list

```kotlin
import com.github.ajalt.mordant.input.interactiveSelectList

val choice: String? = terminal.interactiveSelectList(
    choices = listOf("Option A", "Option B", "Option C"),
    title = "Pick one:",
)
// Returns null if user cancels (Escape/Ctrl-C)
```

## Interactive multi-select list

```kotlin
import com.github.ajalt.mordant.input.interactiveMultiSelectList

val choices: List<String>? = terminal.interactiveMultiSelectList(
    choices = listOf("Red", "Green", "Blue"),
    title = "Select colors:",
)
// Returns null if cancelled, empty list if none selected
```

## enterRawMode

For full control over terminal I/O:

```kotlin
terminal.enterRawMode().use { rawMode ->
    // rawMode.readKey(): KeyboardEvent
    // Terminal is in raw mode until close()
    val key = rawMode.readKey()
}
```

---

# TerminalInterface

Implement `TerminalInterface` for testing or custom I/O backends.

```kotlin
import com.github.ajalt.mordant.terminal.TerminalInterface
import com.github.ajalt.mordant.terminal.Terminal

class TestTerminalInterface : TerminalInterface {
    val output = StringBuilder()

    override val info: TerminalInfo get() = TerminalInfo(
        width = 80, height = 24,
        ansiLevel = AnsiLevel.TRUECOLOR,
        ansiHyperLinks = false,
        interactive = false,
    )

    override fun completePrintRequest(request: PrintRequest) {
        output.append(request.text)
        if (request.trailingLinebreak) output.append("\n")
    }

    // Override other members as needed
}

val terminal = Terminal(terminalInterface = TestTerminalInterface())
```

---

# Theme customization

```kotlin
import com.github.ajalt.mordant.rendering.Theme

val myTheme = Theme {
    styles["info"] = (bold + cyan)
    styles["warning"] = (bold + yellow)
    styles["danger"] = (bold + red)
    styles["muted"] = dim
    strings["list.bullet"] = ">"      // UnorderedList bullet character
    strings["hr.rule"] = "━"          // HorizontalRule character
    flags["markdown.code.block.border"] = false
}

val terminal = Terminal(theme = myTheme)
```

Theme entries are accessed by key. Override defaults to customize
widgets globally.

---

# Clikt integration

## NoOpCliktCommand as dispatch root

```kotlin
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.mordant.terminal.Terminal

class App : NoOpCliktCommand() {
    override fun help(context: Context) = "My CLI app"

    init {
        subcommands(Serve(), Deploy())
    }
}

fun main(args: Array<String>) = App().main(args)
```

## Accessing Terminal from Clikt

Clikt 5.x uses Mordant's Terminal. Access it via `currentContext`:

```kotlin
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal

class Greet : CliktCommand() {
    override fun help(context: Context) = "Say hello"

    override fun run() {
        currentContext.terminal.println(
            (bold + green)("Hello from Mordant!")
        )
    }
}
```

## Custom terminal in context

```kotlin
class App : NoOpCliktCommand() {
    override fun run() {
        currentContext.terminal = Terminal(
            theme = myTheme,
            ansiLevel = AnsiLevel.TRUECOLOR,
        )
    }
}
```

## Testing Clikt + Mordant

```kotlin
import com.github.ajalt.clikt.testing.test

val result = App().test("subcommand --flag value")
// result.stdout — captured output
// result.stderr — captured stderr
// result.statusCode — exit code (0 = success)
```

For testing with a controlled terminal:

```kotlin
val result = App().test(
    args = "serve --port 8080",
    width = 120,
    height = 40,
)
println(result.stdout)
```

---

# Rendering internals

## Widget interface

Every renderable implements:

```kotlin
interface Widget {
    fun measure(t: Terminal, width: Int): WidthRange
    fun render(t: Terminal, width: Int): Lines
}
```

- `measure` returns the min/max width the widget can occupy.
- `render` returns `Lines` — the styled content to display.

## Lines, Line, Span

- `Lines` — a list of `Line` objects (one per visual row).
- `Line` — a list of `Span` objects (styled text segments).
- `Span` — a styled string: `Span.word(text, style)`,
  `Span.space(count, style)`.

```kotlin
import com.github.ajalt.mordant.rendering.Lines
import com.github.ajalt.mordant.rendering.Line
import com.github.ajalt.mordant.rendering.Span
import com.github.ajalt.mordant.rendering.TextStyle

val style = TextStyle(color = TextColors.green, bold = true)
val span = Span.word("hello", style)
val line = Line(listOf(span))
val lines = Lines(listOf(line))
```

## TextStyle

```kotlin
TextStyle(
    color = TextColors.red,          // foreground
    bgColor = TextColors.white,      // background
    bold = true,
    dim = false,
    italic = false,
    underline = false,
    lineThrough = false,
    overline = false,
    inverse = false,
    hyperlink = null,                // URL string
)
```

## Size and WidthRange

```kotlin
data class WidthRange(val min: Int, val max: Int)
```

Used by `measure` to report how narrow/wide a widget can be rendered.

## BorderType constants

| Constant | Example corners |
|----------|----------------|
| `NONE`   | (no border)    |
| `ASCII`  | `+--+`         |
| `SQUARE` | `┌──┐`         |
| `ROUNDED`| `╭──╮`         |
| `DOUBLE` | `╔══╗`         |
| `HEAVY`  | `┏━━┓`         |
| `SQUARE_DOUBLE_SECTION_SEPARATOR` | Square with double lines between sections |
| `HEAVY_HEAD_FOOT` | Heavy top/bottom, square sides |

---

# FORBIDDEN

**Do NOT emit raw ANSI escape sequences.** Always use Mordant's
`TextColors`, `TextStyles`, cursor API, or `Widget` rendering. Hand-
written `\u001b[...` codes bypass Mordant's level detection and break
on terminals that don't support the assumed level.
