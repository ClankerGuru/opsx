---
name: kotlin-multiplatform
description: Reference guide for Kotlin Multiplatform (KMP) covering target selection, the default source-set hierarchy, expect/actual declarations, the kotlin { } Gradle DSL, Compose Multiplatform, library compatibility, publishing, and common gotchas. Activates when deciding whether to adopt KMP or structuring a KMP module.
---

# Kotlin Multiplatform

> For generic Kotlin language and DSL topics see:
> - `/kotlin-lang` — language features you'll still use in common code
> - `/kotlin-functional-first` — functional style that ports cleanly across targets
> - `/kotlinx-coroutines` — concurrency that Just Works on every KMP target
> - `/kotlinx-serialization` — JSON/protobuf in common code
> - `/gradle` — Gradle lifecycle, Settings/Project model
> - `/kotlin-dsl` — Gradle Kotlin DSL usage
> - `/gradle-providers-properties` — lazy Property<T> API used in `kotlin { }` DSL

## When to reach for this skill

- Deciding between plain Kotlin/JVM and Kotlin Multiplatform.
- Designing the source-set hierarchy (`commonMain`, `jvmMain`, `iosMain`, ...).
- Writing `expect` / `actual` for platform primitives.
- Configuring the `kotlin { }` block: targets, compiler options, dependencies.
- Auditing a third-party library for KMP compatibility.
- Publishing a KMP library to Maven Central or a private repository.
- Adopting Compose Multiplatform for shared UI.

## Is KMP worth the tax?

KMP introduces a real build-time cost: Kotlin/Native compilation is slow, IDE
tooling has rough edges, and the library ecosystem is narrower than JVM.
Adopt it only when the benefits outweigh that tax.

**Adopt when all of these are true:**
- The code is platform-neutral logic — parsing, validation, domain models,
  protocols, state machines, view models — or you want a shared UI via
  Compose Multiplatform.
- You ship to more than one target you actually care about: Android + iOS,
  JVM + JS, server + browser, desktop + mobile.
- Your dependencies are already KMP-ready, or you are willing to wrap the
  few JVM-only ones behind `expect`/`actual`.
- You can tolerate Kotlin/Native's slower build for non-JVM targets.

**Skip when:**
- You only ship on the JVM. Plain `kotlin("jvm")` has less friction and a
  larger ecosystem.
- The shared logic is trivial (a few hundred lines) — duplication is
  cheaper than the toolchain tax.
- Your core dependencies are JVM-only (most AWS SDKs, Jackson, Guava,
  Spring, JPA, JDBC).
- You are building a Gradle plugin — always ship `kotlin("jvm")`.

For this project (Gradle plugin), prefer `kotlin("jvm")`. Use KMP only for
libraries that are genuinely consumed from multiple targets.

## Targets

Targets are platforms the module compiles for. Declare each one in the
`kotlin { }` block.

| Target | Declaration | Notes |
|---|---|---|
| JVM | `jvm()` | Server, desktop, Android libraries consumed from JVM |
| Android | `androidTarget()` | Requires `com.android.library` / `com.android.application` (AGP) |
| iOS | `iosX64()`, `iosArm64()`, `iosSimulatorArm64()` | Kotlin/Native; need all three for Intel+ARM simulators and devices |
| macOS | `macosArm64()`, `macosX64()` | Native desktop (Apple Silicon + Intel) |
| Linux | `linuxX64()`, `linuxArm64()` | Native desktop |
| Windows | `mingwX64()` | Native desktop (MinGW toolchain) |
| watchOS | `watchosArm64()`, `watchosX64()`, `watchosSimulatorArm64()` | Apple Watch |
| tvOS | `tvosArm64()`, `tvosX64()`, `tvosSimulatorArm64()` | Apple TV |
| JavaScript | `js(IR) { browser(); nodejs() }` | IR compiler; legacy backend removed in 2.0 |
| WebAssembly | `wasmJs { browser() }`, `wasmWasi { nodejs() }` | Two Wasm variants; `wasmJs` targets browsers, `wasmWasi` targets server/CLI |

Declare only targets you ship. Each extra target costs build time.

## Default source-set hierarchy

Kotlin 1.9.20+ ships a **default hierarchy template** wired automatically
when you declare targets. You rarely need to create intermediate source
sets by hand.

```
commonMain
├── jvmMain
├── androidMain
├── jsMain
├── wasmJsMain
├── wasmWasiMain
└── nativeMain
    ├── appleMain
    │   ├── iosMain
    │   │   ├── iosArm64Main
    │   │   ├── iosX64Main
    │   │   └── iosSimulatorArm64Main
    │   ├── macosMain
    │   ├── tvosMain
    │   └── watchosMain
    ├── linuxMain
    └── mingwMain
```

Every `xxxTest` mirror exists too (`commonTest`, `jvmTest`, `iosTest`, ...).

Rules of thumb:

- Put code at the **highest** level where it still compiles. Keep
  JVM-specific code out of `commonMain`; keep Apple-specific code in
  `appleMain` not duplicated between `iosMain` and `macosMain`.
- Every level sees APIs declared in its ancestors.
- `iosMain` code can call into `appleMain` → `nativeMain` → `commonMain`.
- `commonMain` code cannot reference anything below it.

## expect / actual

`expect` declares a signature that **must** be implemented by an `actual`
in every target that compiles the declaring source set. It's the
compile-time polymorphism equivalent of a platform SPI.

```kotlin
// commonMain
expect fun currentTimeMillis(): Long

expect class Uuid() {
    override fun toString(): String
    companion object {
        fun random(): Uuid
    }
}
```

```kotlin
// jvmMain
actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual class Uuid {
    private val v: java.util.UUID = java.util.UUID.randomUUID()
    actual override fun toString(): String = v.toString()
    actual companion object {
        actual fun random(): Uuid = Uuid()
    }
}
```

```kotlin
// iosMain
import platform.Foundation.NSDate
import platform.Foundation.NSUUID

actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()

actual class Uuid {
    private val v: NSUUID = NSUUID()
    actual override fun toString(): String = v.UUIDString
    actual companion object {
        actual fun random(): Uuid = Uuid()
    }
}
```

**Rules:**

- `expect` / `actual` signatures must match **exactly** — same
  nullability, generics, default arguments, and visibility.
- Default arguments live on the `expect`, not the `actual`.
- Declaring `expect` in `commonMain` means every leaf target needs an
  `actual`. If three iOS targets share the same implementation, put the
  `actual` in `iosMain`.
- `expect` in a library's public API is part of the ABI. Changing it is
  breaking.

### Prefer DI over expect/actual for ordinary logic

`expect`/`actual` is for platform primitives (clocks, crypto, file I/O,
bridges). For anything you can express as "this logic varies by
implementation", use interfaces + dependency injection instead — simpler,
testable, doesn't touch the ABI:

```kotlin
// commonMain
interface Logger { fun info(msg: String) }
class Service(private val log: Logger) { /* ... */ }

// platform code: pass a Logger impl at construction
```

### expect/actual for type aliases

You can `actual typealias` in a platform source set when the platform
already has a suitable type:

```kotlin
// commonMain
expect class AtomicInt(initial: Int) {
    fun getAndIncrement(): Int
    fun get(): Int
}

// jvmMain
actual typealias AtomicInt = java.util.concurrent.atomic.AtomicInteger
```

The JVM type must already have the signatures the `expect` requires.

## kotlin { } Gradle DSL

Library shipping JVM + iOS + Android:

```kotlin
plugins {
    kotlin("multiplatform") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("com.android.library") version "8.5.0"
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    androidTarget {
        compilations.all {
            compilerOptions.configure { jvmTarget.set(JvmTarget.JVM_17) }
        }
        publishLibraryVariants("release")
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("io.ktor:ktor-client-core:3.0.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
        }
        jvmMain.dependencies {
            implementation("org.slf4j:slf4j-api:2.0.13")
            implementation("io.ktor:ktor-client-cio:3.0.1")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.0.1")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.0.1")
        }
    }
}

android {
    namespace = "com.example.mylib"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
}
```

Notes:

- Use `implementation` **inside** `.dependencies { }`, not the top-level
  `dependencies { }` block.
- `commonMain`, `jvmMain`, etc. are properties on `sourceSets`; use the
  typed accessors, not string names (`sourceSets["commonMain"]`), unless
  you need a custom source set.
- `compilerOptions` replaces the older `kotlinOptions` DSL in Kotlin 2.0+.
- Use `kotlin("test")` in `commonTest` for multiplatform test annotations
  (`kotlin.test.Test`, `assertEquals`, etc.).

### Intermediate source sets

Sometimes you need a source set shared by a subset of targets that
doesn't match the default template. Add it explicitly:

```kotlin
kotlin {
    sourceSets {
        val jvmJsMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(jvmJsMain)
        jsMain.get().dependsOn(jvmJsMain)
    }
}
```

Prefer the default template — only add intermediate sets when you truly
have a target group the template doesn't cover.

## Compose Multiplatform

Compose Multiplatform is an optional UI layer built on KMP by JetBrains.

| Platform | Status |
|---|---|
| Android | Stable — delegates to Jetpack Compose |
| Desktop (JVM) | Stable |
| iOS | Stable (since 1.6) |
| Web (Wasm) | Beta |

Use it when you want a single `@Composable` tree across platforms. Keep
platform-specific APIs (camera, keychain, biometrics, system file picker)
behind `expect`/`actual`, and inject them at the top of the Compose
hierarchy via `CompositionLocalProvider`.

Minimum setup:

```kotlin
plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose") version "1.7.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
        }
    }
}
```

## Library compatibility checklist

Before adding a library to `commonMain`:

1. **Does it publish per-target artifacts?** Check Maven Central for
   artifacts like `mylib-jvm`, `mylib-iosarm64`, `mylib-js`. KMP
   libraries produce Gradle module metadata (`.module` files) that let
   consumers pick the right variant automatically.
2. **Does the README list targets?** Most KMP libraries declare
   "Targets: JVM, Android, iOS, macOS, JS, Wasm" prominently.
3. **Does it depend on `java.*`?** A "KMP" library whose README mentions
   Java types is probably only technically compiled to Native.

### KMP-ready libraries worth knowing

- **JetBrains official**: `kotlinx-coroutines-core`,
  `kotlinx-serialization-{json,protobuf,cbor}`, `kotlinx-datetime`,
  `kotlinx-io`, `kotlinx-atomicfu`, `kotlinx-collections-immutable`.
- **Networking**: Ktor (client + server, though server is JVM-only in
  practice).
- **Storage**: SQLDelight (typed SQL), Okio (filesystem + buffers), Room
  (KMP support since 2.7).
- **DI**: Koin, kotlin-inject.
- **State / navigation**: Decompose, Voyager, Circuit (Android-leaning).
- **Logging**: Kermit, Napier.
- **Image loading**: Coil 3 (KMP), Compose Multiplatform image APIs.

### Pure-JVM escape hatches

JVM-only libraries go in `jvmMain`, never `commonMain`. Do not try to
shim Jackson, JDBC, or Guava across Native — just accept the duplication
or pick a KMP alternative.

## Publishing

KMP libraries publish multiple artifacts plus a Gradle module metadata
file that resolves targets automatically.

```kotlin
plugins {
    kotlin("multiplatform")
    `maven-publish`
}

group = "com.example"
version = "1.0.0"

publishing {
    repositories {
        maven("https://maven.pkg.github.com/org/repo") {
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

The `kotlin("multiplatform")` plugin wires publications automatically.
You get `mylib`, `mylib-jvm`, `mylib-iosarm64`, etc. Consumers on Gradle
6.0+ with module metadata enabled resolve the right variant.

For Maven Central, use the `com.vanniktech.maven.publish` plugin or
`gradle-nexus-publish-plugin` — the native Gradle publish task doesn't
handle signed SonaType releases gracefully.

## Memory model and threading

Kotlin/Native 1.7.20+ uses the **new memory model** (GC-based, no
freeze). Rules:

- Objects can cross threads without `freeze()`.
- Mutable shared state still requires coordination — use
  `kotlinx.atomicfu` primitives or `Mutex` from coroutines.
- `@ThreadLocal` still works for per-thread state.
- Top-level `object` singletons are per-target process; do **not** rely
  on process uniqueness across an iOS framework boundary that is
  statically linked into multiple dylibs.

## kotlin.native gotchas

- `java.*` is unavailable in `commonMain`. No `java.util.Date`, no
  `java.time.*`, no `java.io.File` — use `kotlinx-datetime`, `kotlinx-io`,
  `okio`.
- `System.currentTimeMillis()` is JVM-only. Use `Clock.System.now()`
  from `kotlinx-datetime`.
- `java.util.UUID` is JVM-only. Use `kotlinx-uuid` or `expect`/`actual`.
- iOS frameworks compiled with `-Xbinary=bundleId=...` must have unique
  bundle IDs per framework or iOS rejects the app at install time.
- `@ObjCName` lets you rename Kotlin symbols in the generated
  Objective-C header — useful for Swift interop ergonomics.
- A coroutine suspension inside a `runBlocking` on the Main thread of an
  iOS app freezes the UI. Use proper async patterns on iOS.

## Expect / actual for annotations

Some annotations are platform-specific (`@JvmStatic`, `@JvmOverloads`).
Declare them `expect`:

```kotlin
// commonMain
@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
expect annotation class MyJvmStatic()

// jvmMain
actual typealias MyJvmStatic = kotlin.jvm.JvmStatic
```

`@OptionalExpectation` means non-JVM targets without an `actual` simply
drop the annotation.

## Common pitfalls

```kotlin
// WRONG: pure-JVM API in commonMain
// commonMain
import java.util.concurrent.ConcurrentHashMap  // does not compile on Native
val cache = ConcurrentHashMap<String, Int>()

// Correct: use kotlinx-atomicfu or move to jvmMain
import kotlinx.atomicfu.locks.reentrantLock
```

```kotlin
// WRONG: actual with different default argument
// commonMain
expect fun fetch(url: String, timeout: Long = 5_000): String
// jvmMain
actual fun fetch(url: String, timeout: Long): String = ...   // default moved!

// Correct: keep default ONLY on expect
actual fun fetch(url: String, timeout: Long): String = ...   // no default
```

```kotlin
// WRONG: scheduling work on GlobalScope in a KMP module
GlobalScope.launch { ... }   // leaks, non-cancellable, platform-dependent

// Correct: accept a CoroutineScope from the caller
class Service(private val scope: CoroutineScope) { ... }
```

```kotlin
// WRONG: top-level dependencies { } in kotlin-multiplatform module
dependencies {
    implementation("io.ktor:ktor-client-core:3.0.1")
}
// Correct: inside sourceSets.<name>.dependencies
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.ktor:ktor-client-core:3.0.1")
        }
    }
}
```

## References

- Multiplatform guide — https://kotlinlang.org/docs/multiplatform.html
- Default hierarchy — https://kotlinlang.org/docs/multiplatform-hierarchy.html
- expect / actual — https://kotlinlang.org/docs/multiplatform-expect-actual.html
- DSL reference — https://kotlinlang.org/docs/multiplatform-dsl-reference.html
- Dependencies — https://kotlinlang.org/docs/multiplatform-add-dependencies.html
- Compose Multiplatform — https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform.html
- Kotlin/Native memory model — https://kotlinlang.org/docs/native-memory-manager.html
- Publishing — https://kotlinlang.org/docs/multiplatform-publish-lib.html
