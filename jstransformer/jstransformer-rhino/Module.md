# Module jstransformer-rhino

Provides a JavaScriptTransformer implementation using the Mozilla Rhino engine.

When included as a dependency, this module's `ModuleFactory` is discovered via manifest metadata and added to `Modules.defaultModules`.

## Requirements
The Rhino engine uses Java APIs that Android provides only from API 24 (`java.util.function`) and API 26 (`java.time`). Apps with a `minSdk` below 26 must enable core library desugaring (`desugar_jdk_libs` 2.1.5 or later) in the app module; the Gradle setup is under Installation › JavaScript transformations on the Tealium Prism module page and in the README. Without it, Tealium fails to initialize on API 23 to 25 devices, even if no JavaScript transformation is configured, because this module's factory is registered automatically.