# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## About

This is the Tealium Prism Kotlin SDK — a multi-module Android library for customer data collection, event tracking, and real-time personalization. It provides a core tracking engine alongside opt-in feature modules that extend its capabilities. Modules receive dispatched events from the core, process them according to their configuration, and can track additional events back through the shared tracker.

## Package prefix

SDK module packages are `com.tealium.prism.<module>`, not `com.tealium.<module>` (e.g. `com.tealium.prism.core`, `com.tealium.prism.lifecycle`). Two support modules break the pattern: `tests-common` is `com.tealium.tests.common` and `lint` is `com.tealium.lint` — neither has `.prism.`.

## Architecture

The SDK is organized as a multi-module Gradle project. The `core` module defines the shared interfaces and infrastructure — `Module`, `ModuleFactory`, `Dispatcher`, `TealiumContext`, `Tracker`, `DataStore`, and the pub/sub primitives (`Observable`, `Subject`, `Disposable`). Feature modules depend on `core` and implement these interfaces.

Top-level modules (authoritative list: settings.gradle.kts):
- `core` — tracking engine, dispatch pipeline, persistence, networking, settings
  - `core:core-ktx` — Kotlin extensions for core
- `momentsapi` — Moments API integration
- `jstransformer` — JavaScript evaluation for transformations
  - `jstransformer:jstransformer-rhino` — Rhino engine adapter
- `lifecycle` — app lifecycle tracking
- `extensions` — support for standard IQ extensions (e.g. Lowercasing, Persist Data Value, etc.)
- `tests-common` — shared test helpers and utilities
- `platform` — BOM for dependency management
- `lint` — custom lint rules
- `docs` — documentation
- `app` — sample/test app

Core subsystems:
- **Dispatch pipeline** — events flow through a transformer chain (`TransformerCoordinator`, `MappingsEngine`) before being handed to `Dispatcher` modules
- **Barriers** — gating mechanism that can block dispatch until conditions are met (e.g. consent, readiness)
- **Settings** — local and remote configuration management; modules receive updates via `updateConfiguration()`
- **Persistence** — database-backed stores scoped per module via `ModuleStoreProvider`
- **Pub/sub** — reactive `Observable`/`Subject`/`Disposable` primitives used throughout for inter-component communication

Module system:
- `ModuleFactory` registers a module at SDK init; `create()` is called with a `TealiumContext` providing all shared dependencies
- `Module` is the base interface; `Dispatcher` extends it for modules that participate in the dispatch pipeline
- Modules can be dynamically enabled/disabled via remote or local settings
- `allowsMultipleInstances = true` on a factory allows multiple concurrent instances (e.g. multiple Collect module instances with different endpoints/profiles)

### Settings

There are four settings sources, merged in priority order (lowest → highest):

1. **Local** — JSON file bundled in app assets
2. **Cached remote** — previously downloaded settings stored on device
3. **Remote** — downloaded from a configured URL at a set interval, refreshed on app foreground
4. **Enforced** — programmatically set via `TealiumConfig` or `ModuleFactory.getEnforcedSettings()`; always wins

Later sources override earlier ones via a deep merge. Enforced settings can never be overridden by local or remote sources — this is how `<Module>.configure { }` guarantees its enforced module configuration is always applied.

How settings reach modules:
- `SettingsManager` merges all sources and emits updates via `sdkSettings: ObservableState<SdkSettings>`
- `ModuleManagerImpl` subscribes and either calls `ModuleFactory.create()` for new modules or `module.updateConfiguration()` for existing ones
- A module returns itself from `updateConfiguration()` to stay enabled, or `null` to be disabled

Key classes:
- `SettingsManager` — merge orchestration and remote refresh
- `SdkSettings` — root container (modules, rules, transformations, barriers, consent)
- `ModuleSettings` — per-module config extracted from `SdkSettings`
- `CoreSettings` — SDK-wide settings (log level, queue size, session timeout, refresh interval)

## Common Patterns

**Data model**

`DataItem` is the SDK's universal value wrapper — it holds a single typed value (`String`, `Int`, `Long`, `Double`, `Boolean`, `DataObject`, `DataList`, or null). `DataObject` is an immutable key-value map of `DataItem`s. `DataList` is an immutable ordered collection of `DataItem`s.

Construct with the DSL:
```kotlin
val obj = DataObject.create { put("key", "value") }
val list = DataList.create { add("item") }
```

Read with typed getters — all return null if the key is absent or the type doesn't match:
```kotlin
dataObject.getString("key")
dataObject.getBoolean("key")
dataObject.getDataList("key")
dataObject.getDataObject("key")
```

**Reactive/pubsub (`core/.../api/pubsub/`)**

The SDK uses its **own** reactive primitives, **not RxJava or Kotlin Flow** — the names look Rx but
the semantics are the SDK's own, so don't assume Rx behavior. Beyond the usual operators, note the
non-obvious ones: `flatMapLatest`, `resubscribingWhile`, `distinct`, `asSingle`, and the
`StateSubject` (holds latest value) / `ReplaySubject` (caches N) distinction. `core-ktx` bridges
these to coroutines/Flow.

## Testing

Tests use JUnit 4 + MockK + Robolectric.

**Naming convention:** `<methodName>_Does_Something_When_Some_Condition`

**Schedulers:**
- Use `Scheduler.SYNCHRONOUS` for event-based tests that don't need the Android looper
- Use `@RunWith(RobolectricTestRunner::class)` + `Scheduler.MAIN` when the Android main looper is required
- `testSchedulers` from `tests-common` provides real single-threaded schedulers for integration-style tests

**Settings builder structure:**
- `ModuleSettingsBuilder.build()` nests the module config under a `"configuration"` key — use `getDataObject("configuration")` or the `buildConfiguration()` helper from `tests-common` to access it in assertions

**DRY helpers:**
- Prefer `private data class` over `Pair` for named test fixtures
- Helper/factory functions go at the bottom of test files
