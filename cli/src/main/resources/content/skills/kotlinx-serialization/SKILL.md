---
name: kotlinx-serialization
description: Reference guide for kotlinx.serialization covering Gradle setup, @Serializable, Json { } configuration, property annotations (SerialName/Transient/EncodeDefault/JsonNames), sealed-hierarchy polymorphism, SerializersModule, custom KSerializer, formats (JSON/ProtoBuf/CBOR/HOCON/Properties), JsonElement manipulation, and common gotchas. Activates when serializing or deserializing Kotlin data.
---

# kotlinx.serialization

> For related topics see:
> - `/serialization-patterns` — project-specific conventions
> - `/kotlin-lang` — sealed classes, data classes, value classes used in schemas
> - `/kotlin-functional-first` — decoding as a pure transform from bytes to models
> - `/kotlinx-coroutines` — streaming JSON via `Flow<ByteArray>` with Ktor

## When to use this skill

Any time Kotlin code moves data across a process boundary: HTTP requests and
responses, disk files, IPC, caches, message queues, config files.

`kotlinx.serialization` is the Kotlin-first, reflectionless, compile-time
generated, multiplatform serializer. It is the default choice in modern
Kotlin codebases.

**Prefer it over:**
- `java.io.Serializable` — legacy binary protocol, unsafe, slow, not
  portable. Never make it a transfer format.
- Jackson — still common on JVM, but pulls reflection and misses KMP.
  Use Jackson only when interop with Spring forces it.
- Gson — unmaintained; ignores Kotlin null-safety and default values.
- Moshi — a reasonable JVM alternative, but `kotlinx.serialization`
  wins on multiplatform and does not need kapt/KSP.

## Gradle setup

```kotlin
plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
```

The `kotlin("plugin.serialization")` Gradle plugin is **mandatory** — it
generates the synthetic `.serializer()` companion functions that
`@Serializable` classes rely on. The plugin version must match the Kotlin
compiler version.

For KMP, apply the plugin in the root `kotlin { }` block and add the
dependency inside `commonMain.dependencies`.

### Format artifacts

| Artifact | Format |
|---|---|
| `kotlinx-serialization-json` | JSON (default) |
| `kotlinx-serialization-protobuf` | Protocol Buffers (binary) |
| `kotlinx-serialization-cbor` | CBOR (binary JSON) |
| `kotlinx-serialization-hocon` | Typesafe Config (decode only) |
| `kotlinx-serialization-properties` | Java `.properties` |

Everything below uses JSON unless noted.

## Core pattern

```kotlin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Serializable
data class User(val name: String, val age: Int)

val text: String = Json.encodeToString(User("Ada", 36))
// {"name":"Ada","age":36}

val user: User = Json.decodeFromString<User>(text)
```

Only **primary-constructor parameters** are serialized. Body `val`s are
skipped unless you write a custom serializer.

Properties with initializers (defaults) are serialized too:

```kotlin
@Serializable
data class User(val name: String, val age: Int = 0)
```

## Json { } configuration

Configure one `Json` instance per profile — **do not reconfigure on every
call**. Json instances are thread-safe and cheap to share.

```kotlin
val json = Json {
    ignoreUnknownKeys = true     // tolerate unexpected fields from server
    encodeDefaults = false       // omit defaults to keep payload small
    explicitNulls = false        // skip null fields entirely
    prettyPrint = false          // true only for debug output
    prettyPrintIndent = "  "
    isLenient = false            // strict JSON in production
    coerceInputValues = true     // map invalid enum/null to default
    allowStructuredMapKeys = false
    allowSpecialFloatingPointValues = false
    classDiscriminator = "type"  // default; rename to avoid collisions
    useAlternativeNames = true   // enable @JsonNames fallback on decode
    decodeEnumsCaseInsensitive = false
    namingStrategy = null        // e.g. JsonNamingStrategy.SnakeCase
}
```

**Field guide:**

- `ignoreUnknownKeys = true` — **always** for HTTP clients. Servers
  evolve; you don't want to crash when they add a field.
- `encodeDefaults = false` — default; keeps payload small. Set per-field
  override with `@EncodeDefault`.
- `explicitNulls = false` — omit null fields on encode; missing keys
  decode to the default. Good for REST payloads.
- `isLenient = true` — accept relaxed JSON (unquoted keys, etc.). Turn
  on for legacy input only.
- `coerceInputValues = true` — invalid enum values or nulls for
  non-nullable fields with defaults coerce to the default instead of
  throwing.
- `namingStrategy = JsonNamingStrategy.SnakeCase` — global snake_case
  mapping; overridable per field with `@SerialName`.

### Pre-configured instances

```kotlin
val debug = Json { prettyPrint = true }
val lenient = Json { isLenient = true; ignoreUnknownKeys = true }
val strict = Json { /* defaults */ }
```

## Property annotations

```kotlin
import kotlinx.serialization.*
import kotlinx.serialization.json.JsonNames

@Serializable
data class Account(
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: Long,
    @JsonNames("email", "emailAddress") val email: String,
    @Transient val passwordHash: String = "",
    @EncodeDefault val role: String = "user",
    @Required val apiKey: String,
)
```

- `@SerialName("wire_name")` — remap the key on the wire. Keep Kotlin
  names idiomatic; remap for server contracts.
- `@JsonNames("a", "b")` — accept alternative keys on decode (enable
  with `useAlternativeNames = true`). Doesn't affect encoding.
- `@Transient` — skip entirely on both sides. Property must have a
  default.
- `@EncodeDefault(ALWAYS|NEVER)` — override `encodeDefaults` per field.
- `@Required` — disable the default fallback: decoder throws if the key
  is missing, even if the property has a Kotlin default.

## Enums

```kotlin
@Serializable
enum class Priority { LOW, MEDIUM, HIGH }

@Serializable
enum class Status {
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("done") DONE,
}
```

Use `@SerialName` on each constant to control the wire form. With
`coerceInputValues = true` and a default, invalid values coerce silently;
otherwise they throw.

## Polymorphism — sealed hierarchies (preferred)

Sealed hierarchies get polymorphism for free — the compiler knows the
subtype set at compile time. No module registration needed.

```kotlin
@Serializable
sealed interface Event {
    @Serializable
    @SerialName("login")
    data class Login(val user: String) : Event

    @Serializable
    @SerialName("logout")
    data object Logout : Event

    @Serializable
    @SerialName("error")
    data class Error(val code: Int, val message: String) : Event
}

val raw = Json.encodeToString<Event>(Event.Login("ada"))
// {"type":"login","user":"ada"}
```

**Rules:**

- Annotate the sealed parent **and** every subclass with `@Serializable`.
- Every subclass needs `@SerialName` or it uses its FQN — brittle for
  refactoring.
- The discriminator key is `"type"` by default; change via
  `Json { classDiscriminator = "kind" }`.
- Use `encodeToString<Event>(login)` (or a `KSerializer<Event>`) so the
  compiler picks the polymorphic serializer. Passing `login` with no
  type argument serializes the concrete type only.

### Content-based polymorphism

For APIs that don't have a discriminator field, inspect the payload:

```kotlin
object EventSerializer : JsonContentPolymorphicSerializer<Event>(Event::class) {
    override fun selectDeserializer(element: JsonElement) = when {
        "user" in element.jsonObject -> Event.Login.serializer()
        "code" in element.jsonObject -> Event.Error.serializer()
        else -> Event.Logout.serializer()
    }
}
```

### Polymorphism across modules (open hierarchies)

Open or abstract classes require explicit registration via
`SerializersModule`:

```kotlin
val module = SerializersModule {
    polymorphic(Message::class) {
        subclass(TextMessage::class)
        subclass(ImageMessage::class)
        defaultDeserializer { UnknownMessageSerializer }
    }
}

val json = Json { serializersModule = module }
```

Use this when subtypes live in downstream modules or come from plugins.

## Custom KSerializer

Write one when a type doesn't map cleanly to JSON: primitive wrappers,
third-party types, tagged unions with unusual shape.

### Primitive-backed

```kotlin
object InstantAsLongSerializer : KSerializer<Instant> {
    override val descriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.toEpochMilliseconds())
    }

    override fun deserialize(decoder: Decoder): Instant =
        Instant.fromEpochMilliseconds(decoder.decodeLong())
}

@Serializable
data class Event(
    @Serializable(with = InstantAsLongSerializer::class)
    val at: Instant,
)
```

### Structured

```kotlin
object IpAddressSerializer : KSerializer<IpAddress> {
    override val descriptor = buildClassSerialDescriptor("IpAddress") {
        element<Int>("a")
        element<Int>("b")
        element<Int>("c")
        element<Int>("d")
    }

    override fun serialize(encoder: Encoder, value: IpAddress) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.a)
            encodeIntElement(descriptor, 1, value.b)
            encodeIntElement(descriptor, 2, value.c)
            encodeIntElement(descriptor, 3, value.d)
        }
    }

    override fun deserialize(decoder: Decoder): IpAddress =
        decoder.decodeStructure(descriptor) {
            var a = 0; var b = 0; var c = 0; var d = 0
            while (true) {
                when (val i = decodeElementIndex(descriptor)) {
                    0 -> a = decodeIntElement(descriptor, 0)
                    1 -> b = decodeIntElement(descriptor, 1)
                    2 -> c = decodeIntElement(descriptor, 2)
                    3 -> d = decodeIntElement(descriptor, 3)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index $i")
                }
            }
            IpAddress(a, b, c, d)
        }
}
```

### Surrogate pattern

Easier than writing encode/decode by hand — convert to a serializable
proxy:

```kotlin
@Serializable
private class UserSurrogate(val name: String, val ageDays: Int)

object UserSerializer : KSerializer<User> {
    override val descriptor = UserSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: User) {
        val s = UserSurrogate(value.name, value.age.inWholeDays.toInt())
        encoder.encodeSerializableValue(UserSurrogate.serializer(), s)
    }

    override fun deserialize(decoder: Decoder): User {
        val s = decoder.decodeSerializableValue(UserSurrogate.serializer())
        return User(s.name, s.ageDays.days)
    }
}
```

## JsonElement — dynamic payloads

For payloads that aren't schema-stable:

```kotlin
val element: JsonElement = Json.parseToJsonElement(text)
val name: String = element.jsonObject["user"]!!.jsonObject["name"]!!.jsonPrimitive.content
val items: List<JsonElement> = element.jsonObject["items"]!!.jsonArray.toList()
```

Build dynamically:

```kotlin
val obj = buildJsonObject {
    put("name", "Ada")
    put("age", 36)
    putJsonArray("tags") {
        add("admin"); add("active")
    }
    putJsonObject("address") {
        put("city", "Paris")
    }
}
val text = Json.encodeToString(JsonElement.serializer(), obj)
```

If you find yourself manipulating `JsonElement` extensively, declare a
proper `@Serializable` type instead — you keep type safety and avoid
runtime casts.

## Contextual serialization

Plug in a serializer for a type you can't annotate (third-party class):

```kotlin
@Serializable
data class Event(
    val id: String,
    @Contextual val at: Instant,
)

val json = Json {
    serializersModule = SerializersModule {
        contextual(Instant::class, InstantAsLongSerializer)
    }
}
```

## Format choice

| Format | Use when |
|---|---|
| JSON | Default — APIs, config, debug-friendly |
| ProtoBuf | Wire performance, compact binary, gRPC-adjacent workloads |
| CBOR | JSON semantics in binary (WebAuthn, COSE, IoT) |
| HOCON | Reading Typesafe Config files (decode only) |
| Properties | `.properties` files — rarely the best choice |

### ProtoBuf

```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.7.3")

@Serializable
data class Point(
    @ProtoNumber(1) val x: Int,
    @ProtoNumber(2) val y: Int,
)

val bytes = ProtoBuf.encodeToByteArray(Point(1, 2))
```

ProtoBuf is schema-less in `kotlinx-serialization` — it produces
proto-compatible bytes but does not generate `.proto` files. Interop with
non-Kotlin proto consumers requires they agree on the schema.

## Streaming (JVM / I/O)

For large payloads, stream through `InputStream` / `OutputStream`:

```kotlin
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import okio.buffer
import okio.source

val user = Json.decodeFromBufferedSource<User>(file.source().buffer())
```

The `kotlinx-serialization-json-okio` and `kotlinx-serialization-json-io`
artifacts provide streaming APIs.

## Interop: Ktor

Ktor Client integrates directly:

```kotlin
install(ContentNegotiation) {
    json(Json { ignoreUnknownKeys = true })
}

val user: User = client.get("/users/ada").body()
```

Ktor Server does the same via `application.install(ContentNegotiation)`.

## Common gotchas

- **Missing plugin** — forgetting `kotlin("plugin.serialization")` causes
  cryptic "serializer not found" / "no companion" errors at runtime.
- **`data object` needs `@Serializable`** — without it, polymorphic
  sealed parents fail.
- **`Json.encodeToString(value)` uses the compile-time type** — for
  polymorphism, use `encodeToString<Event>(login)` or pass the
  `KSerializer<Event>` explicitly.
- **Cyclic references** — not supported. Break the cycle or write a
  custom serializer that tags and resolves references.
- **Jackson + kotlinx on the same class** — don't. They disagree about
  defaults and nullability; readers get confused.
- **Plugin/compiler version mismatch** — `kotlin("plugin.serialization")`
  version must match the Kotlin compiler version, or you'll see odd
  codegen failures.
- **Large enums with `ignoreUnknownKeys`** — `ignoreUnknownKeys` only
  affects unknown **object fields**. For unknown enum values, use
  `coerceInputValues = true` plus a default.
- **Serializing generics** — `Json.encodeToString(list)` may need
  `ListSerializer(Foo.serializer())` explicitly if the compiler can't
  infer `T`. Prefer the reified `encodeToString<List<Foo>>(list)`.
- **`@SerialName` on class** — renames the sealed-polymorphism
  discriminator value, not the class keys. Kotlin property names are
  used for field keys unless `@SerialName` is on the property.

## Anti-patterns

```kotlin
// WRONG: no plugin.serialization — synthetic companion missing
plugins { kotlin("jvm") }

// WRONG: reconfiguring Json on every call
fun serialize(u: User) = Json { prettyPrint = true }.encodeToString(u)

// Correct: share one instance
private val json = Json { prettyPrint = true }
fun serialize(u: User) = json.encodeToString(u)
```

```kotlin
// WRONG: polymorphic encode without type
val login: Event = Event.Login("ada")
Json.encodeToString(login)   // encodes as Login, loses discriminator
// Correct
Json.encodeToString<Event>(login)
```

```kotlin
// WRONG: mutable state in @Serializable type
@Serializable
data class Config(var cache: MutableMap<String, String> = mutableMapOf())

// Correct: immutable
@Serializable
data class Config(val cache: Map<String, String> = emptyMap())
```

```kotlin
// WRONG: java.io.Serializable as transfer format
data class User(val name: String) : java.io.Serializable

// Correct: kotlinx.serialization
@Serializable
data class User(val name: String)
```

## References

- Repository — https://github.com/Kotlin/kotlinx.serialization
- Serialization guide — https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serialization-guide.md
- JSON features — https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/json.md
- Polymorphism — https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md
- Custom serializers — https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md
- Formats — https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/formats.md
- API reference — https://kotlinlang.org/api/kotlinx.serialization/
