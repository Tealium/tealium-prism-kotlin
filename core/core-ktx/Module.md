# Module core-ktx

Provides Kotlin-specific method definitions for the core library.

## Overview

The Tealium Prism SDK is intended to be usable by both Kotlin and Java callers and so some features
are written in a way that may not be "kotlin first". The `core-ktx` module is there to bridge the 
gap by providing Kotlin-specific functions for easy interoperability.

### Example: asynchronous 

For many reasons, the majority of work performed by the Prism SDK will be on a background thread, 
and therefore most interactions with the Prism SDK will be in an asynchronous manner. This module 
therefore adds helpers so that Kotlin users can keep using coroutines when interacting with the Prism
SDK, whilst still allowing Java users to interact directly.

```kotlin
scope.launch {
    val someValue = tealium.dataLayer.get("some_key")
        .await()
    // do something with someValue
}

scope.launch {
    tealium.dataLayer.onDataUpdated
        .collect { updatedData: DataObject -> 
            // do something
        }
}
```