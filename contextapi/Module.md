# Module contextapi

The Context API module provides support for real-time visitor personalization using Tealium's 
[Context API](https://docs.tealium.com/server-side/context-api/) Server Side technology.

Fetching an engine response requires the visitor ID, so `fetchEngineResponse` fails with 
`ModuleNotEnabledException` while the VisitorId module is disabled. A request still in progress when 
the Context API module shuts down fails with `ModuleShutdownException`.


## Getting started

To set up the Context API module, simply add the Context API ModuleFactory to your `TealiumConfig`

```kotlin
// Kotlin
val config = TealiumConfig.Builder(/* ... */  modules = listOf(
    Modules.contextApi()
)).build()
```

```java
// Java
TealiumConfig config = new TealiumConfig.Builder(/* ... */ Arrays.asList(
    ContextApi.configure()
)).build();
```

## Configuration

TODO