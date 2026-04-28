---
description: "Okio I/O library by Square — the foundation kotlinx-io is based on. Reference for understanding kotlinx-io patterns."
globs:
  - "**/*.kt"
alwaysApply: false
---

## When to use this skill

- Understanding Okio patterns when reading third-party code or
  documentation that uses Okio.
- Mapping Okio concepts to their kotlinx-io equivalents.
- Migrating existing Okio code to kotlinx-io.

## When NOT to use this skill

- Writing new code in this project — use kotlinx-io directly (see
  `/kotlinx-io` skill).

## Project convention

**This project uses kotlinx-io exclusively.** Do not add Okio as a
dependency. This skill exists only as a reference for understanding
the patterns that kotlinx-io inherits from Okio.

---

# Core concepts

Okio provides the same layered I/O model that kotlinx-io adopted:

| Concept     | Purpose                                      |
|-------------|----------------------------------------------|
| `Buffer`    | Mutable byte buffer, implements both Source and Sink |
| `Source`    | Buffered read interface                      |
| `Sink`      | Buffered write interface                     |
| `ByteString`| Immutable byte sequence with hex/base64/UTF-8 conversions |
| `FileSystem`| Multiplatform file operations (read, write, list, move, delete) |
| `Path`      | Multiplatform file path                      |

Okio calls its unbuffered types `Source` and `Sink` directly, then
wraps them with `.buffer()` to get `BufferedSource` / `BufferedSink`.
kotlinx-io renames these to `RawSource` / `RawSink` and `.buffered()`.

---

# Mapping table: Okio to kotlinx-io

| Okio                          | kotlinx-io                        | Notes                           |
|-------------------------------|-----------------------------------|---------------------------------|
| `okio.Buffer`                 | `kotlinx.io.Buffer`               | Same role, same API shape       |
| `okio.Source`                 | `kotlinx.io.RawSource`            | Unbuffered read                 |
| `okio.Sink`                   | `kotlinx.io.RawSink`              | Unbuffered write                |
| `okio.BufferedSource`         | `kotlinx.io.Source`               | Buffered read                   |
| `okio.BufferedSink`           | `kotlinx.io.Sink`                 | Buffered write                  |
| `.buffer()`                   | `.buffered()`                     | Wrap raw in buffered            |
| `okio.ByteString`             | `kotlinx.io.bytestring.ByteString`| Immutable bytes                 |
| `"hex".decodeHex()`           | `ByteString(hexString)`           | Hex decoding                    |
| `byteString.hex()`            | `byteString.toHexString()`        | Hex encoding                    |
| `byteString.base64()`         | (not built-in)                    | Use `java.util.Base64` on JVM   |
| `okio.FileSystem`             | `kotlinx.io.files.SystemFileSystem` | Singleton, not `SYSTEM`       |
| `okio.FileSystem.SYSTEM`      | `kotlinx.io.files.SystemFileSystem` | No `.SYSTEM` accessor         |
| `okio.Path`                   | `kotlinx.io.files.Path`           | Same concept                    |
| `"path".toPath()`             | `Path("path")`                    | Construction                    |
| `path / "child"`              | `path / "child"`                  | Same operator                   |
| `fileSystem.source(path)`     | `SystemFileSystem.source(path)`   | Returns raw, buffer it          |
| `fileSystem.sink(path)`       | `SystemFileSystem.sink(path)`     | Returns raw, buffer it          |
| `fileSystem.appendingSink(p)` | `SystemFileSystem.sink(p, append = true)` | Append mode            |
| `fileSystem.exists(path)`     | `SystemFileSystem.exists(path)`   | Boolean                         |
| `fileSystem.list(path)`       | `SystemFileSystem.list(path)`     | Collection of Paths             |
| `fileSystem.createDirectories(p)` | `SystemFileSystem.createDirectories(p)` | Recursive mkdir     |
| `fileSystem.delete(path)`     | `SystemFileSystem.delete(path)`   | Delete file or empty dir        |
| `fileSystem.atomicMove(a, b)` | `SystemFileSystem.atomicMove(a, b)` | Rename / move                 |
| `fileSystem.metadata(path)`   | `SystemFileSystem.metadataOrNull(path)` | Returns nullable       |
| `source.readUtf8()`           | `source.readString()`             | Read all as String              |
| `source.readUtf8Line()`       | `source.readLine()`               | Read one line                   |
| `source.readByteArray()`      | `source.readByteArray()`          | Same                            |
| `source.readByte()`           | `source.readByte()`               | Same                            |
| `source.readInt()`            | `source.readInt()`                | Same (big-endian)               |
| `source.readIntLe()`          | `source.readIntLe()`              | Same (little-endian)            |
| `source.readLong()`           | `source.readLong()`               | Same (big-endian)               |
| `source.readLongLe()`         | `source.readLongLe()`             | Same (little-endian)            |
| `source.readShort()`          | `source.readShort()`              | Same (big-endian)               |
| `source.readShortLe()`        | `source.readShortLe()`            | Same (little-endian)            |
| `source.readByteString()`     | `source.readByteString()`         | Same                            |
| `source.readDecimalLong()`    | `source.readDecimalLong()`        | Same                            |
| `source.readHexadecimalUnsignedLong()` | `source.readHexadecimalUnsignedLong()` | Same          |
| `source.exhausted()`          | `source.exhausted()`              | Same                            |
| `source.indexOf(byte)`        | `source.indexOf(byte)`            | Same                            |
| `source.peek()`               | `source.peek()`                   | Same                            |
| `source.skip(n)`              | `source.skip(n)`                  | Same                            |
| `source.readAll(sink)`        | `source.transferTo(sink)`         | Name changed                    |
| `sink.writeUtf8(string)`      | `sink.writeString(string)`        | Name changed                    |
| `sink.writeByte(b)`           | `sink.writeByte(b)`               | Same                            |
| `sink.writeInt(i)`            | `sink.writeInt(i)`                | Same                            |
| `sink.writeIntLe(i)`          | `sink.writeIntLe(i)`              | Same                            |
| `sink.writeLong(l)`           | `sink.writeLong(l)`               | Same                            |
| `sink.writeLongLe(l)`         | `sink.writeLongLe(l)`             | Same                            |
| `sink.writeShort(s)`          | `sink.writeShort(s)`              | Same                            |
| `sink.writeShortLe(s)`        | `sink.writeShortLe(s)`            | Same                            |
| `sink.writeDecimalLong(l)`    | `sink.writeDecimalLong(l)`        | Same                            |
| `sink.writeHexadecimalUnsignedLong(l)` | `sink.writeHexadecimalUnsignedLong(l)` | Same          |
| `sink.write(byteString)`      | `sink.write(byteString)`          | Same                            |
| `sink.write(byteArray)`       | `sink.write(byteArray)`           | Same                            |
| `sink.flush()`                | `sink.flush()`                    | Same                            |
| `sink.emit()`                 | `sink.emit()`                     | Same                            |

---

# When to use Okio vs kotlinx-io

| Situation                                    | Use          |
|----------------------------------------------|--------------|
| New code in this project                     | kotlinx-io   |
| Third-party library returns `okio.Source`    | Bridge at boundary, convert to kotlinx-io internally |
| OkHttp response body (returns Okio types)    | Use OkHttp's Okio types at the call site, convert to kotlinx-io for internal processing |
| Reading Okio documentation to understand a pattern | Map to kotlinx-io using the table above |

Okio and kotlinx-io are **not interchangeable at the type level** --
you cannot pass an `okio.Source` where `kotlinx.io.Source` is expected.
Bridge at the boundary using byte arrays or streams:

```kotlin
// Okio Source → kotlinx-io Source (via InputStream bridge)
val kotlinxSource = okioSource.inputStream().asSource().buffered()

// kotlinx-io Source → Okio Source (via InputStream bridge)
val okioSource = kotlinxSource.asInputStream().source().buffer()
```
