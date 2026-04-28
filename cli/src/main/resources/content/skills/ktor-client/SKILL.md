---
name: ktor-client
description: >-
  Use when building an HTTP client with Ktor — HttpClient creation, engines
  (CIO/OkHttp/Apache5/Java/Darwin/Js/WinHttp/Curl), client plugins
  (ContentNegotiation, Logging, Auth, HttpTimeout, DefaultRequest,
  HttpRequestRetry, Resources, WebSockets, SSE, HttpCache, HttpCookies,
  UserAgent), making requests (get/post/put/patch/delete/head/options),
  request builders (url, headers, parameter, bearerAuth, basicAuth, setBody,
  accept, contentType), response handling (body, bodyAsText, bodyAsChannel,
  status, headers), streaming and multipart/form-data submission, typed
  requests with kotlinx.serialization, bearer token refresh, websocket and
  SSE sessions, and MockEngine-based testing.
---

## When to use this skill

- Calling any HTTP or WebSocket API from Kotlin (JVM, Android, Native,
  iOS, JS).
- Needing a single multiplatform client that speaks JSON / XML / CBOR /
  Protobuf via kotlinx.serialization.
- Replacing a per-platform client (OkHttp on Android + URLSession on
  iOS) with one shared codebase.

## When NOT to use this skill

- Server-side HTTP handling — that's `/ktor-server`.
- Creating a reusable plugin (client *or* server) — that's
  `/ktor-plugin`.
- Pure gRPC or proprietary binary protocols — Ktor client speaks HTTP.

## Installation

Gradle (`build.gradle.kts`):

```kotlin
dependencies {
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")          // engine
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-client-auth:$ktorVersion")
}
```

Pin `$ktorVersion` via a version catalog. Every client artifact ships
from the same release — keep versions aligned.

## Engines

| Engine     | Platform     | Notes                                           |
|------------|--------------|-------------------------------------------------|
| `CIO`      | JVM, Native  | Pure Kotlin, coroutine-native, default pick.    |
| `OkHttp`   | JVM, Android | Battle-tested; HTTP/2; best on Android.         |
| `Apache5`  | JVM          | Apache HttpClient 5; sync + async; HTTP/2.      |
| `Java`     | JVM (11+)    | Uses `java.net.http.HttpClient`.                |
| `Darwin`   | iOS, macOS   | `NSURLSession`.                                 |
| `Js`       | JS           | Browser `fetch` or Node `node-fetch`.           |
| `WinHttp`  | Windows Native | WinHTTP.                                      |
| `Curl`     | Linux/macOS Native | libcurl.                                  |

Pick per platform. Multiplatform projects use `expect/actual` factories
or let Ktor pick via `HttpClient()` without specifying an engine (not
recommended — always be explicit).

## Minimum viable client

```kotlin
val client = HttpClient(CIO)
val body: String = client.get("https://api.example.com/ping").bodyAsText()
client.close()
```

Clients are expensive — create one per application and reuse. Close on
shutdown (or `use { ... }` for a scoped client).

## Configuration block

```kotlin
val client = HttpClient(CIO) {
    engine {
        requestTimeout = 30_000
        endpoint {
            connectTimeout = 10_000
            keepAliveTime = 5_000
            maxConnectionsCount = 1000
            pipelining = true
        }
    }
    expectSuccess = true  // throw on 3xx/4xx/5xx by default

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        })
    }

    install(Logging) {
        level = LogLevel.INFO
        logger = Logger.DEFAULT
    }

    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis  = 30_000
    }

    defaultRequest {
        url("https://api.example.com/")
        header(HttpHeaders.Accept, ContentType.Application.Json)
        header("X-Api-Version", "2024-01")
    }
}
```

`expectSuccess = true` turns non-2xx into `ClientRequestException` (4xx)
or `ServerResponseException` (5xx); leave `false` when you need to
inspect error bodies.

## Making requests

```kotlin
val r = client.get("https://api.example.com/users/42")
val text: String = r.bodyAsText()
val status: HttpStatusCode = r.status
val etag: String? = r.headers[HttpHeaders.ETag]
```

Request builder:

```kotlin
client.get("users") {
    url {
        parameters.append("page", "2")
        parameters.append("size", "50")
    }
    header("X-Trace", traceId)
    accept(ContentType.Application.Json)
}
```

POST with body:

```kotlin
@Serializable data class CreateUser(val name: String, val email: String)

val user: User = client.post("users") {
    contentType(ContentType.Application.Json)
    setBody(CreateUser(name = "Ada", email = "ada@example.com"))
}.body()
```

`body()` deserializes via `ContentNegotiation`. `bodyAsText()` returns
the raw string.

## All HTTP methods

```kotlin
client.get(url)
client.post(url) { setBody(x) }
client.put(url) { setBody(x) }
client.patch(url) { setBody(x) }
client.delete(url)
client.head(url)
client.options(url)
```

Generic form: `client.request(url) { method = HttpMethod.Report }`.

## Query parameters and URL builder

```kotlin
client.get {
    url {
        protocol = URLProtocol.HTTPS
        host = "api.example.com"
        path("v2", "users", id.toString())
        parameters.append("include", "profile")
    }
}
```

`parameter("k", "v")` inside the builder is shorthand for one
param.

## Headers & auth

```kotlin
client.get("secure") {
    bearerAuth("token123")
    basicAuth("user", "pass")
    header("X-Custom", "value")
}
```

Install `Auth` for persistent credentials with token refresh:

```kotlin
install(Auth) {
    bearer {
        loadTokens { BearerTokens(accessToken, refreshToken) }
        refreshTokens {
            val new = refresh(oldTokens!!.refreshToken)
            BearerTokens(new.access, new.refresh)
        }
        sendWithoutRequest { req -> req.url.host == "api.example.com" }
    }
}
```

`basic { credentials { BasicAuthCredentials(user, pass) } }`,
`digest { ... }`, and custom `provider { }` are the other shapes.

## Serialization

`ContentNegotiation` plus a content-type plugin:

```kotlin
install(ContentNegotiation) {
    json()                                    // JSON only
    xml()                                     // + XML
    cbor()                                    // + CBOR
    protobuf()                                // + Protobuf
    register(ContentType.Application.Json, MyCustomConverter())
}
```

Request-scoped body inference:

```kotlin
@Serializable data class Repo(val name: String, val stars: Int)
val repo: Repo = client.get("repos/ktorio/ktor").body()
val repos: List<Repo> = client.get("orgs/ktorio/repos").body()
```

## Streaming

```kotlin
client.prepareGet("large.bin").execute { response ->
    val channel: ByteReadChannel = response.bodyAsChannel()
    val buffer = ByteArray(8 * 1024)
    while (!channel.isClosedForRead) {
        val read = channel.readAvailable(buffer, 0, buffer.size)
        if (read > 0) sink.write(buffer, 0, read)
    }
}
```

`prepareGet`/`preparePost` keep the response open; use `.execute { }`
to scope channel lifetime.

## Multipart / form submission

```kotlin
// application/x-www-form-urlencoded
val r = client.submitForm(
    url = "login",
    formParameters = parameters {
        append("user", "ada")
        append("pass", "secret")
    }
)

// multipart/form-data
val r = client.submitFormWithBinaryData(
    url = "upload",
    formData = formData {
        append("description", "cat pic")
        append("file", catBytes, Headers.build {
            append(HttpHeaders.ContentType, "image/png")
            append(HttpHeaders.ContentDisposition, "filename=cat.png")
        })
    }
)
```

## Retries

```kotlin
install(HttpRequestRetry) {
    retryOnServerErrors(maxRetries = 3)
    retryOnException(maxRetries = 2, retryOnTimeout = true)
    exponentialDelay(base = 2.0, maxDelayMs = 60_000)
    modifyRequest { request -> request.headers.append("X-Retry", retryCount.toString()) }
}
```

`retryIf { response -> ... }` lets you custom-match.

## WebSockets

```kotlin
install(WebSockets) {
    pingInterval = 20.seconds
}

client.webSocket("wss://echo.example.com/ws") {
    send("hello")
    for (frame in incoming) {
        if (frame is Frame.Text) echo(frame.readText())
    }
}
```

`webSocketSession { }` gives you a handle to hold across scopes.

## Server-Sent Events

```kotlin
install(SSE)

client.sse("https://api.example.com/events") {
    while (true) {
        val event = incoming.receive()
        echo("${event.event}: ${event.data}")
    }
}
```

## Typesafe requests via Resources

```kotlin
install(Resources)

@Serializable @Resource("/users/{id}")
class UserById(val id: Long)

@Serializable @Resource("/users")
class Users(val page: Int = 1, val size: Int = 20)

val user: User = client.get(UserById(id = 42)).body()
val page: List<User> = client.get(Users(page = 2)).body()
```

Matches the server-side `/ktor-server` Resources; sharing the resource
class between client and server is the common pattern.

## Cookies and caching

```kotlin
install(HttpCookies) {
    storage = AcceptAllCookiesStorage()
}

install(HttpCache) {
    publicStorage(FileStorage(File(cacheDir)))
    privateStorage(HttpCacheStorage.Default)
}
```

`HttpCookies` honors `Set-Cookie`; `HttpCache` respects `Cache-Control`,
`ETag`, and conditional requests.

## Testing with `MockEngine`

```kotlin
val engine = MockEngine { request ->
    when (request.url.encodedPath) {
        "/users/42" -> respond(
            content = ByteReadChannel("""{"id":42,"name":"Ada"}"""),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
        else -> respondError(HttpStatusCode.NotFound)
    }
}

val client = HttpClient(engine) {
    install(ContentNegotiation) { json() }
}
```

Inspect `engine.requestHistory` after calls for assertions.

## Exception handling

| Exception                       | When                                          |
|---------------------------------|-----------------------------------------------|
| `ClientRequestException`        | 4xx response with `expectSuccess = true`.     |
| `ServerResponseException`       | 5xx response with `expectSuccess = true`.     |
| `RedirectResponseException`     | 3xx and redirects disabled.                   |
| `HttpRequestTimeoutException`   | `requestTimeoutMillis` exceeded.              |
| `ConnectTimeoutException`       | Could not connect within `connectTimeoutMillis`. |
| `SocketTimeoutException`        | No bytes within `socketTimeoutMillis`.        |
| `ResponseException` (parent)    | Generic catch-all for 3xx/4xx/5xx.            |

```kotlin
try {
    client.get("never-works").body<Thing>()
} catch (e: ClientRequestException) {
    // e.response.status, e.response.bodyAsText()
}
```

## Anti-patterns

```kotlin
// WRONG — create-and-throwaway clients
fun fetch() {
    val c = HttpClient(CIO)
    c.get(...)              // never closed; engine leaks
}

// WRONG — using bodyAsText() then body<T>()
val raw = response.bodyAsText()   // consumes the channel
val obj: T = response.body()      // fails: already read

// WRONG — blocking in a coroutine
runBlocking { client.get("...").bodyAsText() }   // OK in main
fun foo() = runBlocking { client.get("...") }    // NO — blocks thread
suspend fun foo() = client.get("...")            // YES

// WRONG — mutating response after await
val body: ByteArray = response.readBytes()       // use body<ByteArray>() or bodyAsChannel()

// WRONG — manual JSON string building
val json = """{"name":"$name"}"""                // escape bugs waiting to happen
setBody(User(name))                              // let ContentNegotiation do it
```

## Pitfalls

| Symptom                                           | Cause / fix                                                                 |
|---------------------------------------------------|-----------------------------------------------------------------------------|
| `IllegalStateException: No transformation found` | Missing `ContentNegotiation` or a `kotlinx.serialization` converter for the body type. |
| `ConnectException` on Android                     | Network permission missing or cleartext blocked (HTTP on API 28+).          |
| `ChannelClosedException`                          | Consumed a streaming response body twice.                                   |
| Empty `body()` on 204                             | Expected — no content. Use `status.value == 204` guard.                     |
| `ClientRequestException: 401 Unauthorized`        | `Auth` bearer didn't send token: either `sendWithoutRequest` is false or `loadTokens` returned null. |
| `HttpRequestTimeoutException` despite large limits | `HttpTimeout` not installed; per-request `timeout { ... }` block overrides. |
| WebSocket closes immediately                      | Server expected `Sec-WebSocket-Protocol` — set via `request { header(...) }`. |
| `SerializationException: missing field`           | `Json { ignoreUnknownKeys = true }` only ignores extras; use `@SerialName` + defaults for missing fields. |

## Reference points

- https://ktor.io/docs/client.html — client overview
- https://ktor.io/docs/client-engines.html
- https://ktor.io/docs/client-plugins.html
- https://ktor.io/docs/client-content-negotiation.html
- https://ktor.io/docs/client-auth.html
- https://ktor.io/docs/client-websockets.html
- https://ktor.io/docs/client-testing.html
- https://api.ktor.io/ — full API reference
- Source: https://github.com/ktorio/ktor
