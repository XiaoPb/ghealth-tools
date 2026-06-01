-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

-keep class com.ghealth.tools.core.database.** { *; }

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Navigation Compose (解决 release 版本 Cannot serialize abstract class 崩溃) ──
-keep class androidx.navigation.NavType { *; }
-keep class androidx.navigation.NavType$* { *; }
-keep class androidx.navigation.NavArgument { *; }
-keep class androidx.navigation.NavBackStackEntry { *; }
-keep class androidx.navigation.compose.** { *; }

# ── Hilt / Dagger 生成的代码 ──
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}
-keep class * extends dagger.internal.Factory { *; }
-keep class * extends dagger.MembersInjector { *; }

# ── 保持 Kotlin 元数据（data class / sealed class 需要） ──
-keep class kotlin.Metadata { *; }
-keep class kotlin.coroutines.Continuation { *; }

# ── 保持枚举（防止 R8 enum unboxing 破坏序列化） ──
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── 保持 Parcelable 的 CREATOR ──
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ── SavedStateHandle / Lifecycle ──
-keep class androidx.lifecycle.SavedStateHandle { *; }
-keep class androidx.lifecycle.SavedStateHandle$** { *; }
-keep class androidx.lifecycle.SavedStateViewModelFactory { *; }

# ── 保持产测模块（feature-factory）所有类，防止 R8 类合并导致抽象化 ──
-keep class com.ghealth.tools.feature.factory.** { *; }
-keepclassmembers class com.ghealth.tools.feature.factory.** { *; }

# ── 保持 ble-connection 中的 sealed class / data class ──
-keep class com.ghealth.tools.ble.connection.BleConnectionManager { *; }
-keep class com.ghealth.tools.ble.connection.ConnectedDevice { *; }
-keep class com.ghealth.tools.ble.connection.ConnectionError { *; }
-keep class com.ghealth.tools.ble.connection.ConnectionError$* { *; }
-keep class com.ghealth.tools.ble.connection.ConnectionConstraint { *; }
-keep class com.ghealth.tools.ble.connection.ConnectionConstraint$* { *; }
-keep class com.ghealth.tools.ble.connection.DfuConnectionState { *; }
-keep class com.ghealth.tools.ble.connection.DfuConnectionState$* { *; }
-keep class com.ghealth.tools.ble.connection.DeviceRole { *; }

# ── 关闭 R8 全模式已知问题优化 ──
-optimizations !class/merging/*,!code/allocation/variable
