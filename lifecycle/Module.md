# Module lifecycle

The Lifecycle module provides application lifecycle event tracking. It can automatically 
track `launch`, `wake` and `sleep` events which coincide with the application starting, 
being put into the foreground and being put into the background.


## Getting started

To set up Lifecycle event tracking, simply add the Lifecycle ModuleFactory to your `TealiumConfig`

```kotlin
// Kotlin
val config = TealiumConfig.Builder(/* ... */  modules = listOf(
    Modules.lifecycle()
)).build()
```

```java
// Java
TealiumConfig config = new TealiumConfig.Builder(/* ... */ Arrays.asList(
    Lifecycle.configure()
)).build();
```

## Configuration

TODO