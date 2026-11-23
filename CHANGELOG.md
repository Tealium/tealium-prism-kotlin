# Changelog

All notable changes to Tealium Prism for Kotlin are documented in this file. Releases are published on [GitHub](https://github.com/Tealium/tealium-prism-kotlin/releases).

## [0.8.0](https://github.com/Tealium/tealium-prism-kotlin/releases/tag/0.8.0) - 2026-11-23

### Breaking Changes
#### Commands
- `Dispatch.getCommands()` now returns normalized command names: trimmed of whitespace and lowercased, which is how commands are matched when executed. For example, `[" LogEvent ", "other"]` now gives `["logevent", "other"]`. Code that compares the returned names with their original casing or whitespace must compare against the normalized form instead. The payload's `command_name` is unchanged, and `Command`s registered with any casing still run.
#### Rules
- A rule group (`and`, `or`, `not`) with no children this SDK can convert, for example an empty group or one whose conditions all use unknown operators, is now treated as absent and dropped from its parent group, as in the Swift SDK. Review local and remote settings for such groups and remove or fix them:
  - In a load rule, an absent rule now blocks the modules that use it: their dispatchers receive no events and their collectors add no data, and a `LoadRules` warning is logged for each event. An empty `and` used to allow everything.
  - In a transformation condition, an absent condition now applies the transformation to every dispatch in its scope. An `or` with only unknown operators used to never apply.
  - Nested groups change their parent's result too. For example, `and(x, or[<unknown operators>])` used to never match and now matches whenever `x` does.
  - A module whose `rules` setting is absent isn't restricted.
  - A `not` group used to make settings fail to load instead (see **Fixes**).
---
### Additions
#### Data Model
- `JsonPathConvertible<TRoot>` is implemented by types that can be converted to a `JsonPath` whose first component is a `TRoot`. `JsonPath` implements it by returning itself, and `JsonObjectPathConvertible` extends it with `TRoot` set to `JsonPath.Component.Key`, through a default `asJsonPath()`. An implementation that already declares its own `asJsonPath()` must mark it `override`, or rename it if it returns something else.
- Failable extraction helpers on `DataObject` (`com.tealium.prism.core.api.data`, `FailableExtractionUtils` from Java) read a value by path and report what went wrong. Every helper takes the path as a `JsonPathConvertible<JsonPath.Component.Key>`, so it accepts both a type implementing `JsonObjectPathConvertible` and a `JsonObjectPath` built at runtime, such as `JsonPath["parameters"].key(name)` with `name` read from the payload. A list-rooted path (`JsonListPath`) or the untyped result of `JsonPath.parse` is rejected at compile time; `JsonPath.parseJsonObjectPath` gives an accepted one.
  - `extract` and `extractDataObject` never throw; they return `null` when the value is absent or can't be converted.
  - `optional` and `optionalDataObject` return `null` when the value is absent or an explicit `null`, and throw `TypeMismatch` on a wrong type.
  - `require`, `requireDataObject` and `requireNonBlank` throw `Missing` when the value is absent or an explicit `null`, and `TypeMismatch` on a wrong type. `requireNonBlank` also throws `Empty` for a blank value.
  - `requireDataItem` throws `Missing` only when the path is absent. An explicit `null` is returned as `DataItem.NULL`, so "set to null" can be told apart from "absent".
  - `requireOneOf` and `optionalOneOf` map a text to one of the given values, matching the trimmed text ignoring case, and throw `InvalidValue` for a text that matches none of them. For an absent, `null` or blank text, `optionalOneOf` returns `null`, while `requireOneOf` throws `Missing` or `Empty`.
  - The `require` and `optional` forms that infer the expected type name from their type argument are Kotlin-only. From Java, call the overloads that take an `expectedType`, the type name used in `TypeMismatch` messages.
- `ExtractionException`, a sealed class with `Missing`, `TypeMismatch`, `InvalidValue` and `Empty`, is what the helpers throw. Its `key` is the rendered path. The messages never include the received value.
#### Commands
- `CommandException.invalidPayload(ExtractionException)` reports a payload parameter that couldn't be read. Its message is the cause's message.
- A `Command.synchronous` block can call the extraction helpers directly: an `ExtractionException` it throws reaches the dispatcher as `CommandException.invalidPayload`.
- `Command.synchronous(commandName: CommandName, block)` overload, alongside the one taking a `String`.
- `CommandException.noValidParameters(expected)` lists the expected parameters in its message. The no-argument form is still available.
#### Rules
- `InvalidRegexException`, a new `OperatorFailedException` subclass, reports a `regular_expression` filter that isn't a valid regular expression (see **Fixes**). Code that catches `OperatorFailedException` or `ConditionEvaluationException` is unaffected.
#### PubSub
- `Observable.debounce(delay: TimeFrame, scheduler)` emits the latest value once no other value has arrived for `delay`, and `Observable.delay(delay: TimeFrame, scheduler)` emits each value after `delay`. With a delay of zero or less, both behave like `observeOn(scheduler)`. They match the operators of the same name in the Swift SDK, which take the delay in milliseconds.
---
### Deprecations
#### Commands
- `CommandException.missingParameter`, `invalidParameterType`, `emptyParameter` and `emptyArray` are deprecated. Use `CommandException.invalidPayload(…)` with the matching `ExtractionException` (`Missing`, `TypeMismatch` or `Empty`) instead, or throw the `ExtractionException` from the command. The deprecated factories keep their 0.7.0 behaviour and messages, so command-based integrations (using `CommandDispatcher`) built against 0.7.0 keep working.
#### PubSub
- `Observable.resubscribingWhile` is deprecated, with no replacement. The SDK no longer uses it, and it has known issues: a source that emits synchronously on subscription resubscribes recursively and can overflow the stack.
---
### Improvements
#### Commands
- Command failure messages changed. Only the `CommandDispatcher`'s log shows them:
  - A failure that is neither a `CommandException` nor an `ExtractionException` is wrapped in `CommandException.unknown`, with the original exception as its `cause`.
  - `CommandException.unknown(cause)` has the message "Command failed due to error: …", using the cause's message, or its class name when it has none. It used to be the cause's message alone, which could be `null`.
  - `CommandException.noValidParameters()` says "No valid parameter found." (it was "No valid parameters found"), matching the Swift SDK.
  - Command logs and `commandNotFound` errors use the normalized command name.
#### Dispatch
- A batch handed to a `Dispatcher` now holds at most 50 events, even when `dispatchLimit` is higher, matching the Swift SDK. A `dispatchLimit` below 1 is treated as 1. No code change is needed: a custom dispatcher with a higher `dispatchLimit` receives more, smaller batches. No built-in dispatcher is affected.
- A queue flush, which bypasses flushable barriers such as batching, now follows the Swift SDK's timing. A flush happens on launch, on each application status change and on `flushEventQueue()`, once per dispatcher.
  - It starts about 200 ms after the trigger, instead of immediately.
  - It ends once no events have been pending for 200 ms, or 5 s after the trigger at the latest. It used to last until the pending count reached zero, however long that took.
  - No action is needed. Events flushed by `flushEventQueue()` leave about 200 ms later, and events still pending when a flush ends are held by the flushable barriers again, such as batching.
#### Transformations
- `Transformer.applyTransformation`'s `completion` may now be called from any thread. The rest of the chain resumes on the Tealium thread, so later transformers and dispatchers are still called there. A transformer that works on other threads must return a `Disposable` that is safe to use from both the Tealium thread and its own.
- A transformation in progress is now cancelled when the dispatch loop stops, for example on `shutdown()`: the SDK disposes the `Disposable` returned by the transformer. The SDK also disposes it once the transformation has completed; as the contract already requires, that must have no effect.
#### Modules (custom module API)
- The KDoc now states the threading contract that custom modules already had to follow: modules are created and called on the Tealium thread, and `DataStore`, `DataStore.Editor` and `ModuleStoreProvider` aren't thread-safe and must be used only on the Tealium thread. `DataStore` change events notify subscribers synchronously on the thread that made the change.
#### Persistence
- Database corruption is now logged more completely. A corruption of the in-memory database the SDK falls back to is logged at error level, on Android 8.1 (API 27) and later. Corruption messages say whether the file was deleted.
---
### Fixes
#### Dispatch
- A batch could be left marked as in flight until the app was relaunched, never sent and never released, when dequeuing it changed the barrier state, typically by closing the batching barrier. In a related case, while dispatching was starting up, events kept being sent after a barrier had closed. A dequeued batch is now always handed to its dispatcher, and nothing more is dequeued once a barrier closes.
- A `dispatchLimit` of 0 caused a `StackOverflowError`, and a negative one dequeued the whole queue at once. Draining a long backlog with a small `dispatchLimit` also nested deeply on the stack. A `dispatchLimit` below 1 is now treated as 1, and a long backlog drains without deep nesting.
- After a settings refresh or a module change, events held back by the batching barrier were sent without batching. A flush that never saw the pending count reach zero, for example because a non-flushable barrier held events back, also bypassed batching indefinitely.
- With `KEEP_FIRST_VALUE`, a failure reading the stored value in the Persist Data Value transformation made `track()` and Lifecycle calls fail, lost events tracked by custom modules from other threads, and left a dispatcher-scoped batch stuck in flight until the app restarted. The error is now logged, the stored value isn't overwritten, and the event continues unchanged. Failures writing the value now include the exception message in the log.
- Whenever the device's storage couldn't be read (the platform's `StatFs` failed), the event being collected failed: `track()` returned a failure and Lifecycle events were lost. The event is now tracked without the `device_free_system_storage` or `device_free_external_storage` key that couldn't be read, and a single warning is logged.
#### Collect
- A bulk request, sent when two or more events with the same visitor ID go out together, always used the configured account and profile, even when a transformation scoped to Collect had changed the events' `tealium_account` or `tealium_profile`. An event sent on its own already kept its values. A bulk request now uses the account and profile of its first event, as in the Swift SDK. When that event has no account or profile, or an empty one, the configured value is used. A profile set with `CollectSettingsBuilder.setProfile` (`override_profile` in settings) still replaces the profile, in bulk and single requests alike. Bulk requests are still grouped by visitor ID only: if the events of one bulk request have different accounts or profiles, all of them are sent under the first event's.
#### Transformations
- A transformer that called `completion` twice ran every later transformation step again, including its side effects, and the event was stored twice. Now only the first call is used; the later ones are ignored and logged as a warning.
- A transformer that called `completion` from another thread ran the rest of the chain on that thread. When a JavaScript transformation came next, it failed with `js_error`.
#### Commands
- `CommandDispatcher` could complete a dispatch twice, or before all of its commands had finished, when two commands completed at the same time on different threads, or when a `Command` called its callback more than once.
- A `Command.synchronous` command whose completion callback threw had the callback invoked a second time, with the exception as a failure. The exception now propagates out of the command instead of being reported as a failure, and the callback runs only once.
#### Rules
- A `not` rule with no children that could be converted made the settings fail to load. In local or enforced settings, SDK initialization failed on every launch. When it arrived in a remote refresh, the refresh failed silently for the rest of the session, but the settings were cached, so initialization then failed on every later launch.
- An invalid `regular_expression` pattern in a load rule or transformation condition made `track()` fail when evaluated while tracking. When evaluated while dispatching, it could also leave a queued batch in flight until restart, or make initialization fail on later launches while an affected event was queued. The rule now counts as failed: `Condition.matches` throws a `ConditionEvaluationException` caused by an `InvalidRegexException`.
#### Data Layer and Data Store
- `Expiry.isExpired` now returns `true` as soon as no time remains (`timeRemaining() <= 0`), and the SDK deletes data expiring at the current second, matching the reads, which already hid it. An `Expiry.afterEpochTime` before 1970 (a negative epoch) now counts as already expired, so storing a value with it is handled as described in the next entry. It used to be stored as if it never expired: the value stayed readable and was never deleted, and `-2` and `-3` were read back as `SESSION` and `UNTIL_RESTART`.
- Storing a value with an `Expiry` that has already passed now removes any existing value at that key and reports the removal through `onDataRemoved`. It used to overwrite the value with one that could never be read, reported through `onDataUpdated`. Reads are unaffected; a listener that tracks removals should observe `onDataRemoved`.
- `onDataUpdated` and `onDataRemoved` could disagree with what was stored. A key both put and removed in the same transaction appeared in both events, and a remove followed by a put reported a stored key as removed. Each key is now reported once per write, by its final edit, so the events match the stored state.
#### Persistence
- A failed database transaction now reports the error that caused it as the cause of the `PersistenceException`, whatever the failure. A failure to end or roll back the transaction afterwards, for example because a corruption had closed the database, is attached to it as a suppressed exception instead of replacing it.
#### Session
- If session-scoped data couldn't be deleted (for example, on a full disk), SDK initialization failed. When this happened as a session expired, the failure was ignored silently and some `SessionRegistry.session` subscribers never received the `Ended` status. The failure is now logged at error level, the session still ends, and the data stays until a later cleanup succeeds.
#### Deep Link
- With automatic deep-link tracking, a link whose intent carried extras the app couldn't read (for example, a `Parcelable` class it doesn't have) wasn't handled, and again each time the Activity was re-created. The link is now handled without a referrer, and a warning is logged.
#### PubSub
- With `StateSubject`, a value or completion emitted into the subject while a new subscriber handles its first value now reaches that subscriber after its callback returns, instead of nested inside it. Other subscribers still receive it right away. This matches `ReplaySubject` (see the next entry).
- A `ReplaySubject` subscriber that emitted into the subject while its cached values were being replayed could make `subscribe` throw `ConcurrentModificationException`. The remaining cached values were not replayed, and the subscriber stayed registered without a `Disposable`. The subscriber now receives every cached value, then the values emitted during the replay. A completion emitted during the replay arrives after it.
- `ReplaySubject.resize` didn't shrink a cache holding a `null` value, so more values than the new size were replayed.
#### JavaScript Transformer
- JavaScript transformations that use regular expressions (regex literals, `RegExp`) or typed arrays (`ArrayBuffer`, `Uint8Array`, `DataView` and the like) failed in apps minified with R8. The event was sent without the transformation's changes and with a `js_error` key. They now work: the Rhino module's consumer ProGuard rules keep the members Rhino looks up by reflection. This affected every release since 0.5.0. No app-side change is needed.
---
### Known Issues
#### JavaScript Transformer
- Apps that include `prism-js-transformer-rhino` and have a `minSdk` below 26 must enable core library desugaring (`desugar_jdk_libs` 2.1.5 or later). Without it, Tealium fails to initialize on Android 6.0 to 7.1 (API 23 to 25), even if no JavaScript transformation is configured. This affects every release since 0.5.0; the requirement and the Gradle setup are now documented in the README, under Installation › JavaScript transformations.
---
**Full Changelog**: [`0.7.0...0.8.0`](https://github.com/Tealium/tealium-prism-kotlin/compare/0.7.0...0.8.0)

## [0.7.0](https://github.com/Tealium/tealium-prism-kotlin/releases/tag/0.7.0) - 2026-10-16 - General Availability

### Breaking Changes
#### Context API (formerly Moments API)
- The Moments API module is renamed to Context API, following the product rename. Regions and endpoints are unchanged.
  - Artifact: `com.tealium.prism:prism-moments-api` → `com.tealium.prism:prism-context-api`.
  - Package: `com.tealium.prism.momentsapi` → `com.tealium.prism.contextapi`.
  - Symbols: `MomentsApi` → `ContextApi`, `MomentsApiRegion` → `ContextApiRegion`, `MomentsApiSettingsBuilder` → `ContextApiSettingsBuilder`, `MomentsApiConfigurationException` → `ContextApiConfigurationException`, `Modules.momentsApi(…)` → `Modules.contextApi(…)`, `Modules.Types.MOMENTS_API` → `Modules.Types.CONTEXT_API`, `Tealium.momentsApi` → `Tealium.contextApi`. `EngineResponse` keeps its name but moves to the new package. For Java, `MomentsApiKt` → `ContextApiKt`.
  - Settings: the module key and `module_type` `"MomentsAPI"` are now `"ContextAPI"`, and `moments_api_region` / `moments_api_referrer` are now `context_api_region` / `context_api_referrer`. Update local and remote settings; the old keys no longer configure the module.
- `ContextApi.fetchEngineResponse` now fails with `ModuleNotEnabledException` when the VisitorId module is disabled, since the request needs the visitor ID. A fetch still pending when the module shuts down fails with `ModuleShutdownException`.
#### Visitor ID
- Visitor ID handling moved into a new VisitorId module (see **Added**).
  - `Tealium.resetVisitorId()` → `Tealium.visitorId.regenerate()`.
  - `Tealium.clearStoredVisitorIds()` → `Tealium.visitorId.eraseLocalHistory()`.
  - `TealiumContext.visitorId` is removed. Custom modules should look up `VisitorIdProvider` through the `ModuleManager`, e.g. `context.moduleManager.getModuleOfType(VisitorIdProvider::class.java) { … }`, and read its `visitorId`. It resolves to `null` when the VisitorId module is disabled.
- `CoreSettingsBuilder.setVisitorIdentityKey()` and `CoreSettings.visitorIdentityKey` are removed. Configure the key on the VisitorId module instead: `Modules.visitorId { it.setVisitorIdentityKey("email") }`.
  - In JSON settings, move `visitor_identity_key` from `core` to `modules.VisitorId.configuration.visitor_identity_key`. A key left under `core` is ignored, which turns Visitor Switching off.
#### Consent
- Consent integration isn't available in this release; it will return in a later one. In the meantime, gate consent for Prism events server-side in EventStream.
  - `TealiumConfig.Builder.enableConsentIntegration(…)` and `TealiumConfig.cmpAdapter` are no longer public.
  - `CmpAdapter`, `ConsentDecision` and `ConsentConfigurationBuilder` remain public but have no effect, because no adapter can be registered.
  - A `consent` section in local or remote settings is ignored, and an info message is logged.
#### Data Layer
- `DataLayer.put(DataObject)` and `DataLayer.put(DataObject, Expiry)` are renamed to `DataLayer.putAll(…)`, for consistency with `DataStore.Editor.putAll`. The parameter is now `dataObject` (it was `data`), so Kotlin callers using the named argument need to update it too. The keyed `put(key, …)` overloads are unchanged.
#### Payload
- `tealium_session_id` (`Dispatch.Keys.TEALIUM_SESSION_ID`, `Session.sessionId`) is now the session start time in **milliseconds** instead of seconds, consistent with Tealium's other SDKs. A session already in progress when the app updates keeps its seconds-based id until it ends. Update any server-side rules, enrichments or reports that assume seconds.
#### Mappings
- Dispatcher mappings are now applied on top of the full event payload instead of building a new payload from scratch. Unmapped keys pass through unchanged, and a mapped destination replaces the existing value there.
  - To remove unwanted keys before they reach a dispatcher, use a dispatcher-scoped transformation (these run before mappings).
- `Mappings.keep(…)` and `CommandMappingsBuilder.keep(…)` are removed, since they are now redundant. Delete the calls. JSON settings are not affected.
- Mapped `command_name` values are the exception to "replace": both constant and variable mappings are appended to any commands already in the payload (lists are flattened one level). Previously, multiple constant mappings to *any* destination accumulated into a list; for every other destination the last one now wins.
#### Modules (custom module API)
- `ModuleManager.modules` is removed. Use `observeModules(Module::class.java)`, or the class or interface you actually need.
- The synchronous `ModuleManager.getModuleOfType(Class<T>): T?` is removed. Use `getModuleOfType(clazz) { module -> … }` or `observeModule(clazz)`.
- `ModuleManager.observeModule(Class<T>, transform)` is removed. Use `observeModule(clazz).flatMapLatest { … }`.
- `ModuleManager.getModuleOfType(Class<T>, Callback<T?>)` now returns a `Disposable`, so a pending lookup can be cancelled. Callers are source-compatible but must recompile.
- `TealiumContext` can no longer be constructed directly.
- New abstract members `Tealium.visitorId` and `ModuleManager.observeModules` only affect custom implementations of these interfaces, such as hand-written test fakes.
- `ModuleProxy` has a new member, `observeModuleResult` (see **Added**). Only custom `ModuleProxy` implementations are affected; proxies from `createModuleProxy` aren't.
#### Transformations
- `Transformer.applyTransformation` now takes a `Callback<Dispatch?>` instead of a `(Dispatch?) -> Unit` and returns a `Disposable`. Custom `Transformer`s must:
  - call `completion.onComplete(…)` instead of invoking `completion(…)`;
  - return a `Disposable`, or `Disposables.disposed()` if there is nothing to cancel (e.g. synchronous transformers).

  Kotlin callers passing a trailing lambda compile unchanged. The KDoc now documents the contract: `completion` is called exactly once on the Tealium thread (possibly synchronously), unless the returned `Disposable` has been disposed.
#### JavaScript Transformer
- `JavaScriptEngineAdapter.evaluateJavaScript(js): Observable<TealiumResult<DataItem>>` is now `evaluateJavaScript(js, completion: Callback<TealiumResult<DataItem>>): Disposable`. Custom engine adapters must deliver exactly one result through `completion.onComplete(…)` and return `Disposables.disposed()` if there is nothing to cancel. The bundled Rhino adapter (`jstransformer-rhino`) is already updated.
#### Networking
- `context.network.networkClient` and `networkHelper` now make a single attempt per request. Previously, while the device was offline, a failed request waited for connectivity and was retried. Custom modules that need retries derive their own client:
  ```kotlin
  context.network.networkClient
      .newClient(listOf(
          RequestInterceptors.retryWithBackoff(),
          RequestInterceptors.retryWhenConnected(context.network)
      ))
      .makeNetworkHelper()
  ```
- `NetworkClient` is now immutable: `addInterceptor` and `removeInterceptor` are removed in favour of `newClient(additionalInterceptors, maxRetryCount)`. Custom `NetworkClient` implementations must implement `newClient`.
- `Interceptor` is renamed to `RequestInterceptor`.
  - `didComplete(request, result)` → `onAttemptResult(request, retryCount, result)`. It is called after every attempt and has a default no-op implementation.
  - `shouldRetry(request, result, retryCount)` → `shouldRetry(request, consecutiveRetryCount, result)`. Note the new parameter order.
- `RetryPolicy` changes:
  - `RetryPolicy.RetryAfterDelay(interval: Long)` → `RetryPolicy.AfterDelay(delay: TimeFrame)`.
  - `RetryPolicy.RetryAfterEvent<T>(event)` → `RetryPolicy.AfterEvent(event: Observable<*>)`.
  - `RetryPolicy.shouldRetry()` is removed; check `policy !is RetryPolicy.DoNotRetry` instead.
- `NetworkException.Non200Exception.isRetryable()` is now true only for 408, 429, 500, 502, 503 and 504. It used to be true for 429 and any status from 500 to 600, so 501 and 505+ are no longer retryable, while 408 now is.
#### Java Interop
- `LenientConverters.DOUBLE`, `INT`, `LONG`, `BOOLEAN` and `STRING` are now `@JvmField`. Java callers should change `LenientConverters.INSTANCE.getDOUBLE()` to `LenientConverters.DOUBLE`.
- `Scheduler.MAIN` and `Scheduler.SYNCHRONOUS` are now `@JvmField`. Java callers should change `Scheduler.getMAIN()` to `Scheduler.MAIN`.
- `Disposables.subscription`, `composite` and `disposed`, and `Barriers.connectivity` and `batching`, are now `@JvmStatic`. Java callers should change `Disposables.INSTANCE.disposed()` to `Disposables.disposed()`. Existing Java source still compiles (with a warning), but binaries compiled against 0.6.0 must be recompiled.
- Renamed Java file facades: `DispatchCommandsKt` → `DispatchCommandUtils`, and `ConversionsKt` → `ConversionUtils`.
- These parameters now use SDK functional interfaces instead of Kotlin function types, so Java callers no longer need to return `Unit.INSTANCE`. Kotlin lambdas compile unchanged.
  - `Observable.forEach`: `Consumer<T>`
  - `Command.synchronous`: `Consumer<DataObject>`
  - `Disposables.subscription`: `Runnable?`
  - `ModuleProxy.executeAsyncModuleTask`: `BiConsumer<T, Callback<TealiumResult<R>>>`
- Libraries and apps compiled against 0.6.0 that use any of the symbols above, from Kotlin or Java, must be recompiled.
---
### Added
#### Visitor ID
- `Tealium.visitorId` returns a `VisitorIdManager`, with `current()`, `regenerate()` and `eraseLocalHistory()`.
  - It is also a `Subscribable<TealiumResult<String>>`: it emits the current visitor ID, then every change.
  - While the VisitorId module is disabled it emits `ModuleNotEnabledException` failures, then resumes once the module is re-enabled.
  - As before, if a new visitor ID can't be stored, `regenerate()`, `eraseLocalHistory()` and automatic identity switches still use it for the rest of the session. The call fails with `PersistenceException`, and the previous ID comes back on the next launch.
- New VisitorId module (`Modules.visitorId()`, `Modules.Types.VISITOR_ID`, `VisitorIdSettingsBuilder`). It is added automatically and is now what adds `tealium_visitor_id` to events. Like other collectors it can be disabled through settings, in which case events carry no `tealium_visitor_id`.
- `VisitorIdProvider` (`com.tealium.prism.core.api.modules`) gives custom modules the visitor ID as an `ObservableState<String>`.
#### Modules
- `ModuleManager.observeModules(Class<T>)` emits every enabled module matching a class or interface, in settings `order`.
- `ModuleProxy.observeModuleResult(transform)` gives a state stream as `TealiumResult`s: success values while the module is enabled, and `ModuleNotEnabledException` failures while it isn't. It emits a single failure and completes if `Tealium` fails to initialize or shuts down.
- `ModuleShutdownException` reports a module operation that couldn't finish because a settings update shut the module down.
- Java callers can call `Tealium.shutdown(tealium)` statically.
#### Networking
- `RequestInterceptors` provides the built-in retry interceptors:
  - `retryWithBackoff(…)` retries with jittered exponential backoff. You can configure the maximum attempts, the base delays for transient failures and for throttling, the maximum delay, and the `RetryAfterAboveMaxDelay` behaviour. `Retry-After` is honoured as a minimum wait.
  - `retryWhenConnected(connectivity)` retries once connectivity returns.
- `RetryAfterAboveMaxDelay` (`GIVE_UP`, `WAIT`) controls what happens when a `Retry-After` header is longer than `maxDelay`.
- `NetworkClient.makeNetworkHelper()` wraps any client in a `NetworkHelper`.
- `NetworkUtilities.resourceRefresher(…)` has a new overload taking a `NetworkHelper`, so a refresher can fetch through a retrying client.
#### JavaScript Transformer
- Public `JavaScriptTransformer` interface (it extends `Transformer`). Engine adapters should use it as the source module of the events they track: `DispatchContext.Source.module(JavaScriptTransformer::class.java)`.
#### Data Model
- `DataObject.Builder` now implements `DataItemExtractor`, so the typed getters (`getString`, `getDataList`, …) work on a builder.
- `DataItem.parse(String)` is now `@JvmStatic`.
---
### Changed
#### Visitor ID
- Existing visitor IDs and Visitor Switching history carry over. On the first launch after updating, the database is upgraded and the visitor data moves into the VisitorId module's own storage.
  - Visitor data is no longer in `ModuleStoreProvider.getSharedDataStore()`. Read it through `VisitorIdProvider` or `Tealium.visitorId` instead.
- `eraseLocalHistory()` erases only visitor ID data. `clearStoredVisitorIds()` used to wipe the whole shared store, which also held session data and cached remote settings. If an identity is still present in the data layer, it is associated with the new visitor ID right away.
#### Modules
- `ModuleManager` lookups wait while a settings pass is creating or updating modules, so a module that is about to be enabled is no longer treated as disabled during startup. `observeModule`, `observeModules` and `getModuleOfType` emit nothing until the pass completes, so `null`/`emptyList()` always means "not enabled", never "not created yet". They re-emit only when the resolved instances change, not when a module is reconfigured in place.
- `ModuleProxy` streams now complete when `Tealium` fails to initialize or shuts down, instead of staying open. When `Tealium` failed to initialize, `executeModuleTask` and `executeAsyncModuleTask` now fail with the actual initialization error instead of `TealiumShutdownException`.
#### Dispatch
- A `track` made while no `Dispatcher` is enabled now completes with `TrackResult.dropped`. Previously it reported accepted, but the event was never sent.
- `Dispatch.getCommands()` removes duplicate commands, comparing them after trimming and lowercasing. Each command runs once even if `command_name` repeats it.
#### Networking
- Collect, remote settings and Context API now retry on their own clients:
  - Collect retries retryable failures indefinitely, with jittered backoff capped at 150 s, waits a longer `Retry-After` in full, and waits for connectivity while offline.
  - Remote settings makes up to 3 attempts in a row with backoff capped at 2 s, and at most 10 retries per fetch. It waits for connectivity while offline, and after that the existing refresh cooldown applies.
  - Context API makes up to 3 attempts with backoff capped at 20 s, and no longer waits for connectivity while offline.
  - Collect and remote settings now also retry while the device is online, on HTTP 408, 429, 500, 502, 503 and 504 and on I/O errors.
- Cancelling a request takes effect immediately. Disposing it completes it with `CancelledException` exactly once, cancels any pending retry wait, and aborts the in-flight connection (best-effort). Cancellations are logged at `DEBUG` instead of `ERROR`.
#### Connectivity
- `wifi_only` (`ConnectivityBarrierSettingsBuilder.setWifiOnly(true)`) now holds dispatching back only on a connection known to be cellular, matching the Swift SDK. Wi-Fi, Ethernet and unknown connection types are allowed, and a VPN counts as the network underneath it. Previously, anything other than Wi-Fi or Ethernet was blocked.
- The connectivity barrier and `retryWhenConnected` now treat `Connectivity.Status.Unknown` as connected, so only `NotConnected` holds dispatching back. `Connectivity.isConnected()` still returns `false` for `Unknown`.
- An app cold-started with no network now reports `NotConnected` (`connection_type: "none"`) instead of `Unknown`.
#### Persistence
- The SDK database now uses write-ahead logging (WAL) with `synchronous = NORMAL`. This greatly reduces disk syncs when many events are queued.
  - Events still survive the app being killed; at most the latest writes can be lost on an OS crash or power loss.
  - The database now has `-wal` and `-shm` companion files next to the `.db`.
  - The log is checkpointed when the app goes to the background and on `shutdown()`.
  - If WAL can't be enabled, an error is logged and the database keeps its previous journal mode.
- The database schema is upgraded on first launch. Downgrading to 0.6.0 afterwards deletes and recreates the database, losing the visitor ID, queued events and stored data.
#### Data Model
- `DataLayer.putAll` and `DataStore.Editor.putAll` now store `DataItem.NULL` values, as `put` does, instead of silently dropping them. A bulk write can now overwrite a value with `null`. This matches the Swift SDK.
- `DataList.Builder.addAll(list, index)` now appends when `index` is past the end, as `add` does, instead of throwing `IndexOutOfBoundsException`. Building `DataList`s is also cheaper, which roughly halves the CPU cost of reading queued events.
#### Logging
- Log categories changed, so update any `LogHandler` filters that match on them: `"VisitorIdProvider"` is now `"VisitorId"`, Context API logs use `"ContextAPI"` instead of `"MomentsAPI"`, and `"Connectivity"` and `"Persistence"` are new.
#### PubSub
- `Observable.distinct(isEquals)` compares each value with the previous upstream value, even when that value was suppressed. This matches RxJava's `distinctUntilChanged` and the Swift SDK, and only matters for comparers that aren't transitive.
- `Scheduler.MAIN` no longer touches the main `Looper` until it is first used, so it can be referenced in plain JVM unit tests.
---
### Fixed
#### Dispatch
- Each settings change that changed the module list gave every `Dispatcher` an extra dequeue loop. Queued events were pulled faster than intended, a `Dispatcher` disabled through settings kept sending events already queued for it, and disabled `Dispatcher`s stayed in memory until shutdown.
- If an event couldn't be written to the queue, for example because of a database error, `track` reported accepted even though the event was lost. It now reports `TrackResult.dropped` ("Failed to persist dispatch to the queue.") and logs the error.
- With Collect batching, events without a `tealium_visitor_id` (e.g. removed by a transformation or mapping) were discarded without being sent. They are now sent.
#### Mappings
- Adding mappings to Collect removed required keys such as `tealium_account` and `tealium_profile`, so requests were rejected with HTTP 400.
- `"mappings": []` sent an empty payload; it now leaves the payload unchanged.
- An index mapping such as `a[2]` replaced the whole array in the payload (e.g. sending `[null, null, 3]`).
#### Modules
- Modules removed entirely from the settings were never shut down, so `Module.onShutdown()` wasn't called and their subscriptions kept running. They are now shut down like disabled modules. Likewise, when `updateConfiguration()` returned a different instance, the old one was never shut down; it now is (returning a different instance is documented as unsupported).
- During a settings refresh, an event tracked from a module's constructor or `updateConfiguration()` (e.g. by Lifecycle) could be processed against the previous settings' modules.
- On shutdown, observers could briefly receive `ModuleNotEnabledException` (or `null`) before the shutdown error.
- If a module was disabled while an `executeAsyncModuleTask` was running, the task's result could be replaced with `ModuleNotEnabledException`.
- Calling `shutdown()` on a stale `Tealium` instance could shut down a newer instance created later with the same key.
#### Visitor ID
- With Visitor Switching on, resetting the visitor ID after the identity was removed from the data layer still linked the new ID to that identity. The user's original visitor ID was lost, and a different user logging in next got a new visitor ID instead of taking over the anonymous one.
#### Deep Link
- `Tealium.deepLink.handle(…)` for a link carrying a trace id could fail with `ModuleNotEnabledException` while modules were still loading. It now waits until they are ready.
#### Extensions
- The Persist Data Value transformation could complete a dispatch twice when a later step threw an exception, which logged a misleading "Failed to persist data value" error.
#### JavaScript Transformer
- In apps minified with R8/ProGuard, JavaScript transformations failed as soon as a script used the injected `dataLayer`, `tracker`, `console` or `Expiry` helpers. `jstransformer-rhino` now ships the missing consumer keep rules, so no app-side change is needed.
- Completed JavaScript evaluations are now released immediately, instead of being held until the transformer shut down.
#### Persistence
- A corrupted database file that Android couldn't recover by itself could leave the SDK on in-memory storage on every launch, so nothing was ever persisted. A corrupted file, or one with a schema version this SDK can't use, is now deleted and recreated once. Other failures, such as a full disk or a locked file, still fall back to in-memory storage for that session and keep the file.
#### Connectivity
- On Android 6 (API 23), moving from cellular to Wi-Fi could leave the status `NotConnected` on a working network, holding dispatching back.
- A VPN connection now reports the type of the network underneath it instead of unknown.
- SDK initialization no longer fails if the network callback can't be registered. The status becomes `Unknown` and the failure is logged.
#### Data Model
- Setting an existing list index through a path (`buildPath` on `DataObject`, `DataList`, their builders, and `DataStore`) inserted a new element and shifted the others, instead of replacing the value.
#### PubSub
- `Observable.distinct()` emitted `null` again every time the upstream repeated it. As a result, `observeModule(…)` re-emitted `null` whenever a settings pass changed the module list while the module stayed disabled.
---
**Full Changelog**: [`0.6.0...0.7.0`](https://github.com/Tealium/tealium-prism-kotlin/compare/0.6.0...0.7.0)

## [0.6.0](https://github.com/Tealium/tealium-prism-kotlin/releases/tag/0.6.0) - 2026-09-01

### Breaking Changes
#### Core API
- `Tealium.deeplink` renamed to `Tealium.deepLink`, for consistency with the rest of the API.
- `TemplateProcessor.process(String, DataObject)` is now `@JvmStatic`. Kotlin callers are unaffected. Java callers should change `TemplateProcessor.INSTANCE.process(…)` to `TemplateProcessor.process(…)`.
#### Logging
- `LogLevel.Converter.convert` returns `LogLevel?` now, instead of falling back to `ERROR` for anything it couldn't parse. This only breaks you if you call the converter yourself and treat its result as non-null. Log levels set through settings behave as before, since that path already applied its own `ERROR` default.
#### Networking
- `NetworkHelper`'s POST methods no longer gzip the payload; they always send it uncompressed.
  - If you need compression, build the request with `HttpRequest.post(...).gzip(true)` and send it through `NetworkClient`.
#### Consent
- `CmpAdapter` implementations now have a documented threading contract: emit consent decisions from a single thread, and apply `subscribeOn` to the returned `Observable` with a scheduler backed by that same thread. Emitting from anywhere else races.
---
### Added
#### New Modules
- **`com.tealium.prism:prism-core-ktx`** module — Kotlin coroutine interop for core's pubsub primitives.
  - `Subscribable.asFlow()` and `SubscribableState.asStateFlow(scope)`
  - `Single<TealiumResult<T>>.await()` and `awaitResult()`
#### Core API
- `TemplateProcessor.compile(String)` returns a reusable `Template` with its `{{ }}` substitutions already resolved, which is worth using if you render the same template more than once. `Template.substitutions` lists what it found.
- `ModuleProxy.observeModules` observes all instances of a multi-instance module together, reacting whenever that set changes.
- `Dispatch.getCommands` is now public. It returns the command names carried on a dispatch's payload, for `Dispatcher`s that need to act on specific commands.
#### Networking
- `NetworkException` now carries the `HttpResponse` that preceded the failure, where there was one, so you can inspect the status and headers, plus the body if it could be read. Available on `Non200Exception`, `NetworkIOException` and `UnexpectedException`.
- `NetworkClient.sendRequest` has a new overload taking an unbuilt `HttpRequest.Builder`. An invalid URL arrives as a `Failure` in `completion` instead of throwing `MalformedURLException`.
#### Settings
- `log_level` now accepts `"none"` as an alias for `"silent"`.
---
### Changed
#### Networking
- Response headers and body are now included in `TRACE` logging, for both successful and failed requests.
- A failure while reading a response body now reports the status and headers that arrived before it failed, rather than just the `IOException`.
#### PubSub
- Documented the concurrency contract for the `Observable` returned by `subscribeOn`: nothing but `observeOn` can be chained onto it.
#### Build
- Build tooling upgraded — AGP 8.6.1 to 8.13.2, compileSdk and targetSdk 35 to 36, and `androidx.annotation` raised to 1.10.0.
- Kotlin Gradle Plugin is now 2.2.21. The language version stays at 2.0, so this doesn't change what Kotlin version you need.
---
### Fixed
#### Dispatch
- Dispatches could be sent more than once. When a `Dispatcher` completed its dispatches in more than one batch, only the first batch was removed from the queue; the rest stayed on disk and went out again on the next app launch, unless they expired or got trimmed first.
#### PubSub
- After disposal, `subscribeOn` and `observeOn` could still deliver values or completion, and `subscribeOn` could still subscribe to its source.
- `ReplaySubject.last()` read the cache unlocked, so a concurrent emission could corrupt it.
#### Networking
- `gzip(false)` was ignored and the request got compressed anyway. Omitting the call was the only way to send uncompressed.
- Network requests now have 30 second connect and read timeouts. They previously had none, so a request could hang indefinitely.
- If you added your own `Interceptor`, the completion callback for a network request could be called more than once.
#### Logging
- An enforced log level that couldn't be parsed pinned the logger to `ERROR` for the whole session and ignored the configured level. It's treated as no override now.
---
**Full Changelog**: [`0.5.0...0.6.0`](https://github.com/Tealium/tealium-prism-kotlin/compare/0.5.0...0.6.0)

## [0.5.0](https://github.com/Tealium/tealium-prism-kotlin/releases/tag/0.5.0) - 2026-06-01

### Breaking Changes
#### Settings
- `BarrierScope` and `TransformationScope` `dispatcher` value renamed to plural form and now accepts an array of Dispatcher IDs.
  - `BarrierScope.dispatchers(listOf("Dispatcher1", "Dispatcher2"))`
  - `TransformationScope.dispatchers(listOf("Dispatcher1", "Dispatcher2"))`
- `BarrierSettings.scope` and `TransformationSettings.scope` are now singular (one scope per setting, not a collection).
- `TealiumConfig.addTransformation()` now accepts a `TransformationSettingsBuilder` directly instead of a built `TransformationSettings`. Remove any `.build()` call at the call site.
#### PubSub
- `Observer` now requires both `onNext` and `onComplete` to be implemented. Observers that have been disposed will no longer receive events.
- `Subscribable.subscribe` and `Observable.subscribe` now accept either an `Observer` to handle both `onNext` and `onComplete` or a `Consumer`-only overload is also available for cases where completion does not need to be handled.
- Unused operators `Observable.buffered()` and `Observable.share()` have been removed.
---
### Added
#### New Modules
- **`com.tealium.prism:prism-extensions`** module, including:
  - **`Lowercase`** — converts string values in the dispatch payload to lowercase. Configurable to apply to individual variables, a defined set, or all variables.
  - **`SetDataValues`** — copies values between keys or sets constant values in the dispatch payload.
  - **`PersistDataValue`** — stores a value from the dispatch payload (or a constant) into the data layer with a configurable expiry and update policy.
- **`com.tealium.prism:prism-js-transformer`** and **`com.tealium.prism:prism-js-transformer-rhino`** modules — perform user-defined JavaScript-powered transformations. The `JavaScriptEngineAdapter` interface allows custom engine implementations; a Mozilla Rhino adapter is provided out of the box. **note** the Rhino implementation is supported from API 23+ but requires core library desugaring to be enabled in the app.
#### Settings
- `TransformationSettings` gains an `order: Int` property (default `Int.MAX_VALUE`); lower values run first when multiple transformations share the same scope.
#### Commands
- `Command` infrastructure, laying the foundation for future modules that are commanded by tracking events (previously known as `RemoteCommands`). Includes: `Command`, `SyncCommand`, `CommandDispatcher`, `CommandName`, `CommandMappingsBuilder`, and `CommandException`.
---
### Fixed
- Fixed `Observable` operators and subjects delivering events to observers that had already been disposed.
- Fixed incorrect use of Kotlin's enum `.name` property in `mapCommand()` that could produce unexpected behaviour when mapping commands.
---
**Full Changelog**: [`0.4.0...0.5.0`](https://github.com/Tealium/tealium-prism-kotlin/compare/0.4.0...0.5.0)

## [0.4.0](https://github.com/Tealium/tealium-prism-kotlin/releases/tag/0.4.0) - 2026-02-27

### Breaking Changes

- Changed barrier configuration methods to support their programmatic configuration.
- `DispatcherSettingsBuilder` now requires a `Mappings` supplier to better support custom mappings methods, with `Mappings.default()` available to modules that don't need any custom mappings methods.
  - Some `Mappings` method names updated for clarity
- Changes to the Module/Barrier Metadata prefixes required by the discovery service. Prefixes now correctly match the packages:
   -  `com.tealium.prism.core.api.barriers` and `com.tealium.prism.core.api.modules`

### Added
- Bill of Materials (BoM) to enable importing a single version number that will match Github Tags
- Added programmatic `Barrier` configuration.
- Some utility `LenientConverters` that can be used in `Dispatcher`s to extract data from the payload that could be either of the supported primitive types.
- `Tealium.onReady` that accepts a callback, which can be used to execute multiple `Tealium` operations in a single block.

### Changed
- `Mappings` now support mapping constants of any supported `DataItem`, instead of just `Strings`.


**Full Changelog**: https://github.com/Tealium/tealium-prism-kotlin/compare/0.3.0...0.4.0

## [0.3.0](https://github.com/Tealium/tealium-prism-kotlin/releases/tag/0.3.0) - 2026-01-22

### First Early Access Release

Tealium Prism for Kotlin is now available for early access under the Maven group ID `com.tealium.prism` from Tealium's [Maven repository](https://maven.tealiumiq.com/android/releases/).

#### `prism-core`

The core module defines the common features required for setting up the Tealium Prism SDK. It contains several of the most commonly used module implementations for enriching events and distributing them to other Tealium services.

##### Features

- **Customizable** – fully modular, with features that can be remotely configured, enabled, or disabled
- **Event Tracking** – reliably distributes events to all configured dispatchers
- **Consent Management** – supports external consent signals and restrictive distribution per dispatcher
- **LoadRules** – only send relevant events to the appropriate dispatcher
- **Collectors** – enrich events with common data points
- **Transformations** – mutate event data, and can optionally drop events
- **Barriers** – provide fine-grained control over the flow of events to individual dispatchers

##### Included Modules

- **AppData** – enriches events with data about the application
- **Collect** – distributes events to Tealium's server-side platform for further processing
- **ConnectivityData** – enriches events with connectivity status data
- **DataLayer** – stores user data that should be added to all tracked events
- **DeepLink** – enriches events with data provided by deep links
- **DeviceData** – enriches events with data about the device
- **TealiumData** – enriches events with data specific to the Tealium account and library configuration
- **TimeData** – enriches events with time data, such as event timestamps and time zones
- **Trace** – manages a Trace session to easily debug the library

#### `prism-lifecycle`

The lifecycle module provides application lifecycle event tracking. It can automatically track `launch`, `wake`, and `sleep` events, which coincide with the application starting, returning to the foreground, and going to the background.

Additional lifecycle metrics are captured by default to help build a comprehensive picture of application usage.

#### `prism-moments-api`

The Moments API module provides support for real-time visitor personalization using Tealium's server-side [Moments API](https://docs.tealium.com/server-side/moments-api/) technology.

**Full Changelog**: https://github.com/Tealium/tealium-prism-kotlin/commits/0.3.0
