# Module core

## Visitor ID

The VisitorId module is a `Collector` module that owns the anonymous visitor ID: it creates and
persists it, adds it to every tracking event, and can switch it automatically when an identity
stored in the data layer changes (Visitor Switching). It is enabled by default. Apps read and
observe the visitor ID through `tealium.visitorId`, a `VisitorIdManager`. Other modules read it
synchronously through the `VisitorIdProvider` interface, obtained from the `ModuleManager`.

### Collected data points

| Data Point | Key | Description | Source |
|------------|-----|-------------|--------|
| Visitor ID | `tealium_visitor_id` | The anonymous visitor identifier for this device | VisitorId module |

### Configuration

**Local and remote settings**

The module is enabled by default, so no settings are needed to use it. When it is added with
`Modules.visitorId(null)` (see Settings-driven initialization below), it is only created when local
or remote settings configure it. In that case, to rely on the defaults, declare it with its module
type only:

```json
{
    "modules": {
        "VisitorId": {
            "module_type": "VisitorId"
        }
    }
}
```

To customize it, add a `configuration` block and any common module settings:

```json
{
    "modules": {
        "VisitorId": {
            "module_type": "VisitorId",
            "enabled": true,
            "order": 1,
            "rules": {
                "operator": "and",
                "children": [
                    "rule_id_from_settings"
                ]
            },
            "configuration": {
                "visitor_identity_key": "email"
            }
        }
    }
}
```

When both local and remote settings are provided, they are deep merged with remote settings taking
priority.

**Programmatic configuration**

Add the module to the `modules` list of your `TealiumConfig`. Without an argument it is initialized
with its default settings:

```kotlin
// Kotlin
val config = TealiumConfig.Builder(/* ... */  modules = listOf(
    Modules.visitorId()
)).build()
```

```java
// Java
TealiumConfig config = new TealiumConfig.Builder(/* ... */ Arrays.asList(
    Modules.visitorId()
)).build();
```

> **Note:** This module is automatically included, so it is initialized with default settings even
> if it is not explicitly added to the modules list.

Pass a builder block to enforce specific settings:

```kotlin
// Kotlin
val config = TealiumConfig.Builder(/* ... */  modules = listOf(
    Modules.visitorId { builder ->
        builder.setOrder(1)
            .setRules(Rule.all(listOf(Rule.just("rule_id_from_settings"))))
            .setVisitorIdentityKey("email")
    }
)).build()
```

```java
// Java
TealiumConfig config = new TealiumConfig.Builder(/* ... */ Arrays.asList(
    Modules.visitorId(builder -> builder
        .setOrder(1)
        .setRules(Rule.all(Arrays.asList(Rule.just("rule_id_from_settings"))))
        .setVisitorIdentityKey("email"))
)).build();
```

> **Important:** Enforced settings are deep merged onto local and remote settings and always take
> precedence. Only enforce configuration values that you never want to be changed remotely, as they
> override any remote updates.

To disable the module, set `enabled: false` in its settings or call `setEnabled(false)` on its
builder. See Consequences of disabling below.

**Settings-driven initialization**

Pass `null` to enforce no settings at all. The module is then initialized only when some local or
remote settings are provided for it:

```kotlin
// Kotlin
val config = TealiumConfig.Builder(/* ... */  modules = listOf(
    Modules.visitorId(null)
)).build()
```

```java
// Java
TealiumConfig config = new TealiumConfig.Builder(/* ... */ Arrays.asList(
    Modules.visitorId(null)
)).build();
```

### Configuration options

| Setting | Key | Description | Default Value |
|---------|-----|-------------|---------------|
| Visitor Identity Key | `visitor_identity_key` | The data layer key that holds the identity of the current user, used to drive Visitor Switching. | `null` (Visitor Switching disabled) |

### Settings builder

The VisitorId module is configured through `VisitorIdSettingsBuilder`, which extends
`CollectorSettingsBuilder` and inherits the common module setters:

- `ModuleSettingsBuilder.setEnabled(Boolean)`
- `ModuleSettingsBuilder.setOrder(Int)`
- `RuleModuleSettingsBuilder.setRules(Rule<String>)`

VisitorId-specific setter:

- `VisitorIdSettingsBuilder.setVisitorIdentityKey(String)`: the data layer key that identifies the
  current user, enabling Visitor Switching.

### Visitor Switching

Visitor Switching links visitor IDs to identities you place in the data layer, such as an email
address or a user ID, so that the same person is recognized across logins on the same device.

Visitor Switching is disabled while no `visitor_identity_key` is configured. Once configured, the SDK
has no notion of login or logout: it simply watches that data layer key.

- Writing a **new** identity to the key, when no identity currently owns the visitor ID, links that
  identity to the current visitor ID.
- Writing a **known** identity (one seen before on this device) restores the visitor ID it was
  previously associated with.
- Writing a **different, unknown** identity generates a new visitor ID and links it to that identity.
- **Removing** the key from the data layer does not change the visitor ID.

### Seeding the first visitor ID

`TealiumConfig.Builder.setExistingVisitorId(String)` seeds the very first visitor ID for this device,
for example to migrate an ID generated by a previous SDK. It only applies on first launch, before any
visitor ID has been stored. It has no effect afterwards.

### Consequences of disabling

While the VisitorId module is disabled:

- `tealium_visitor_id` is not added to tracking events.
- `tealium.visitorId` and its methods (`current()`, `regenerate()`, `eraseLocalHistory()`) return a
  failure result.
- Identity-driven Visitor Switching stops: identity changes in the data layer are not observed.
- The Collect module still batches and sends events, grouping those without a visitor ID together.
- The Context API module can't fetch engine responses: `fetchEngineResponse(engineId)` fails with
  `ModuleNotEnabledException`.

### Usage

Read and observe the visitor ID through `tealium.visitorId`.

```kotlin
// Kotlin
val disposables = Disposables.composite()

// Subscribe to the current visitor ID and every subsequent change
tealium.visitorId.subscribe { result ->
    result.onSuccess { visitorId ->
        // Use the current visitor ID
        Log.d("Tealium", "Visitor ID: $visitorId")
    }.onFailure { ex ->
        if (ex is Tealium.TealiumShutdownException) {
            // The Tealium instance was shut down: this is the last emission
            Log.d("Tealium", "Visitor ID no longer available: $ex")
        } else if (ex is ModuleNotEnabledException) {
            // The VisitorId module is disabled: successes resume if it is re-enabled
            Log.d("Tealium", "Visitor ID not available: $ex")
        } else {
            // The Tealium instance failed to initialize: this is the last emission
            Log.d("Tealium", "Visitor ID no longer available: $ex")
        }
    }
}.addTo(disposables)

// Or read it once
tealium.visitorId.current().subscribe { result ->
    // Handle the result
}

// Generate a new anonymous visitor ID
tealium.visitorId.regenerate().subscribe { result ->
    // Handle the result
}

// Erase every stored identity association on this device, then generate a new anonymous visitor ID
tealium.visitorId.eraseLocalHistory().subscribe { result ->
    // Handle the result
}
```

```java
// Java
CompositeDisposable disposables = Disposables.composite();

// Subscribe to the current visitor ID and every subsequent change
disposables.add(tealium.getVisitorId().subscribe(result -> {
    result.onSuccess(visitorId -> {
        // Use the current visitor ID
        Log.d("Tealium", "Visitor ID: " + visitorId);
    }).onFailure(ex -> {
        if (ex instanceof Tealium.TealiumShutdownException) {
            // The Tealium instance was shut down: this is the last emission
            Log.d("Tealium", "Visitor ID no longer available: " + ex);
        } else if (ex instanceof ModuleNotEnabledException) {
            // The VisitorId module is disabled: successes resume if it is re-enabled
            Log.d("Tealium", "Visitor ID not available: " + ex);
        } else {
            // The Tealium instance failed to initialize: this is the last emission
            Log.d("Tealium", "Visitor ID no longer available: " + ex);
        }
    });
}));

// Or read it once
tealium.getVisitorId().current().subscribe(result -> {
    // Handle the result
});

// Generate a new anonymous visitor ID
tealium.getVisitorId().regenerate().subscribe(result -> {
    // Handle the result
});

// Erase every stored identity association on this device, then generate a new anonymous visitor ID
tealium.getVisitorId().eraseLocalHistory().subscribe(result -> {
    // Handle the result
});
```

While the module is disabled, every method returns a failure result, and subscribers receive a
failure carrying `ModuleNotEnabledException`, both at subscription time and if the module becomes
disabled later. These failures do not complete the subscription: successes resume if the module is
re-enabled. If the `Tealium` instance failed to initialize, or is later shut down, every method
returns a failure result, and subscribers receive a single failure carrying that exception
(`Tealium.TealiumShutdownException` on shutdown), after which the subscription completes.

If `regenerate()` or `eraseLocalHistory()` can't store their changes, they return a failure result,
but the new visitor ID is still used until the app is relaunched. In this case nothing is stored, so
on the next launch the previous visitor ID and every stored association are restored.
