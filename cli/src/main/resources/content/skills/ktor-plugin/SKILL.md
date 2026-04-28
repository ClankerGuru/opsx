---
name: ktor-plugin
description: >-
  Use when building a reusable Ktor plugin — server-side with
  `createApplicationPlugin` / `createRouteScopedPlugin`, client-side with
  `createClientPlugin`, configuration classes, phase hooks (onCall,
  onCallReceive, onCallRespond, on(MonitoringEvent), on(CallFailed)),
  request and response transforms, pipeline interceptors (legacy
  `BaseApplicationPlugin` / `BaseRouteScopedPlugin`), plugin state via
  `AttributeKey`, plugin composition (install inside a plugin),
  publishing to Maven, and unit testing a plugin via `testApplication`
  or `MockEngine`.
---

## When to use this skill

- Factoring out cross-cutting concerns (tracing, auth, request ID,
  metrics, vendor SDK integration) into a reusable plugin.
- Publishing a library that other Ktor apps install with
  `install(MyPlugin) { ... }`.
- Adapting legacy `install(Feature) { }` plugins written against the
  older pipeline API.

## When NOT to use this skill

- One-off route middleware that lives in one codebase — just use
  `intercept(...)` or a `route { }` wrapper.
- Extending Ktor core internals — those live in Ktor's own modules.
- Writing a client library that doesn't intercept the pipeline —
  just build on `HttpClient` directly.

## Server plugin — modern API

### `createApplicationPlugin` — app-scoped

```kotlin
class RequestIdConfig {
    var headerName: String = "X-Request-Id"
    var generator: () -> String = { UUID.randomUUID().toString() }
}

val RequestIdPlugin = createApplicationPlugin(
    name = "RequestId",
    createConfiguration = ::RequestIdConfig
) {
    val headerName = pluginConfig.headerName
    val generator = pluginConfig.generator

    onCall { call ->
        val id = call.request.headers[headerName] ?: generator()
        call.attributes.put(RequestIdKey, id)
        call.response.headers.append(headerName, id)
    }
}

val RequestIdKey = AttributeKey<String>("RequestId")
```

Install:

```kotlin
install(RequestIdPlugin) {
    headerName = "X-Trace-Id"
}
```

Read in a handler: `call.attributes[RequestIdKey]`.

### `createRouteScopedPlugin` — per-route

Same API, different scope. Installed on a single `route { }` block or
the root `routing { }`. Useful when the same plugin applies to some
routes but not all (a finer-grained version of `install` on the app).

```kotlin
val AdminOnly = createRouteScopedPlugin("AdminOnly") {
    onCall { call ->
        if (call.principal<UserIdPrincipal>()?.name != "admin") {
            call.respond(HttpStatusCode.Forbidden)
            return@onCall
        }
    }
}

routing {
    route("/admin") {
        install(AdminOnly)
        get("/stats") { /* ... */ }
    }
}
```

### Hook points

Inside the plugin-creation lambda:

| Hook                        | Fires                                                |
|-----------------------------|------------------------------------------------------|
| `onCall { call -> }`        | Once per incoming request (start of the Plugins phase). |
| `onCallReceive { call -> }` | Body deserialization. Can transform `subject`.        |
| `onCallRespond { call -> }` | Response send. Can inspect `subject` or replace it.   |
| `on(CallFailed)`            | An exception escaped the handler.                     |
| `on(ResponseBodyReadyForSend) { call, content -> }` | Right before bytes go to the wire.  |
| `on(ResponseSent) { call -> }` | After the response is written.                     |
| `on(MonitoringEvent(Event)) { ... }` | Application lifecycle: start, stopping, stopped. |
| `onCallReceive { transformBody { data -> ... } }` | Mutate incoming body bytes/types.     |
| `onCallRespond { transformBody { data -> ... } }` | Mutate outgoing body before send.     |

```kotlin
val TimingPlugin = createApplicationPlugin("Timing") {
    onCall { call ->
        val started = System.nanoTime()
        call.attributes.put(StartKey, started)
    }
    on(ResponseSent) { call ->
        val started = call.attributes.getOrNull(StartKey) ?: return@on
        log.info("${call.request.path()} ${(System.nanoTime() - started) / 1_000_000} ms")
    }
}
```

### Config with `init`

`createConfiguration` can accept a lambda that runs after user config:

```kotlin
class AuthConfig {
    var realm: String = "default"
    var validators: MutableList<suspend (Credentials) -> Principal?> = mutableListOf()

    fun validate(block: suspend (Credentials) -> Principal?) {
        validators += block
    }
}

val AuthPlugin = createApplicationPlugin("Auth", ::AuthConfig) {
    val validators = pluginConfig.validators.toList()
    onCall { call -> /* use validators */ }
}
```

Freeze the config inside the plugin body — later mutations by user
code won't leak into running handlers.

### Interacting with other plugins — `application`

```kotlin
val MyPlugin = createApplicationPlugin("MyPlugin") {
    application.monitor.subscribe(ApplicationStarted) { log.info("started") }
    application.attributes.put(MyKey, MyServiceImpl())
    application.environment.config.propertyOrNull("my.key")
}
```

### Installing other plugins inside yours

```kotlin
val MyBundle = createApplicationPlugin("MyBundle") {
    // Plugin composition
    application.install(StatusPages) { /* ... */ }
    application.install(CallLogging)
}
```

Prefer this over duplicating installations. The user only writes
`install(MyBundle)`.

## Client plugin — modern API

```kotlin
class RetryClientPluginConfig {
    var retries: Int = 3
    var delay: Long = 500
}

val RetryClientPlugin = createClientPlugin("Retry", ::RetryClientPluginConfig) {
    val retries = pluginConfig.retries
    val delay = pluginConfig.delay

    onRequest { request, _ ->
        request.attributes.put(RetryAttemptKey, 0)
    }

    on(Send) { request ->
        var lastResponse: HttpClientCall? = null
        repeat(retries + 1) { attempt ->
            lastResponse = proceed(request)
            if (lastResponse!!.response.status.value < 500) return@on lastResponse!!
            delay(delay * (1L shl attempt))
        }
        lastResponse!!
    }
}

val RetryAttemptKey = AttributeKey<Int>("RetryAttempt")

val client = HttpClient(CIO) {
    install(RetryClientPlugin) { retries = 5; delay = 1_000 }
}
```

Client hook points:

| Hook                    | Fires                                           |
|-------------------------|-------------------------------------------------|
| `onRequest { req, body -> }` | Before the request is serialized.          |
| `onResponse { res -> }` | After the response headers are received.        |
| `on(Send) { req -> ... }` | Wraps the whole send; call `proceed(req)` to delegate. Used for retry/circuit-breaker. |
| `transformRequestBody { ... }` | Custom outgoing body type conversion.    |
| `transformResponseBody { ... }` | Custom incoming body type conversion.   |

`proceed(request)` is the "call-next" primitive — without it, your
plugin swallows the request.

## Legacy server plugin API — pipeline-centric

Before `createApplicationPlugin`, plugins implemented
`BaseApplicationPlugin<Pipeline, Config, Plugin>`:

```kotlin
class LegacyPlugin(val config: Config) {
    class Config {
        var flag: Boolean = false
    }

    companion object : BaseApplicationPlugin<ApplicationCallPipeline, Config, LegacyPlugin> {
        override val key = AttributeKey<LegacyPlugin>("Legacy")

        override fun install(pipeline: ApplicationCallPipeline, configure: Config.() -> Unit): LegacyPlugin {
            val config = Config().apply(configure)
            val plugin = LegacyPlugin(config)
            pipeline.intercept(ApplicationCallPipeline.Plugins) {
                if (plugin.config.flag) { /* ... */ }
            }
            return plugin
        }
    }
}
```

Route-scoped: `BaseRouteScopedPlugin<Config, Plugin>`.

Prefer `createApplicationPlugin` for new work — the legacy API is
kept for plugins that need precise pipeline-phase control or deep
integration with Ktor core internals.

## Pipeline phases — when you need manual control

```kotlin
val CustomPhase = PipelinePhase("Custom")

pipeline.insertPhaseBefore(ApplicationCallPipeline.Plugins, CustomPhase)
pipeline.intercept(CustomPhase) {
    /* ... */
    proceed()   // continue pipeline; omit to short-circuit
    /* ... after downstream ... */
}
```

Server phases (default order): `Setup → Monitoring → Plugins → Call
→ Fallback`. Plugins hook `Plugins` unless they specifically need
earlier/later ordering.

Client phases: `Before → State → Transform → Send → Receive → Parse
→ After`.

## State with `AttributeKey`

```kotlin
val TraceIdKey = AttributeKey<String>("TraceId")

call.attributes.put(TraceIdKey, traceId)
val traceId = call.attributes[TraceIdKey]                // throws if absent
val maybe   = call.attributes.getOrNull(TraceIdKey)       // null if absent
val removed = call.attributes.take(TraceIdKey)
val exists  = call.attributes.contains(TraceIdKey)
```

Declare the key at package level; it doubles as the contract other
code uses to fish values out.

## Multiple instances & named installs

Some plugins support multiple installations (Auth providers,
Sessions). Your plugin does by default unless you fail on
double-install. Ktor's `install` is idempotent by `key`; to allow
repeated installations with different configs, store state per-call
rather than per-plugin and read config from `pluginConfig` inside the
hook.

## Publishing

Plugin module Gradle:

```kotlin
plugins {
    `maven-publish`
    signing
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.example"
version = "1.0.0"

dependencies {
    api("io.ktor:ktor-server-core:$ktorVersion")  // use `api` so consumers don't need to add
    implementation("io.ktor:ktor-server-config-yaml:$ktorVersion")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("My Ktor Plugin")
                description.set("...")
                licenses { license { name.set("Apache-2.0"); url.set("...") } }
            }
        }
    }
}
```

Consumers: `implementation("com.example:my-ktor-plugin:1.0.0")`.

Version-match Ktor: pin `ktor-server-core` as `api` to propagate
the compatible-version range to consumers; bump your plugin when
Ktor's minor version changes.

## Testing a server plugin

```kotlin
class RequestIdPluginTest : StringSpec({
    "echoes incoming request id" {
        testApplication {
            application {
                install(RequestIdPlugin) { headerName = "X-Request-Id" }
                routing {
                    get("/") { call.respond(call.attributes[RequestIdKey]) }
                }
            }
            val response = client.get("/") { header("X-Request-Id", "abc-123") }
            response.bodyAsText() shouldBe "abc-123"
            response.headers["X-Request-Id"] shouldBe "abc-123"
        }
    }

    "generates one if missing" {
        testApplication {
            application {
                install(RequestIdPlugin) { generator = { "fixed-id" } }
                routing { get("/") { call.respond(call.attributes[RequestIdKey]) } }
            }
            client.get("/").bodyAsText() shouldBe "fixed-id"
        }
    }
})
```

## Testing a client plugin

```kotlin
class RetryClientPluginTest : StringSpec({
    "retries 5xx" {
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls < 3) respond("", HttpStatusCode.InternalServerError) else respond("ok")
        }
        val client = HttpClient(engine) {
            install(RetryClientPlugin) { retries = 5; delay = 0 }
        }
        client.get("http://x/").bodyAsText() shouldBe "ok"
        calls shouldBe 3
    }
})
```

## Convention: plugin shape checklist

- Name uses PascalCase and matches the `val`: `val MyPlugin = createApplicationPlugin("MyPlugin") { ... }`.
- Configuration class lives at top-level and is public. Fields have
  sensible defaults.
- `AttributeKey`s are package-level `val`s named after the value
  they hold (`TraceIdKey`), not after the plugin (`MyPluginKey`).
- Plugin body captures config into local `val`s **once** before
  installing hooks — don't read `pluginConfig` in a hot path.
- Plugin is idempotent: double-installing is a no-op or a clear
  error, not silent duplication of interceptors.
- Document expected order with other plugins (e.g. "install *after*
  `ContentNegotiation`") in the plugin's KDoc.

## Anti-patterns

```kotlin
// WRONG — side effects in config class
class Config {
    var counter = 0
    init { counter++ }   // runs before user config, not what you want
}

// WRONG — reading pluginConfig inside the hook lambda
val P = createApplicationPlugin("P", ::Config) {
    onCall { call ->
        if (pluginConfig.enabled) { ... }    // crash: pluginConfig is scoped to the outer body
    }
}
// RIGHT
val P = createApplicationPlugin("P", ::Config) {
    val enabled = pluginConfig.enabled
    onCall { call -> if (enabled) { ... } }
}

// WRONG — catching and swallowing in a plugin
onCall { call -> try { ... } catch (e: Throwable) { log.error(e) } }
// RIGHT — let it bubble, install StatusPages to format the response

// WRONG — blocking in a hook
on(Send) { request -> Thread.sleep(1000); proceed(request) }
// RIGHT
on(Send) { request -> delay(1000); proceed(request) }

// WRONG — not calling proceed in a Send interceptor
on(Send) { request -> myOwnResponse(request) }   // request never actually sent

// WRONG — reusing AttributeKey across unrelated plugins
val DataKey = AttributeKey<Any>("Data")          // name clash waiting to happen
// RIGHT — keys are strongly typed and named after the value
val RequestIdKey = AttributeKey<String>("RequestId")
```

## Pitfalls

| Symptom                                          | Cause / fix                                                                 |
|--------------------------------------------------|-----------------------------------------------------------------------------|
| Plugin not triggered                             | Installed on sub-route but expected global; or installed *after* the route it was supposed to wrap. |
| Config values are wrong                          | Mutated `pluginConfig` after install; snapshot into locals in the plugin body. |
| `onCallRespond` fires twice                      | Two responses sent (e.g. error handler after an explicit `respond`). Gate on `call.response.status()`. |
| Client `on(Send)` never sends                    | Forgot to call `proceed(request)`. Always return its result unless you intentionally short-circuit. |
| `AttributeKey` not found                         | Call went through a different pipeline; or plugin was route-scoped and you're reading in an app-level handler. |
| ClassNotFoundException for plugin types          | Consumer missing `ktor-server-*` dependencies. Use `api` for transitively-required artifacts. |
| Hook runs in wrong order                         | Pipeline phase default is `Plugins`; insert explicitly with `pipeline.insertPhaseBefore/After` for deterministic ordering. |
| `StatusPages` masks plugin errors                | Install your plugin *after* `StatusPages`, or rethrow inside to let StatusPages format. |
| Client plugin retries infinitely                 | Missing attempt counter; use `request.attributes` or a local counter in the `on(Send)` body. |
| Plugin breaks WebSocket upgrades                 | Hook intercepted `ResponseBodyReadyForSend` and mutated a non-WebSocket body type; guard on `call.response.status()` or route. |

## Reference points

- https://ktor.io/docs/server-custom-plugins.html — modern server plugin API
- https://ktor.io/docs/server-custom-plugins-base-api.html — legacy API
- https://ktor.io/docs/client-custom-plugins.html — client plugin API
- https://ktor.io/docs/server-plugins.html — first-party plugin list
- https://ktor.io/docs/client-plugins.html
- https://api.ktor.io/ktor-server/ktor-server-core/
- https://api.ktor.io/ktor-client/ktor-client-core/
- Source: https://github.com/ktorio/ktor
