# Add project specific ProGuard rules here.

-keepparameternames
-keeppackagenames
-renamesourcefileattribute SourceFile
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,
                SourceFile,LineNumberTable,*Annotation*,EnclosingMethod

-keep class kotlin.Metadata { *; }

-keep public interface com.tealium.prism.core.ktx.** { *; }
-keep public class com.tealium.prism.core.ktx.** { *; }

-keep public class com.tealium.prism.**$DefaultImpls { *; }
