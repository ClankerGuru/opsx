---
name: serialization-patterns
description: Reference guide for serialization in Gradle plugin code — kotlinx-serialization for JSON, custom KSerializer for value classes and compound strings, manual YAML parsing for simple configs, java.io.Serializable for the Gradle configuration cache, and rules for resilient decoding. Activates when reading/writing config files or designing data classes carried by task properties.
---

# Serialization Patterns

> For related topics see:
> - `/kotlinx-serialization` — full kotlinx-serialization reference
> - `/kotlin-lang` — `@Serializable`, value classes, data classes
> - `/kotlin-conventions` — `runCatching` over try/catch, value classes with `init { require(...) }`
> - `/gradle` — configuration cache, task property serialization requirements
> - `/gradle-providers-properties` — `Property<T>` vs raw fields

## The three serialization surfaces in a Gradle plugin

| Surface | Format | Mechanism |
|---|---|---|
| Config files read from disk | JSON / YAML | kotlinx-serialization for JSON; hand-written for YAML |
| Task property values carried across the configuration cache | Binary (Gradle-internal) | `java.io.Serializable` + `serialVersionUID` |
| HTTP payloads / IPC | JSON | kotlinx-serialization |

Keep the three separate. A data class may implement both
`@Serializable` and `java.io.Serializable` when it crosses both
surfaces — that's legitimate, not a code smell.

## kotlinx-serialization for JSON

Apply the convention plugin that brings the compiler plugin and the
JSON module:

```kotlin
// build.gradle.kts
plugins {
    id("clkx-conventions")
    id("clkx-serialization")
}
```

`clkx-serialization.gradle.kts` wires:
- `kotlin("plugin.serialization")` — compile-time serializer generation.
- `org.jetbrains.kotlinx:kotlinx-serialization-json` — runtime.

### Annotated data classes

```kotlin
@Serializable
data class RepositoryEntry(
    @SerialName("name") val name: String,
    @SerialName("path") val path: RepositoryUrl,
    @SerialName("category") val category: String = "",
    @SerialName("substitute") val substitute: Boolean = false,
    @SerialName("substitutions") val substitutions: List<ArtifactSubstitution> = emptyList(),
    @SerialName("baseBranch") val baseBranch: GitReference = GitReference("main"),
)
```

Rules:

- `@SerialName` on every field, even when it matches the Kotlin name.
  Makes the wire contract explicit; prevents silent breakage from
  Kotlin renames.
- Give every optional field a default. Missing keys decode to the
  default.
- Keep fields plain data — no side-effecting defaults, no references
  to Gradle types.

### Json instance configuration

One instance per profile, shared:

```kotlin
internal val json = Json {
    ignoreUnknownKeys = true     // servers / configs grow fields
    explicitNulls = false        // null means absent
    encodeDefaults = false       // small payloads
}

val entries = json.decodeFromString<List<RepositoryEntry>>(configText)
```

`ignoreUnknownKeys = true` is the default for anything that reads
config files — users add comments, tooling adds metadata, you don't
want to crash.

### Custom KSerializer — value classes as primitives

Wrap the serialization in a companion `object`:

```kotlin
@Serializable(with = GitReferenceSerializer::class)
@JvmInline
value class GitReference(val value: String) {
    override fun toString(): String = value
}

object GitReferenceSerializer : KSerializer<GitReference> {
    override val descriptor =
        PrimitiveSerialDescriptor("GitReference", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): GitReference =
        GitReference(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: GitReference) =
        encoder.encodeString(value.value)
}
```

The `@Serializable(with = ...)` couples the value class to its
serializer so consumers don't have to remember to pass
`GitReference.serializer()` explicitly.

### Custom KSerializer — compound types as strings

For "short form" serialization where a struct renders as a single
string:

```kotlin
@Serializable(with = ArtifactSubstitutionSerializer::class)
data class ArtifactSubstitution(
    val artifact: ArtifactId,
    val project: ProjectPath,
)

object ArtifactSubstitutionSerializer : KSerializer<ArtifactSubstitution> {
    override val descriptor =
        PrimitiveSerialDescriptor("Substitution", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ArtifactSubstitution {
        val raw = decoder.decodeString()
        val parts = raw.split(",", limit = 2)
        if (parts.size != 2) {
            throw SerializationException(
                "Invalid substitution: '$raw'. Expected: 'group:artifact,project'"
            )
        }
        return runCatching {
            ArtifactSubstitution(
                artifact = ArtifactId(parts[0].trim()),
                project = ProjectPath(parts[1].trim()),
            )
        }.getOrElse { e ->
            throw SerializationException("Invalid substitution: '$raw'. ${e.message}", e)
        }
    }

    override fun serialize(encoder: Encoder, value: ArtifactSubstitution) {
        encoder.encodeString(value.toString())
    }
}
```

Key points:

- Error messages include the raw input and the expected format — the
  user can fix the config without reading code.
- `runCatching { ... }.getOrElse { throw SerializationException(...) }`
  — never `try/catch` (project rule).
- `toString()` on the data class must produce a parseable form.

### Sealed polymorphism for config unions

When a config field can take one of a finite set of shapes:

```kotlin
@Serializable
sealed interface SourceSpec {
    @Serializable
    @SerialName("git")
    data class Git(val url: String, val ref: String = "main") : SourceSpec

    @Serializable
    @SerialName("local")
    data class Local(val path: String) : SourceSpec
}
```

The wire form is `{"type": "git", "url": "...", "ref": "..."}`.
Every subclass needs `@SerialName`, or the JVM FQN becomes the
discriminator — brittle for refactors.

### Surrogate pattern for data classes that can't be @Serializable

When the real type brings a framework dependency (e.g. a `File`),
serialize a surrogate:

```kotlin
@Serializable
private data class RepositorySurrogate(
    val name: String,
    val absolutePath: String,
)

object RepositorySerializer : KSerializer<Repository> {
    override val descriptor = RepositorySurrogate.serializer().descriptor
    override fun deserialize(decoder: Decoder): Repository {
        val s = decoder.decodeSerializableValue(RepositorySurrogate.serializer())
        return Repository(s.name, File(s.absolutePath))
    }
    override fun serialize(encoder: Encoder, value: Repository) {
        encoder.encodeSerializableValue(
            RepositorySurrogate.serializer(),
            RepositorySurrogate(value.name, value.file.absolutePath),
        )
    }
}
```

## Manual YAML parsing for simple configs

YAML gives users comment support and flexible inline/block forms. For
simple configs (scalar `key: value` plus occasional list), avoid
pulling in snakeyaml / kaml — the dependency isn't worth it.

### Scalar key-value

```kotlin
data class ChangeConfig(
    val name: String,
    val status: String = "active",
    val depends: List<String> = emptyList(),
) {
    companion object {
        fun parse(file: File): ChangeConfig? {
            if (!file.exists()) return null
            val lines = file.readLines()
            val scalars = parseScalars(lines)
            val name = scalars["name"] ?: return null
            return ChangeConfig(
                name = name,
                status = scalars["status"] ?: "active",
                depends = parseDepends(lines, scalars["depends"]),
            )
        }

        internal fun parseScalars(lines: List<String>): Map<String, String> {
            val result = mutableMapOf<String, String>()
            lines
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .filter { it.contains(':') }
                .forEach { trimmed ->
                    val colonIndex = trimmed.indexOf(':')
                    val key = trimmed.substring(0, colonIndex).trim()
                    val value = trimmed.substring(colonIndex + 1).trim()
                    if (value.isNotEmpty()) result[key] = value
                }
            return result
        }
    }
}
```

### Bracket list (inline YAML array)

```kotlin
internal fun parseBracketList(value: String): List<String> {
    val content = value.removePrefix("[").removeSuffix("]").trim()
    if (content.isEmpty()) return emptyList()
    return content.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
```

### Block list (`- item`)

```kotlin
private fun parseDependsBlock(lines: List<String>): List<String> {
    val cleaned = lines.map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
    val dependsIndex = cleaned.indexOfFirst {
        it.startsWith("depends:") && it.substringAfter(":").isBlank()
    }
    if (dependsIndex < 0) return emptyList()
    return cleaned
        .drop(dependsIndex + 1)
        .takeWhile { it.startsWith("- ") }
        .map { it.removePrefix("- ").trim() }
}
```

### Writing YAML back

Use `StringBuilder`:

```kotlin
fun write(file: File, config: ChangeConfig) {
    val sb = StringBuilder()
    sb.appendLine("name: ${config.name}")
    sb.appendLine("status: ${config.status}")
    if (config.depends.isNotEmpty()) {
        sb.appendLine("depends: [${config.depends.joinToString(", ")}]")
    }
    file.writeText(sb.toString())
}
```

### In-place YAML field update

```kotlin
fun updateStatus(file: File, newStatus: String) {
    if (!file.exists()) {
        file.writeText("status: $newStatus\n")
        return
    }
    val lines = file.readLines()
    val updated = mutableListOf<String>()
    var statusFound = false
    for (line in lines) {
        if (line.trimStart().startsWith("status:")) {
            updated.add("status: $newStatus")
            statusFound = true
        } else {
            updated.add(line)
        }
    }
    if (!statusFound) updated.add("status: $newStatus")
    file.writeText(updated.joinToString("\n") + "\n")
}
```

### When manual YAML stops being enough

Reach for kaml (`com.charleskorn.kaml:kaml`) when the config needs:

- Multi-line strings with `|` / `>` folding.
- Anchors / aliases (`&foo` / `*foo`).
- Nested maps > 2 levels deep.
- Sequences of maps (`- key: value`).

kaml integrates with kotlinx-serialization so the annotated data
classes are reused.

## java.io.Serializable for Gradle task properties

Gradle's configuration cache serializes task state between builds.
**Any data class stored in a task's `ListProperty`, `MapProperty`, or
`Property` must be `java.io.Serializable`**, or the cache fails.

```kotlin
data class IncludedBuildInfo(
    val name: String,
    val dir: File,
    val relPath: String,
    val projects: List<Pair<String, File>>,
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
```

Rules:

- Always declare `serialVersionUID`. Without it, JVM generates one
  from the shape of the class — any field change breaks existing
  caches.
- Every field must itself be `Serializable` (primitives, `String`,
  `File`, `Pair`, `List`, `Map`, and other `Serializable` types).
- Avoid bringing Gradle types (`Project`, `Task`) into the class —
  they aren't serializable.
- This does **not** replace `@Serializable` for JSON — they're two
  orthogonal mechanisms.

## Defensive decoding

### Missing file

```kotlin
internal fun populateFromConfig(extension: SettingsExtension) {
    val configFile = File(settingsDir, CONFIG_FILE)
    if (!configFile.exists()) {
        configFile.writeText("[]\n")
        return
    }
    val configText = configFile.readText().trim()
    if (configText.isBlank() || configText == "[]") return

    json.decodeFromString<List<MyEntry>>(configText)
        .forEach { entry ->
            extension.items.register(entry.name) { it.path.set(entry.path) }
        }
}
```

### Corrupt file

```kotlin
val entries = runCatching { json.decodeFromString<List<MyEntry>>(text) }
    .getOrElse { e ->
        logger.warn("Failed to parse $CONFIG_FILE: ${e.message}. Using empty list.")
        emptyList()
    }
```

Never let a corrupt config file crash the settings phase — it locks
the user out of every task including the one that fixes the config.

### Version migration

When the schema changes, decode an older version via optional fields
and a default transformation:

```kotlin
@Serializable
data class RepositoryEntryV2(
    val name: String,
    val path: String,
    val legacyField: String? = null,      // removed in V3, still accepted
    val newField: String = "default",     // added in V3
)
```

Keep old fields for one or two releases so users upgrade gradually.

## Complete JSON config reading flow

```kotlin
data object MyPlugin {
    class SettingsPlugin : Plugin<Settings> {
        internal val json = Json { ignoreUnknownKeys = true }

        internal fun populateFromConfig(extension: SettingsExtension) {
            val configFile = File(settingsDir, CONFIG_FILE)
            if (!configFile.exists()) {
                configFile.writeText("[]\n")
                return
            }
            val configText = configFile.readText().trim()
            if (configText.isBlank() || configText == "[]") return

            json.decodeFromString<List<MyEntry>>(configText)
                .forEach { entry ->
                    extension.items.register(entry.name) { item ->
                        item.path.set(entry.path)
                    }
                }
        }
    }
}
```

## Anti-patterns

```kotlin
// WRONG: Gson / Jackson
val gson = Gson()
val config = gson.fromJson(text, Config::class.java)
// Correct: kotlinx-serialization (no reflection, KMP-ready, shipped here)

// WRONG: forget ignoreUnknownKeys
val json = Json { }
json.decodeFromString<Config>(text)           // crashes on unknown field
// Correct:
val json = Json { ignoreUnknownKeys = true }

// WRONG: crash on missing config
val text = configFile.readText()
// Correct:
if (!configFile.exists()) { createWithDefault(); return }
val text = configFile.readText()

// WRONG: data class in task property without Serializable
data class Info(val name: String)
// Correct:
data class Info(val name: String) : java.io.Serializable {
    companion object { private const val serialVersionUID = 1L }
}

// WRONG: try/catch in deserializer
try { ArtifactId(parts[0]) } catch (e: Exception) { throw SerializationException(...) }
// Correct:
runCatching { ArtifactId(parts[0]) }
    .getOrElse { e -> throw SerializationException("...", e) }

// WRONG: snakeyaml / kaml for a simple key:value file
// dependencies { implementation("org.yaml:snakeyaml:...") }
// Correct: manual parsing for simple configs; kaml only when needed

// WRONG: serialize a Gradle type
data class TaskInfo(val project: Project) : java.io.Serializable
// Correct: capture what you need as plain data
data class TaskInfo(val projectPath: String) : java.io.Serializable

// WRONG: missing serialVersionUID
data class Info(val name: String) : java.io.Serializable
// Correct:
data class Info(val name: String) : java.io.Serializable {
    companion object { private const val serialVersionUID = 1L }
}

// WRONG: polymorphic encode without type argument
val event: Event = Event.Login(...)
Json.encodeToString(event)           // encodes as Login, loses discriminator
// Correct:
Json.encodeToString<Event>(event)
```

## Common pitfalls

- **Schema drift on the wire** — a field rename without `@SerialName`
  silently breaks downstream. Always set `@SerialName`.
- **`serialVersionUID` bump forgotten** — add/remove a field on a
  `java.io.Serializable` class and CI caches start throwing
  `InvalidClassException`. Treat `serialVersionUID` as part of the
  public API.
- **Decoder crashes on YAML comments** — the manual scalar parser
  above handles `#` lines; a naive `split(":", limit = 2)` does not.
- **`BOM` at the top of a config file** — `readText()` includes it;
  JSON parser chokes. Strip with `.removePrefix("\uFEFF")`.
- **Missing default on an optional JSON field with
  `explicitNulls = false`** — decoding a missing key fails. Always set
  a default.
- **Polymorphic sealed without `@SerialName`** — discriminator becomes
  the FQN. Rename the class and every persisted config breaks.
- **Writing YAML manually that the parser can't read back** — run a
  round-trip test.

## References

- kotlinx.serialization guide — https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serialization-guide.md
- JSON features — https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/json.md
- Custom serializers — https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md
- Gradle configuration cache — https://docs.gradle.org/current/userguide/configuration_cache.html
- `java.io.Serializable` spec — https://docs.oracle.com/javase/8/docs/platform/serialization/spec/serialTOC.html
