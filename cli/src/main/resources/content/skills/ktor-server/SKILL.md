---
name: ktor-server
description: >-
  Use when building an HTTP or WebSocket server with Ktor — embeddedServer
  vs EngineMain, engines (Netty/CIO/Jetty/Tomcat), application.conf / YAML
  configuration, modules, routing (get/post/put/patch/delete/head/options,
  route/path/parameter matching, Regex routes, Resources typesafe routing),
  ApplicationCall and pipeline phases (Setup, Monitoring, Plugins, Call,
  Fallback), server plugins (ContentNegotiation, StatusPages, Compression,
  CallLogging, CORS, Authentication — basic/form/JWT/OAuth/session/bearer,
  Sessions, AutoHeadResponse, HSTS, ConditionalHeaders, CachingHeaders,
  PartialContent, DefaultHeaders, RequestValidation, DoubleReceive, Micrometer,
  RateLimit, Resources, SSE, WebSockets), request handling (call.receive,
  call.parameters, call.request), response (call.respond, respondText,
  respondFile, respondBytes, respondRedirect), static content, sessions
  storage backends, JWT verification, OAuth flows, and testing via
  testApplication / ApplicationTestBuilder.
---

## When to use this skill

- Standing up any HTTP API or web server in Kotlin (JVM — including
  GraalVM native-image via Netty).
- Serving WebSocket, SSE, or streaming endpoints.
- Replacing Spring Boot / Micronaut / Vert.x for a coroutine-first stack.

## When NOT to use this skill

- Making HTTP calls out — that's `/ktor-client`.
- Writing a reusable plugin — that's `/ktor-plugin`.
- Pure gRPC / WebFlux reactive streams — Ktor is HTTP/WebSocket first.

## Installation

```kotlin
dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")          // engine
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-config-yaml:$ktorVersion")    // if using YAML
}
```

## Engines

| Engine    | Notes                                             |
|-----------|---------------------------------------------------|
| `Netty`   | Default pick. Async, HTTP/2, WebSocket, most-used.|
| `CIO`     | Pure-Kotlin coroutines engine. Multiplatform.     |
| `Jetty`   | JEE-friendly; HTTP/2 via ALPN.                    |
| `Tomcat`  | Drop into existing Tomcat deployments.            |

Use `Netty` unless you have a specific reason to pick another.

## Two ways to start

### `embeddedServer` — in code

```kotlin
fun main() {
    embeddedServer(Netty, port = 8080) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) { json() }
    routing {
        get("/") { call.respondText("hello") }
    }
}
```

### `EngineMain` — config-driven

`application.conf` (HOCON) or `application.yaml`:

```hocon
ktor {
    deployment {
        port = 8080
        port = ${?PORT}
    }
    application {
        modules = [ com.example.ApplicationKt.module ]
    }
}
```

```kotlin
fun Application.module() {
    // ...
}

// Main-Class: io.ktor.server.netty.EngineMain
```

Config-driven is the production default — env-var overrides, no
recompile to change the port.

## Modules

A module is `fun Application.xxx()`. Split concerns across modules —
`configureSecurity()`, `configureRouting()`, `configureMonitoring()`.
Register multiples in `modules = [ a, b, c ]`.

## Routing

```kotlin
routing {
    get("/") { call.respondText("root") }

    route("/api/v1") {
        get("/health") { call.respond(mapOf("ok" to true)) }

        route("/users") {
            get { /* list */ }
            post { /* create */ }
            get("{id}") {
                val id = call.parameters["id"]!!.toLong()
                call.respond(findUser(id))
            }
            put("{id}") { /* update */ }
            delete("{id}") { /* delete */ }
        }
    }
}
```

Path parameters: `{name}` (required), `{name?}` (optional),
`{...}` (tailcard — matches rest of path as `parameters.getAll("")`).

Regex routes:

```kotlin
get(Regex("""/files/(?<ext>\w+)""")) {
    val ext = call.parameters["ext"]
}
```

## `ApplicationCall` — the request context

```kotlin
get("/info") {
    val host = call.request.host()
    val ua = call.request.headers[HttpHeaders.UserAgent]
    val page = call.parameters["page"]?.toIntOrNull() ?: 1
    val body = call.receive<MyDto>()   // needs ContentNegotiation
    call.respond(HttpStatusCode.OK, MyResponse(...))
}
```

`call.receiveText()`, `call.receiveStream()`, `call.receiveChannel()`
for raw body access.

## Responding

```kotlin
call.respondText("hello")                                        // text/plain
call.respondText("hello", ContentType.Text.Html)                 // custom type
call.respond(HttpStatusCode.Created, dto)                        // JSON via ContentNegotiation
call.respondBytes(bytes, ContentType.Application.OctetStream)
call.respondFile(File("/tmp/big.bin"))
call.respondRedirect("/login", permanent = false)
call.respondSource(myByteReadChannel, contentType, status)

call.respondOutputStream(ContentType.Application.Pdf) {
    PdfGenerator(this).generate()
}
```

`call.response.status(HttpStatusCode.Created)` sets status without
body; pair with `call.respond(...)` or `call.respondNullable(...)`.

## Content negotiation

```kotlin
install(ContentNegotiation) {
    json(Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    })
    xml()
    cbor()
    protobuf()
}
```

`call.receive<T>()` and `call.respond(dto)` then round-trip via
`kotlinx.serialization`.

## Error handling — `StatusPages`

```kotlin
install(StatusPages) {
    exception<Throwable> { call, cause ->
        log.error("unhandled", cause)
        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "internal"))
    }
    exception<BadRequestException> { call, cause ->
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to cause.message))
    }
    status(HttpStatusCode.NotFound) { call, status ->
        call.respondText("no such route", status = status)
    }
}
```

Always install `StatusPages` — uncaught exceptions otherwise return a
bare 500 with an empty body.

## Pipelines & phases

Each call flows through an `ApplicationCallPipeline` with phases:
`Setup → Monitoring → Plugins → Call → Fallback`. Plugins hook phases
with `on(CallSetup) { }`, `onCallReceive { }`, `onCallRespond { }`
(see `/ktor-plugin`).

Custom interceptor:

```kotlin
intercept(ApplicationCallPipeline.Plugins) {
    call.attributes.put(StartTimeKey, System.nanoTime())
}
```

## Authentication

```kotlin
install(Authentication) {
    basic("admin") {
        realm = "Ops"
        validate { creds ->
            if (creds.name == "admin" && creds.password == secret) UserIdPrincipal(creds.name) else null
        }
    }
    jwt("api-user") {
        realm = "api.example.com"
        verifier(JWT.require(Algorithm.HMAC256(jwtSecret)).withIssuer(issuer).build())
        validate { credential ->
            if (credential.payload.getClaim("uid").asString() != null) JWTPrincipal(credential.payload) else null
        }
    }
    oauth("google") {
        urlProvider = { "https://example.com/callback" }
        providerLookup = { googleOAuthSettings }
        client = HttpClient(CIO)
    }
    session<UserSession>("session") {
        validate { if (it.userId != null) it else null }
        challenge { call.respondRedirect("/login") }
    }
}

routing {
    authenticate("api-user") {
        get("/me") {
            val principal = call.principal<JWTPrincipal>()!!
            call.respond(principal.payload.getClaim("uid").asString())
        }
    }
}
```

Providers: `basic`, `digest`, `form`, `jwt`, `oauth`, `bearer`,
`session`, `ldap` (via integration module).

## Sessions

```kotlin
install(Sessions) {
    cookie<UserSession>("SESSION", storage = SessionStorageMemory()) {
        cookie.path = "/"
        cookie.maxAgeInSeconds = 3600
        cookie.secure = true
        cookie.httpOnly = true
        cookie.extensions["SameSite"] = "lax"
    }
    header<CsrfToken>("X-CSRF-Token") { }
}

get("/set") {
    call.sessions.set(UserSession(userId = 42))
    call.respondText("ok")
}

get("/me") {
    val s = call.sessions.get<UserSession>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
    call.respondText("user ${s.userId}")
}
```

Storage: `SessionStorageMemory()`, `directorySessionStorage(File("sessions"))`,
`RedisSessionStorage` (third-party), or your own `SessionStorage`.

Transform for signing/encryption:

```kotlin
cookie<UserSession>("SESSION") {
    transform(SessionTransportTransformerMessageAuthentication(signKey))
    transform(SessionTransportTransformerEncrypt(encryptKey, signKey))
}
```

## WebSockets

```kotlin
install(WebSockets) {
    pingPeriod = 15.seconds
    timeout = 60.seconds
}

routing {
    webSocket("/chat") {
        send("welcome")
        for (frame in incoming) {
            when (frame) {
                is Frame.Text -> send("echo: ${frame.readText()}")
                is Frame.Close -> close()
                else -> { /* ignore */ }
            }
        }
    }
}
```

## SSE

```kotlin
install(SSE)

routing {
    sse("/events") {
        repeat(10) { i ->
            send(ServerSentEvent(data = "tick $i", event = "tick", id = i.toString()))
            delay(1_000)
        }
    }
}
```

## Static content

```kotlin
routing {
    staticFiles("/static", File("/var/www/public")) {
        default("index.html")
        preCompressed(CompressedFileType.GZIP, CompressedFileType.BROTLI)
    }
    staticResources("/assets", "public") {
        // from resources/public/
    }
}
```

## Typesafe routing — `Resources`

```kotlin
install(Resources)

@Serializable @Resource("/articles")
class Articles(val sort: String = "new", val page: Int = 1) {
    @Serializable @Resource("{id}")
    class Item(val parent: Articles = Articles(), val id: Long)
}

routing {
    get<Articles> { a -> call.respond(listArticles(a.sort, a.page)) }
    get<Articles.Item> { item -> call.respond(findArticle(item.id)) }
    // URL generation via application.href(Articles.Item(id = 42))
}
```

Share the `@Resource` classes with `/ktor-client` for end-to-end
type safety.

## CORS

```kotlin
install(CORS) {
    allowHost("app.example.com", schemes = listOf("https"))
    allowMethod(HttpMethod.Put)
    allowHeader(HttpHeaders.ContentType)
    allowCredentials = true
    maxAgeInSeconds = 3600
}
```

For local dev, `anyHost()` — never in production.

## Other common plugins

```kotlin
install(CallLogging) {
    level = Level.INFO
    filter { call -> call.request.path().startsWith("/api") }
    format { call -> "${call.response.status()} ${call.request.httpMethod.value} ${call.request.path()}" }
}

install(Compression) { gzip(); deflate() }
install(DefaultHeaders) { header(HttpHeaders.Server, "MyApp 1.0") }
install(AutoHeadResponse)           // HEAD → same as GET
install(ConditionalHeaders)          // ETag / Last-Modified
install(CachingHeaders)
install(PartialContent)
install(HSTS) { maxAgeInSeconds = 31_536_000 }
install(RateLimit) {
    register {
        rateLimiter(limit = 100, refillPeriod = 1.minutes)
    }
}
install(RequestValidation) {
    validate<CreateUser> {
        if (it.email.isBlank()) ValidationResult.Invalid("email required")
        else ValidationResult.Valid
    }
}
install(Micrometer) { registry = prometheusRegistry }
```

## Testing

`ktor-server-test-host` + kotest/JUnit:

```kotlin
@Test
fun `health returns ok`() = testApplication {
    application { module() }          // same module used in production
    val response = client.get("/health")
    assertEquals(HttpStatusCode.OK, response.status)
    assertEquals("""{"ok":true}""", response.bodyAsText())
}
```

`testApplication { }` gives:

- `application { ... }` — configure the server under test.
- `client` — a preconfigured `HttpClient` that targets this app.
- `environment { ... }` — override config values.
- `externalServices { hosts("https://other") { ... } }` — mock out
  outbound calls without a real network.

Install client plugins inside the test:

```kotlin
val client = createClient {
    install(ContentNegotiation) { json() }
}
```

## Graceful shutdown

```kotlin
val server = embeddedServer(Netty, port = 8080) { module() }.start(wait = false)
Runtime.getRuntime().addShutdownHook(Thread {
    server.stop(gracePeriodMillis = 5_000, timeoutMillis = 10_000)
})
```

`EngineMain` installs a sensible shutdown hook by default.

## Anti-patterns

```kotlin
// WRONG — blocking inside a handler
get("/slow") { Thread.sleep(5_000); call.respondText("hi") }
// RIGHT
get("/slow") { delay(5_000); call.respondText("hi") }

// WRONG — reading body twice without DoubleReceive
post { val a = call.receive<X>(); val b = call.receive<X>() }   // throws
// RIGHT — install(DoubleReceive) if you truly need this

// WRONG — manual JSON
call.respondText("""{"ok":true}""", ContentType.Application.Json)
// RIGHT — install ContentNegotiation + kotlinx.serialization
call.respond(mapOf("ok" to true))

// WRONG — mutating the call after responding
call.respond(...); call.response.headers.append("X-After", "nope")   // no-op at best

// WRONG — catching Throwable and swallowing
try { ... } catch (t: Throwable) { log.error(t.message) }
// RIGHT — install StatusPages, let it format the 500 response

// WRONG — shared mutable state without synchronization
val cache = mutableMapOf<String, Thing>()
get("...") { cache[k] = ... }   // races
// RIGHT — ConcurrentHashMap, or coroutine-scoped Mutex, or an Actor
```

## Pitfalls

| Symptom                                           | Cause / fix                                                                 |
|---------------------------------------------------|-----------------------------------------------------------------------------|
| 415 Unsupported Media Type on JSON POST           | No `ContentNegotiation.json()` installed, or wrong `Content-Type` header.    |
| 401 loop with session auth                        | `challenge { }` redirects back to a protected route. Point at public login. |
| Sessions don't persist across restarts            | Using `SessionStorageMemory()` — swap for `directorySessionStorage` or Redis. |
| WebSocket closes with 1006                        | No `install(WebSockets)`, or reverse proxy dropping Upgrade headers.         |
| `NoSuchElementException` on `call.parameters[x]!!`| Required path param absent; use `?: throw BadRequestException("...")` or `RequestValidation`. |
| `StatusPages` never fires                         | Installed on a sub-route; install on the root `Application`.                |
| 404 on static files                               | File path mismatch; `staticFiles` uses filesystem, `staticResources` uses classpath — don't swap. |
| CORS preflight fails                              | `OPTIONS` needs explicit `allowMethod(HttpMethod.Options)` plus the real method. |
| JWT `validate` never called                       | `verifier(...)` threw on a malformed token earlier in the chain; check `StatusPages.exception<JWTVerificationException>`. |
| Gradle `Main class not found`                     | `application { mainClass.set("io.ktor.server.netty.EngineMain") }` for `EngineMain`; point at your own `MainKt` for `embeddedServer`. |

## Reference points

- https://ktor.io/docs/server.html — server overview
- https://ktor.io/docs/server-create-a-new-project.html
- https://ktor.io/docs/server-engines.html
- https://ktor.io/docs/server-modules.html
- https://ktor.io/docs/server-routing.html
- https://ktor.io/docs/server-plugins.html
- https://ktor.io/docs/server-auth.html
- https://ktor.io/docs/server-sessions.html
- https://ktor.io/docs/server-websockets.html
- https://ktor.io/docs/server-testing.html
- https://api.ktor.io/
- Source: https://github.com/ktorio/ktor
