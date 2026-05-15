-dontwarn javax.annotation.**
-keepattributes *Annotation*

-keep class com.ghealth.tools.core.database.** { *; }

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
