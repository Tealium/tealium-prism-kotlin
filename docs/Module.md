# Module Tealium Prism

A library to integrate the Tealium CDP into your Android apps.

## Requirements
Minimum OS versions:
- Android: 23.0+

## Installation

### Maven/Gradle
tealium-prism is currently available via our Maven repository. To install:

In your Android project, add the following maven repository

```kotlin
dependencyResolutionManagement {
    repositories {
        // .. other repos
        maven {
            url = URI("https://maven.tealiumiq.com/android/releases/")
        }
    }
}
```
Then add your required tealium-prism dependencies

```kotlin
implementation(platform("com.tealium.prism:prism-bom:0.6.0"))
implementation("com.tealium.prism:prism-core")
implementation("com.tealium.prism:prism-lifecycle")
implementation("com.tealium.prism:prism-context-api") 
```

And re-sync your Gradle projects

## Usage

To start using the library:

1. Import the necessary modules
2. Initialize a `Tealium` instance
3. Start tracking events.

```kotlin
import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.TealiumConfig

val config = TealiumConfig.Builder(
   accountName = "my_account",
   profileName = "my_profile",
   environment = Environment.PROD,
   modules = listOf(
        Modules.appData(),
        Modules.collect(),
        Modules.connectivityData(),
        Modules.deepLink(),
        Modules.deviceData(),
        Modules.lifecycle(),
        Modules.timeData(),
        Modules.trace(),
       )
).build()
val tealium = Tealium.create(config)
tealium.track("An Event")
```

For more advanced usage and detailed documentation, visit our [developer documentation](https://tealium.github.io/tealium-prism-kotlin/).

## Custom Modules

### Lifecycle of a Module

Modules are created, updated and shut down by settings passes. A pass runs whenever settings are loaded: at startup, and each time remote settings are downloaded. A download is attempted at launch, and when the app returns to the foreground once the refresh interval has elapsed; a download that fails, or finds the settings unchanged on the server (HTTP 304), does not run a pass. Every pass calls `updateConfiguration()` on every live module, even when its configuration has not changed. Within a pass, the modules are processed one at a time in the `order` from the settings.

- **Creation.** A module that is enabled in the settings and has no live instance is created through its `ModuleFactory.create()`. A module may track from its constructor (the Lifecycle module does): that event is collected and dispatched by the modules of the pass that is creating it, including modules created later in the same pass. It is never served by the modules of the previous pass.

- **Reconfiguration.** A module that already has a live instance receives the new configuration through `updateConfiguration()`. The return value decides what happens next:
  - `this` keeps the current instance with the new configuration.
  - `null` disables the module, and the current instance is shut down.

  Returning any other instance is not supported.

- **Shutdown.** `onShutdown()` is called exactly once on a module that leaves the list: it was disabled in the settings, it returned `null` from `updateConfiguration()`, its entry disappeared from the settings entirely, or the `Tealium` instance was shut down. Dispose subscriptions and release resources there.

### Reaching Other Modules

A module reaches the other modules through the `ModuleManager` exposed on the `TealiumContext` as `context.moduleManager`.

These lookups are asynchronous, and there is deliberately no synchronous alternative, because of *when* a module is created.
The modules of a settings pass are created one at a time, so when your module's constructor or `create()` runs, the module you are looking for may not exist yet.
A synchronous answer at that point couldn't tell "that module is not enabled" from "that module hasn't been created yet", so instead of handing back a premature `null`, these methods withhold the answer until the pass has created every module.

- **`getModuleOfType(clazz, callback)`** — a one-shot lookup, for when you need the module once, at a specific moment. The callback is called exactly once. It returns a `Disposable`, so a lookup still waiting for the modules can be abandoned, for example from your module's own `onShutdown()`.

  ```kotlin
  context.moduleManager.getModuleOfType(OtherCustomModule::class.java) { other ->
      // Called once, after the modules have been created; null means not enabled
  }
  ```

- **`observeModule(clazz)`** — the same lookup, followed over time. Use it when the module can come and go: a settings update can enable or disable it, and each change is emitted.

  ```kotlin
  context.moduleManager.observeModule(OtherCustomModule::class.java)
      .subscribe { other -> this.other = other }
      .addTo(disposables)
  ```

- **`observeModules(clazz)`** — every module matching a class or interface, in the module order from the settings. This is how to reach modules you only know through an interface, such as every `Dispatcher`.

  ```kotlin
  context.moduleManager.observeModules(Dispatcher::class.java)
      .subscribe { dispatchers -> this.dispatchers = dispatchers }
      .addTo(disposables)
  ```

Three behaviors are worth spelling out, as none is visible from the signatures:

- No module is resolved while the modules are being created or updated. A subscriber arriving mid-pass waits for that pass to complete instead of being handed the answer of the previous one.
- Once one of these has started emitting, a `null` (or an `emptyList()`) means **the module is not enabled**. It never means "not created yet". After the SDK has been shut down you get a `null` (or an `emptyList()`) as well, rather than a lookup that never resolves.
- The resolved module(s) are re-emitted only when they change by identity or order. A module reconfigured in place by `updateConfiguration()` is not re-emitted. A value that varies over time for the same module is not re-emitted — observe it through the module itself.

Never assume the other modules exist while your module is being created — resolve them through `context.moduleManager`, which withholds the answer until the modules of the current settings pass have been created.

## License

tealium-prism is available under a commercial license. See the [LICENSE](./LICENSE) file for more info.