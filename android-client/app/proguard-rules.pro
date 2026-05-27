# ── Protobuf Lite ────────────────────────────────────────────────────────────
# Keep all generated message classes and their builders
-keep class com.lintab.protocol.** { *; }
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
    <methods>;
}
-dontwarn com.google.protobuf.**

# ── Kotlin coroutines ────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ── View Binding ─────────────────────────────────────────────────────────────
-keep class com.lintab.client.databinding.** { *; }
