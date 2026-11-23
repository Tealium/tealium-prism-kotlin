-keep class com.tealium.prism.jstransformer.rhino.RhinoJavaScriptTransformerFactory {
    <init>(...);
    *;
}

# Rhino loads some classes by name and calls members it finds by reflection: built-ins such as
# RegExp, the typed arrays, DataView and Continuation are built on first use by looking up a
# static init(Context, Scriptable, boolean) method (or, failing that, the constructors and
# jsFunction_/jsGet_ members), and the interpreter and regexp engine are instantiated by class
# name. Nothing calls these directly, so R8 removes them unless every member is kept.
# "class" also matches interfaces.
-keep class org.mozilla.javascript.** { *; }

# Rhino references java.beans.* (Java SE only, not available on Android).
# These code paths are unreachable on Android — Rhino is run in interpreted
# mode (isInterpretedMode = true), so the JVM bytecode optimizer path that
# uses these classes is never invoked.
-dontwarn org.mozilla.javascript.JavaToJSONConverters

## Rhino's optimizer/Bootstrapper references jdk.dynalink.* (JDK 9+ internal
## API, not available on Android). Same reason as above — dead code on Android.
-dontwarn org.mozilla.javascript.optimizer.**

# Members invoked reflectively by Rhino from JavaScript
-keepclassmembers class com.tealium.prism.jstransformer.rhino.internal.** {
    @org.mozilla.javascript.annotations.JSFunction <methods>;
}
# Expiry constants are read as JS properties via their getters
-keepclassmembers class com.tealium.prism.jstransformer.rhino.internal.ScriptableDataLayer$ScriptableExpiry {
    public <methods>;
}