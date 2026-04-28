---
description: "kotlinx-io multiplatform I/O — Buffer, Source, Sink, FileSystem, Path. Use instead of java.io.File."
globs:
  - "**/*.kt"
alwaysApply: false
---

## When to use this skill

- Reading or writing files, streams, or byte buffers in Kotlin.
- Any file system operation (list, create, delete, move, metadata).
- Replacing `java.io.File`, `java.io.InputStream`, or
  `java.io.OutputStream` with multiplatform-safe APIs.
- Building pipelines that process bytes incrementally (network I/O,
  checksums, encoding).

## When NOT to use this skill

- Memory-mapped files or NIO channels beyond simple read/write — stay
  on `java.nio` for those.
- Android `ContentResolver` URIs — those aren't regular file paths.

## Project convention

This project uses **kotlinx-io exclusively**. Do not introduce
`java.io.File`, `java.io.InputStream`, or `java.io.OutputStream` in
new code. Wrap legacy APIs with `asSource()` / `asSink()` at the
boundary.

---

# Core types

| Type       | Role                                          |
|------------|-----------------------------------------------|
| `Buffer`   | Mutable, resizable byte buffer. Both Source and Sink. |
| `Source`    | Read bytes. The main read interface.          |
| `Sink`     | Write bytes. The main write interface.        |
| `RawSource` | Unbuffered read — implement for custom sources. |
| `RawSink`  | Unbuffered write — implement for custom sinks. |

`Source` and `Sink` are buffered wrappers. `RawSource` and `RawSink`
are the low-level interfaces you implement when building custom I/O.
Buffer them with `.buffered()`.

```kotlin
import kotlinx.io.*
```

---

# Read operations (Source)

## Strings and lines

```kotlin
source.readString()                   // read all remaining bytes as UTF-8 String
source.readString(byteCount = 10)     // read exactly 10 bytes as UTF-8 String
source.readLine()                     // read until \n or \r\n, null at EOF
source.readLineLenient()              // like readLine but returns "" at EOF
```

## Byte arrays

```kotlin
source.readByteArray()                // read all remaining as ByteArray
source.readByteArray(byteCount = 16)  // read exactly 16 bytes
source.readAtMostTo(byteArray, startIndex, endIndex)  // partial read, returns count
```

## Primitives — big-endian (default)

```kotlin
source.readByte()                     // Byte
source.readShort()                    // Short (2 bytes, big-endian)
source.readInt()                      // Int (4 bytes, big-endian)
source.readLong()                     // Long (8 bytes, big-endian)
source.readFloat()                    // Float (4 bytes, big-endian)
source.readDouble()                   // Double (8 bytes, big-endian)
```

## Primitives — little-endian

```kotlin
source.readShortLe()                  // Short (2 bytes, little-endian)
source.readIntLe()                    // Int (4 bytes, little-endian)
source.readLongLe()                   // Long (8 bytes, little-endian)
source.readFloatLe()                  // Float (4 bytes, little-endian)
source.readDoubleLe()                 // Double (8 bytes, little-endian)
```

## Unsigned variants

```kotlin
source.readUByte()                    // UByte
source.readUShort()                   // UShort (big-endian)
source.readUInt()                     // UInt (big-endian)
source.readULong()                    // ULong (big-endian)
source.readUShortLe()                 // UShort (little-endian)
source.readUIntLe()                   // UInt (little-endian)
source.readULongLe()                  // ULong (little-endian)
```

## Text-encoded numbers

```kotlin
source.readDecimalLong()              // read ASCII decimal digits as Long
source.readHexadecimalUnsignedLong()  // read ASCII hex digits as Long
```

## Code points

```kotlin
source.readCodePointValue()           // read one UTF-8 code point as Int
```

## ByteString

```kotlin
source.readByteString()               // read all remaining as ByteString
source.readByteString(byteCount = 8)  // read exactly 8 bytes as ByteString
```

## Inspection and search

```kotlin
source.exhausted()                    // true if no bytes remain
source.peek()                         // returns a Source that reads ahead without consuming
source.indexOf(byte)                  // index of first occurrence, -1 if not found
source.indexOf(byteString)            // index of first occurrence of byte sequence
source.startsWith(byte)               // true if next byte matches
source.startsWith(byteString)         // true if next bytes match
source.skip(byteCount)                // discard n bytes
source.transferTo(sink)               // copy all remaining bytes to a Sink
```

---

# Write operations (Sink)

## Strings

```kotlin
sink.writeString(string)              // write UTF-8 encoded String
sink.writeString(string, startIndex, endIndex)  // substring
```

## Byte arrays

```kotlin
sink.write(byteArray)                 // write entire ByteArray
sink.write(byteArray, startIndex, endIndex)     // range
```

## Primitives — big-endian (default)

```kotlin
sink.writeByte(byte)                  // 1 byte
sink.writeShort(short)                // 2 bytes, big-endian
sink.writeInt(int)                    // 4 bytes, big-endian
sink.writeLong(long)                  // 8 bytes, big-endian
sink.writeFloat(float)                // 4 bytes, big-endian
sink.writeDouble(double)              // 8 bytes, big-endian
```

## Primitives — little-endian

```kotlin
sink.writeShortLe(short)              // 2 bytes, little-endian
sink.writeIntLe(int)                  // 4 bytes, little-endian
sink.writeLongLe(long)                // 8 bytes, little-endian
sink.writeFloatLe(float)              // 4 bytes, little-endian
sink.writeDoubleLe(double)            // 8 bytes, little-endian
```

## Unsigned variants

```kotlin
sink.writeUByte(ubyte)                // 1 byte
sink.writeUShort(ushort)              // 2 bytes, big-endian
sink.writeUInt(uint)                  // 4 bytes, big-endian
sink.writeULong(ulong)               // 8 bytes, big-endian
sink.writeUShortLe(ushort)            // 2 bytes, little-endian
sink.writeUIntLe(uint)               // 4 bytes, little-endian
sink.writeULongLe(ulong)             // 8 bytes, little-endian
```

## Text-encoded numbers

```kotlin
sink.writeDecimalLong(long)           // write Long as ASCII decimal digits
sink.writeHexadecimalUnsignedLong(long)  // write Long as ASCII hex digits
```

## Code points

```kotlin
sink.writeCodePointValue(codePoint)   // write one UTF-8 code point from Int
```

## ByteString

```kotlin
sink.write(byteString)               // write ByteString contents
```

## Buffer access and flushing

```kotlin
sink.writeToInternalBuffer { buffer: Buffer ->
    // direct access to the internal buffer for bulk writes
    buffer.writeInt(42)
    buffer.writeString("hello")
}
sink.flush()                          // push buffered bytes to underlying sink
sink.emit()                           // flush partial buffer (when available)
```

---

# File system

## Path

```kotlin
import kotlinx.io.files.Path

val p = Path("/usr/local/bin/app")
val p2 = Path("relative/path")
val p3 = Path("/usr", "local", "bin")     // join segments

p.name                                // "app" — last segment
p.parent                              // Path("/usr/local/bin")
p.isAbsolute                          // true

// Joining
p / "child"                           // Path("/usr/local/bin/app/child")
```

## SystemFileSystem

```kotlin
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.Path

val fs = SystemFileSystem

// Read
val source: Source = fs.source(Path("/etc/hosts"))
val content = source.use { it.readString() }

// Write
val sink: Sink = fs.sink(Path("/tmp/output.txt"))
sink.use { it.writeString("hello\n") }

// Append
val appendSink: Sink = fs.sink(Path("/tmp/output.txt"), append = true)

// Existence
fs.exists(Path("/etc/hosts"))         // Boolean

// Listing
val entries: Collection<Path> = fs.list(Path("/usr/local"))

// Create directories
fs.createDirectories(Path("/tmp/a/b/c"))

// Delete
fs.delete(Path("/tmp/output.txt"))    // throws if not found
fs.delete(Path("/tmp/output.txt"), mustExist = false)

// Move / rename
fs.atomicMove(
    source = Path("/tmp/old.txt"),
    destination = Path("/tmp/new.txt"),
)

// Resolve symlinks
val resolved: Path = fs.resolve(Path("/usr/local/bin/app"))
```

## FileMetadata

```kotlin
val meta: FileMetadata? = fs.metadataOrNull(Path("/etc/hosts"))
meta?.isRegularFile       // Boolean
meta?.isDirectory         // Boolean
meta?.size                // Long — file size in bytes
```

## System constants

```kotlin
import kotlinx.io.files.SystemTemporaryDirectory   // Path — platform temp dir
import kotlinx.io.files.SystemPathSeparator         // Char — '/' or '\\'
```

## SystemLineSeparator

```kotlin
import kotlinx.io.SystemLineSeparator               // String — "\n" or "\r\n"
```

---

# JVM interop

Bridge between kotlinx-io and `java.io` / `java.nio`:

```kotlin
import kotlinx.io.asSource
import kotlinx.io.asSink
import kotlinx.io.asInputStream
import kotlinx.io.asOutputStream
import kotlinx.io.asByteChannel

// java.io.InputStream → Source
val source: Source = inputStream.asSource().buffered()

// java.io.OutputStream → Sink
val sink: Sink = outputStream.asSink().buffered()

// Source → java.io.InputStream
val javaIn: InputStream = source.asInputStream()

// Sink → java.io.OutputStream
val javaOut: OutputStream = sink.asOutputStream()

// Source/Sink → java.nio.channels.ByteChannel
val channel: ByteChannel = source.asByteChannel()
```

Always call `.buffered()` on the result of `asSource()` / `asSink()`
— the raw wrappers are unbuffered.

---

# Common patterns

## Read an entire file to String

```kotlin
val text = SystemFileSystem.source(path).buffered().use { it.readString() }
```

## Write a String to a file

```kotlin
SystemFileSystem.sink(path).buffered().use { it.writeString(content) }
```

## Copy a file

```kotlin
SystemFileSystem.source(src).buffered().use { source ->
    SystemFileSystem.sink(dst).buffered().use { sink ->
        source.transferTo(sink)
    }
}
```

## Read lines

```kotlin
SystemFileSystem.source(path).buffered().use { source ->
    val lines = buildList {
        while (true) {
            add(source.readLine() ?: break)
        }
    }
}
```

## Temporary file

```kotlin
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.files.SystemFileSystem

val tmpDir = SystemTemporaryDirectory
val tmpFile = tmpDir / "prefix-${System.nanoTime()}.tmp"
SystemFileSystem.sink(tmpFile).buffered().use { it.writeString("temp data") }
// ... use tmpFile ...
SystemFileSystem.delete(tmpFile, mustExist = false)
```

## Checksum / hash a file

```kotlin
import java.security.MessageDigest
import kotlinx.io.files.SystemFileSystem

fun hashFile(path: Path, algorithm: String = "SHA-256"): ByteArray {
    val digest = MessageDigest.getInstance(algorithm)
    SystemFileSystem.source(path).buffered().use { source ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = source.readAtMostTo(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest()
}
```

## Download URL to file (JVM)

```kotlin
import java.net.URI
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem

fun download(url: String, dest: Path) {
    val conn = URI(url).toURL().openConnection()
    conn.getInputStream().asSource().buffered().use { source ->
        SystemFileSystem.sink(dest).buffered().use { sink ->
            source.transferTo(sink)
        }
    }
}
```

---

# FORBIDDEN

| Do NOT | Do instead |
|--------|-----------|
| `java.io.File` | `kotlinx.io.files.Path` + `SystemFileSystem` |
| `java.io.FileInputStream` | `SystemFileSystem.source(path).buffered()` |
| `java.io.FileOutputStream` | `SystemFileSystem.sink(path).buffered()` |
| `java.io.BufferedReader(FileReader(...))` | `SystemFileSystem.source(path).buffered()` then `readLine()` |
| `Files.readString(path)` | `SystemFileSystem.source(path).buffered().use { it.readString() }` |
| `Files.write(path, bytes)` | `SystemFileSystem.sink(path).buffered().use { it.write(bytes) }` |
| `file.exists()` | `SystemFileSystem.exists(path)` |
| `file.mkdirs()` | `SystemFileSystem.createDirectories(path)` |
| `file.delete()` | `SystemFileSystem.delete(path)` |
| `file.listFiles()` | `SystemFileSystem.list(path)` |
| `file.renameTo(dest)` | `SystemFileSystem.atomicMove(src, dst)` |
