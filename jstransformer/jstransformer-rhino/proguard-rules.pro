# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keepparameternames
-keeppackagenames
-renamesourcefileattribute SourceFile
-keepattributes Exceptions, InnerClasses, Signature, Deprecated, SourceFile, LineNumberTable,
*Annotation*, EnclosingMethod

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep class kotlin.Metadata { *; }

-keep public interface com.tealium.prism.jstransformer.rhino.* { *; }
-keep public class com.tealium.prism.jstransformer.rhino.* { *; }

-keep public class com.tealium.prism.**$DefaultImpls { *; }

# Members invoked reflectively by Rhino from JavaScript
-keepclassmembers class com.tealium.prism.jstransformer.rhino.internal.** {
    @org.mozilla.javascript.annotations.JSFunction <methods>;
}
# Expiry constants are read as JS properties via their getters
-keepclassmembers class com.tealium.prism.jstransformer.rhino.internal.ScriptableDataLayer$ScriptableExpiry {
    public <methods>;
}