-keep class com.tealium.prism.jstransformer.rhino.RhinoJavaScriptTransformerFactory {
    <init>(...);
    *;
}

-keep class org.mozilla.javascript.**
-keep interface org.mozilla.javascript.**

# Rhino references java.beans.* (Java SE only, not available on Android).
# These code paths are unreachable on Android — Rhino is run in interpreted
# mode (isInterpretedMode = true), so the JVM bytecode optimizer path that
# uses these classes is never invoked.
-dontwarn org.mozilla.javascript.JavaToJSONConverters

## Rhino's optimizer/Bootstrapper references jdk.dynalink.* (JDK 9+ internal
## API, not available on Android). Same reason as above — dead code on Android.
-dontwarn org.mozilla.javascript.optimizer.**
