# Module contextapi

The Context API module provides support for real-time visitor personalization using Tealium's 
[Context API](https://docs.tealium.com/server-side/context-api/) Server Side technology.


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