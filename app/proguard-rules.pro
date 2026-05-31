-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-keepattributes *Annotation*

-keep class com.ghealth.tools.core.database.** { *; }

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
