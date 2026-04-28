---
name: clikt
description: >-
  Use when building a Kotlin command-line application with Clikt —
  `CliktCommand` / `NoOpCliktCommand`, `option()` and `argument()` with
  all built-in converters (`int`/`long`/`float`/`double`/`boolean`/
  `choice`/`enum`/`file`/`path`/`inputStream`/`outputStream`/`convert`),
  flags, counted flags, feature switches, multi-value options
  (`pair`/`triple`/`split`/`splitPair`/`multiple`/`unique`/`associate`/
  `varargValues`/`optionalValue`/`transformValues`), prompting and
  passwords, option groups (`mutuallyExclusiveOptions`/`cooccurring`/
  `groupChoice`/`groupSwitch`), envvars and value sources, subcommands
  (registration, context sharing via `findObject`/`findOrSetObject`/
  `requireObject`, `invokeWithoutSubcommand`, `allowMultipleSubcommands`,
  `ChainedCliktCommand`), eager options, deprecated options,
  `treatUnknownOptionsAsArgs`, terminal and `echo`, testing via
  `.test()`, and exception handling with `CliktError`, `UsageError`,
  `PrintMessage`, `PrintHelpMessage`.
---

## When to use this skill

- Building any Kotlin CLI that needs argument parsing, help generation,
  subcommands, typed parameters, shell completion, or interactive
  prompts.
- Migrating from hand-rolled `args: Array<String>` parsing.
- Replacing a Java-first library (picocli, commons-cli, JCommander) in
  a Kotlin-first project.

## When NOT to use this skill

- Single-file scripts with zero flags — just read `args` directly.
- Deeply dynamic command structures generated at runtime — Clikt wants
  classes. Consider a data-driven parser instead.

## Project convention

**Use `runCatching`, never bare `try/catch`.** When invoking a command
from code that must not exit the JVM, wrap `parse(args)` in
`runCatching` and inspect the result rather than relying on
`main(args)` (which calls `exitProcess`).

## Minimum viable command

Subclass `CliktCommand`, override `run()`, call `main(args)` from `fun
main`.

```kotlin
class Hello : CliktCommand() {
    override fun run() {
        echo("hello")
    }
}

fun main(args: Array<String>) = Hello().main(args)
```

`NoOpCliktCommand` is the right base for a parent that only dispatches
to subcommands — its `run()` is already empty.

## Options vs arguments

| `option("-n", "--name")` | `argument()`                    |
|--------------------------|---------------------------------|
| Named; declared anywhere | Positional; ordered declaration |
| Defaults to `null`       | Required by default             |
| Zero or more             | Exactly one by default          |
| Can have short + long    | No names                        |

Both return property delegates via `by`.

```kotlin
val count: Int? by option("-n", "--count", help = "Number of greetings").int()
val name: String by argument(help = "Who to greet")
```

## Option & argument names

- Auto-generated from property name: `val fooBar by option()` → `--foo-bar`.
- Override with one or more explicit names: `option("-f", "--file")`.
- `option().help("...")` or `help = "..."` parameter — both work.
- `option(hidden = true)` omits from help but keeps the option functional.

## Built-in type converters

Every converter is chained onto the delegate. Order matters — converters
must come before `.default`/`.required`/`.multiple`/etc.

```kotlin
val port:     Int     by option().int().restrictTo(1..65_535).default(8080)
val ratio:    Double  by option().double().default(1.0)
val retries:  Long    by option().long().default(3)
val enabled:  Boolean by option().boolean().default(true)
val count:    UInt    by option().uint().default(0u)
val config:   Path    by option().path(mustExist = true, canBeFile = true, canBeDir = false)
val output:   File    by argument().file(canBeDir = false)
val input:    java.io.InputStream  by option().inputStream()            // "-" = stdin
val sink:     java.io.OutputStream by option().outputStream()           // "-" = stdout
val format:   String  by option().choice("json", "yaml", "toml")
val level:    Level   by option().enum<Level>()                         // case-insensitive
```

### Paths & files — the full parameter surface

```kotlin
option().path(
    mustExist = true,
    canBeFile = true,
    canBeDir = false,
    mustBeReadable = true,
    mustBeWritable = false,
    canBeSymlink = true,
)
```

Same knobs on `.file(...)`. Prefer `path()` (NIO) for new code.

### Validation: `check` vs `validate`

```kotlin
val port by option().int().check("must be privileged") { it < 1024 }
val path by option().path().validate { require(it.isAbsolute) { "$it must be absolute" } }
```

- `check { boolean }` — terse; the lambda returns `Boolean`.
- `validate { ... require ... }` — richer; use `require`/`fail` with
  context. Runs *after* conversion.

## Custom converters

Any type is reachable via `.convert { }`. Call `fail(message)` for
input validation.

```kotlin
data class HostPort(val host: String, val port: Int)

val endpoint: HostPort by option().convert { raw ->
    val parts = raw.split(":")
    if (parts.size != 2) fail("expected host:port, got $raw")
    HostPort(parts[0], parts[1].toIntOrNull() ?: fail("bad port"))
}.required()
```

Converters are composable — `option().convert { ... }.default(...)`
works.

## Flags & switches

### `flag` — boolean option

```kotlin
val verbose by option("-v", "--verbose").flag("-q", "--quiet", default = false)
```

Pass a list of "secondary" names that set the flag to `false`. The
last occurrence on the command line wins.

### `counted` — accumulate occurrences

```kotlin
val verbosity: Int by option("-v").counted()
// -vvv → verbosity == 3
```

### `switch` — map names to values

```kotlin
val level by option().switch(
    "--debug" to "DEBUG",
    "--info"  to "INFO",
    "--warn"  to "WARN",
).default("INFO")
```

Each name maps to a discrete value; the user picks at most one.

### Feature switch — mutually exclusive flags with different values

Same mechanism as `switch` but typed; good for enabling/disabling a
feature that has a default.

```kotlin
val color: Boolean by option().switch(
    "--color"    to true,
    "--no-color" to false,
).default(true)
```

## Multi-value helpers

### `.pair()` / `.triple()`

Option takes 2 or 3 values and returns a `Pair` / `Triple`.

```kotlin
val pos: Pair<Int, Int>? by option().int().pair()
// --pos 10 20  →  Pair(10, 20)
```

### `.split(delimiter)` — one argument, many values

```kotlin
val tags: List<String>? by option().split(",")
// --tags a,b,c
```

### `.splitPair(delimiter)` — `key=value` in one token

```kotlin
val kv: Pair<String, String>? by option().splitPair("=")
// --kv host=localhost
```

### `.multiple()` — allow repetition

```kotlin
val includes: List<Path> by option("-I").path().multiple()
// -I /a -I /b -I /c
```

`.multiple(required = true)` demands at least one occurrence.

### `.unique()` — deduplicate

```kotlin
val tags: Set<String> by option().multiple().unique()
```

### `.associate()` — `--header k=v --header k2=v2`

```kotlin
val headers: Map<String, String> by option("-H").associate()
// -H Content-Type=json -H X-Trace-Id=abc
```

`.associate(delimiter = "=")` overrides the separator. `associateBy`
/ `associateWith` provide key- or value-only variants.

### `.varargValues()` / `.optionalValue()` / `.transformValues(n)`

```kotlin
val files by option().varargValues()          // --files a b c  (stops at next --opt)
val theme by option().optionalValue("dark")    // --theme       → "dark"
                                               // --theme light → "light"
val range by option().transformValues(2) { (lo, hi) -> lo.toInt()..hi.toInt() }
```

`transformValues(n)` is the low-level primitive `pair`/`triple` are
built on.

## Prompting & passwords

Prompt the user when a value isn't on the command line.

```kotlin
val name: String by option().prompt("Your name")
val pwd:  String by option().prompt("Password", hideInput = true, requireConfirmation = true)
```

`prompt(...)` reads until EOF or newline. `hideInput = true` uses the
terminal's secret input. Use `option().password()` as a shortcut for
hidden + confirmed secrets.

## Arguments — variadic and typed

```kotlin
val files: List<Path> by argument().path(mustExist = true).multiple(required = true)
val main:  Path       by argument().path(mustExist = true)
val rest:  List<String> by argument().multiple()
```

Rules:

- At most one variadic (`.multiple()`) argument per command.
- Required arguments come before optional; optional before variadic.
- `argument().default(x)` makes it optional.

## Option groups

Groups solve "these options belong together" and "only one of these
sets may appear".

### `mutuallyExclusiveOptions` — one-of-N

```kotlin
class Load : OptionGroup()
class FromFile : Load() { val path by option().path().required() }
class FromUrl  : Load() { val url  by option().required() }

class Cmd : CliktCommand() {
    val source: Load by mutuallyExclusiveOptions(FromFile(), FromUrl()).required()
    override fun run() = when (val s = source) {
        is FromFile -> loadFile(s.path)
        is FromUrl  -> loadUrl(s.url)
    }
}
```

### `cooccurring` — all-or-nothing

```kotlin
class Auth : OptionGroup() {
    val user by option().required()
    val pass by option().required()
}

class Cmd : CliktCommand() {
    val auth: Auth? by Auth().cooccurring()
    override fun run() { auth?.let { login(it.user, it.pass) } }
}
```

If *any* option in the group is given, *all* must be given.

### `groupChoice` — select a group by option value

```kotlin
class ServeMode : OptionGroup()
class Http  : ServeMode() { val port by option().int().default(80) }
class Https : ServeMode() { val port by option().int().default(443); val cert by option().path().required() }

class Cmd : CliktCommand() {
    val mode: ServeMode by option().groupChoice(
        "http"  to Http(),
        "https" to Https(),
    ).defaultByName("http")
}
```

### `groupSwitch` — select a group by flag name

```kotlin
val mode: ServeMode by option().groupSwitch(
    "--http"  to Http(),
    "--https" to Https(),
).defaultByName("--http")
```

## Environment variables & value sources

### `envvar` per option

```kotlin
val apiKey: String by option(envvar = "API_KEY").required()
```

### Auto-prefix all envvars

```kotlin
class App : CliktCommand() {
    init { context { autoEnvvarPrefix = "APP" } }
    val port by option().int()    // reads APP_PORT
}
```

### Config files via `ValueSource`

```kotlin
class App : CliktCommand() {
    init {
        context {
            valueSource = PropertiesValueSource.from("config.properties")
            readEnvvarBeforeValueSource = true   // env wins over file
        }
    }
}
```

`MapValueSource`, `PropertiesValueSource`, and (via `clikt-hocon` etc.)
JSON/TOML/HOCON are all available. Lookup priority (default):

1. Command line
2. Environment variable
3. Value source (file)
4. `.default(...)`

`readEnvvarBeforeValueSource` swaps steps 2/3.

## Subcommands

```kotlin
class Tool : NoOpCliktCommand(name = "tool")

class Build : CliktCommand() {
    override fun run() { echo("build") }
}

class Test : CliktCommand() {
    val filter by option()
    override fun run() { echo("test filter=$filter") }
}

fun main(args: Array<String>) =
    Tool().subcommands(Build(), Test()).main(args)
```

### Execution order

1. Parent parses its own options.
2. Parent `run()` executes.
3. Child parses its own options.
4. Child `run()` executes.

So parent `run()` is shared setup; child `run()` sees parsed state on
the parent.

### Context / object sharing

Use `currentContext.obj` to carry state between parent and child.

```kotlin
class Root : CliktCommand() {
    val verbose by option().flag()
    override fun run() {
        currentContext.findOrSetObject { Config(verbose) }
    }
}

class Child : CliktCommand() {
    private val config by requireObject<Config>()
    override fun run() { if (config.verbose) echo("hi") }
}
```

- `findObject<T>()` — walk up the context chain; returns `T?`.
- `findOrSetObject<T> { factory }` — find or create in *this* context.
- `requireObject<T>()` — delegate that throws if not found.

### `invokeWithoutSubcommand`

By default, a parent with registered subcommands **requires** a
subcommand. Allow the parent to run alone:

```kotlin
class Root : CliktCommand() {
    init { context { invokeWithoutSubcommand = true } }
    override fun run() {
        if (currentContext.invokedSubcommand == null) echo("no subcommand")
    }
}
```

### `allowMultipleSubcommands`

Lets the user chain subcommands in one invocation:

```kotlin
class Pipeline : CliktCommand() {
    init { context { allowMultipleSubcommands = true } }
}

fun main(args: Array<String>) =
    Pipeline().subcommands(Fetch(), Transform(), Load()).main(args)
// ./pipeline fetch --url x transform --filter y load --dest z
```

### `ChainedCliktCommand<T>` — pass state through a chain

When multiple subcommands run in sequence, `ChainedCliktCommand<T>`
makes each `run()` return a value threaded to the next:

```kotlin
class Root : ChainedCliktCommand<Context>() {
    override fun run(value: Context): Context = value
}

class Stage : ChainedCliktCommand<Context>() {
    override fun run(value: Context): Context = value.copy(steps = value.steps + "stage")
}

fun main(args: Array<String>) {
    val final = Root().subcommands(Stage()).main(args, initial = Context())
    println(final.steps)
}
```

## Eager options

Eager options (like `--help`, `--version`) run immediately, before
parsing the rest, and typically exit via `PrintMessage`.

```kotlin
class App : CliktCommand() {
    init {
        eagerOption("--version", help = "Show version") {
            throw PrintMessage("1.2.3")
        }
    }
}
```

Built-in `versionOption("1.2.3")` does the same.

## Deprecated options

```kotlin
val host by option().deprecated("use --endpoint")
val legacy by option().deprecated("removed", error = true)  // fails if supplied
```

Warnings go to stderr; `error = true` converts to a hard failure.

## Unknown tokens → arguments

Useful for pass-through CLIs (wrappers around `docker`, `kubectl`, ...):

```kotlin
class Runner : CliktCommand() {
    init { context { allowInterspersedArgs = false } }
    val passthrough: List<String> by argument().multiple()
    override fun run() { exec("docker", *passthrough.toTypedArray()) }
}
```

For "unknown options become arguments":

```kotlin
class Runner : CliktCommand(treatUnknownOptionsAsArgs = true)
```

Both often combined with `allowInterspersedArgs = false` to stop option
parsing at the first argument.

## Terminal I/O — always use `echo`

```kotlin
echo("normal")
echo("error!", err = true)
echo("no newline", trailingNewline = false)
```

`echo` routes through the command's `Terminal`, so tests can capture
it, ANSI codes render correctly, and encoding respects the platform.
Never use `println` in a `CliktCommand`.

Read input:

```kotlin
val line = terminal.readLineOrNull(hideInput = false)
```

## Exception handling

| Throw                  | Behavior                                           |
|------------------------|----------------------------------------------------|
| `CliktError(msg, code)`| Print `msg` to stderr, exit `code` (default 1).    |
| `UsageError(msg)`      | Print `msg` + command usage, exit 1.               |
| `BadParameterValue`    | Like `UsageError` but tied to a parameter.         |
| `MissingOption`/`MissingArgument` | Built-in; thrown by framework.          |
| `PrintMessage(msg)`    | Print `msg` to stdout, exit 0.                     |
| `PrintHelpMessage`     | Print full help, exit 0.                           |
| `Abort`                | User cancelled (Ctrl-C on prompt); exit code 1.    |

Throw these from `run()` for user-facing failures. Let real bugs
propagate — Clikt's default handler prints the stack trace for
unexpected exceptions.

```kotlin
if (!target.exists()) {
    throw UsageError("target not found: $target", paramName = "target")
}
if (serverDown) {
    throw CliktError("server unreachable", statusCode = 2)
}
```

### `issueMessage` — soft warnings

```kotlin
override fun run() {
    if (deprecatedFlagSet) issueMessage("--old is deprecated, use --new")
    // ...
}
```

Shown after the command completes successfully; no exception.

### `registerCloseable`

For resources that must be closed on shutdown (success or failure):

```kotlin
val conn = registerCloseable(openConnection())
```

## Testing

Depend on `com.github.ajalt.clikt:clikt-test:<version>`. Use `.test()`:

```kotlin
class GreetTest : BehaviorSpec({
    given("two repetitions") {
        `when`("invoked") {
            val result = Greet().test("--count 2 Ada")
            then("greets twice") {
                result.statusCode shouldBe 0
                result.stdout shouldBe "hello Ada\nhello Ada\n"
            }
        }
    }
})
```

`.test(...)` returns a `CliktCommandTestResult`:

| Property         | Contents                                         |
|------------------|--------------------------------------------------|
| `stdout`         | Captured stdout.                                 |
| `stderr`         | Captured stderr.                                 |
| `output`         | Combined, in emission order.                     |
| `statusCode`     | 0 on success, 1+ on `CliktError`.                |

Pass `stdin = "..."` to feed prompted input. Pass `envvars =
mapOf(...)` to inject environment variables. Pass `width = N` to pin
the help formatting width.

### Parsing without exiting the JVM

`main(args)` calls `exitProcess`. In embedded contexts, use `parse`:

```kotlin
val result = runCatching { cmd.parse(args) }
result.onFailure { e ->
    when (e) {
        is CliktError -> echo(e.message, err = true)
        else -> throw e
    }
}
```

## Help formatting

- `help = "..."` on `option`/`argument`/`CliktCommand`.
- `CliktCommand(help = "...")` or override `val commandHelp`.
- Markdown rendering: install `clikt-markdown` and
  `context { helpFormatter = { MordantMarkdownHelpFormatter(it) } }`.
- Group options into sections with `OptionGroup(name = "Section")`.
- `metavar = "PATH"` customizes the placeholder shown in help.

## Anti-patterns

```kotlin
// WRONG — accessing delegates in init
class Bad : CliktCommand() {
    val name by option().required()
    init { echo(name) }    // crashes: parsing hasn't happened
}

// WRONG — println instead of echo
override fun run() { println("hello") }   // bypasses Terminal, breaks tests

// WRONG — try/catch around CliktError
try { cmd.parse(args) } catch (e: Exception) { ... }
// RIGHT
runCatching { cmd.parse(args) }.onFailure {
    if (it is CliktError) echo(it.message, err = true) else throw it
}

// WRONG — .default and .required together
option().int().default(0).required()   // compile error

// WRONG — .multiple() with .default() instead of .multiple(default = ...)
option().multiple().default(listOf("x"))   // compile error
// RIGHT
option().multiple(default = listOf("x"))

// WRONG — multiple variadic arguments
val a by argument().multiple()
val b by argument().multiple()   // only one variadic allowed

// WRONG — calling main(args) in a test
Greet().main("--count 2 Ada".split(" ").toTypedArray())   // kills the test JVM
// RIGHT
Greet().test("--count 2 Ada")

// WRONG — subcommand parent without NoOpCliktCommand
class Tool : CliktCommand() { override fun run() = Unit }
// RIGHT
class Tool : NoOpCliktCommand()
```

## Pitfalls

| Symptom                                           | Cause / fix                                                                 |
|---------------------------------------------------|-----------------------------------------------------------------------------|
| `IllegalStateException: Cannot read ...`          | Accessed a parameter outside `run()`. Move it inside or into a computed property gated on `currentContext`. |
| Tests print `Process finished with exit code 1`   | Used `main(args)` instead of `.test(...)`.                                  |
| Envvar not honored                                | Forgot `envvar = "..."` or missing `autoEnvvarPrefix` in context.           |
| Subcommand never runs                             | Parent didn't call `subcommands(...)`; or `invokeWithoutSubcommand` expected. |
| Help omits an option                              | `hidden = true` left on, or defined on wrong command in a subcommand tree.  |
| `UsageError` shown when you wanted plain error    | Throw `CliktError` for non-usage problems.                                  |
| Parent's object is `null` in child                | Parent forgot `findOrSetObject { ... }` in its `run()`.                     |
| Args starting with `-` parsed as options          | Either add `treatUnknownOptionsAsArgs = true` or pass `--` on the command line. |
| `main` import not found in 5.x                    | Import `com.github.ajalt.clikt.core.main` — it moved from a member to a free extension function. |

## Reference points

- https://ajalt.github.io/clikt/ — top-level docs index
- https://ajalt.github.io/clikt/quickstart/
- https://ajalt.github.io/clikt/parameters/
- https://ajalt.github.io/clikt/options/
- https://ajalt.github.io/clikt/arguments/
- https://ajalt.github.io/clikt/commands/
- https://ajalt.github.io/clikt/advanced/
- https://ajalt.github.io/clikt/testing/
- https://ajalt.github.io/clikt/exceptions/
- https://ajalt.github.io/clikt/api/clikt/com.github.ajalt.clikt.core/
- Source: https://github.com/ajalt/clikt
