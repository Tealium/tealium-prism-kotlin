# Module momentsapi

The Moments API module provides support for real-time visitor personalization using Tealium's 
[Moments API](https://docs.tealium.com/server-side/moments-api/) Server Side technology.


## Getting started

To set up the Moments API module, simply add the Moments API ModuleFactory to your `TealiumConfig`

```kotlin
// Kotlin
val config = TealiumConfig.Builder(/* ... */  modules = listOf(
    Modules.momentsApi()
)).build()
```

```java
// Java
TealiumConfig config = new TealiumConfig.Builder(/* ... */ Arrays.asList(
    MomentsApi.configure()
)).build();
```

## Configuration

TODO