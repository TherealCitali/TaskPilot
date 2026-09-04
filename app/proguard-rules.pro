# TaskPilot R8 configuration.
#
# The app is almost entirely Compose UI plus a hand-rolled org.json client, so
# very little needs keeping. JSON is parsed field-by-field with explicit string
# keys (no reflection, no kotlinx.serialization), which means model classes are
# safe to rename and shrink.

# Entry points declared in the manifest (Activity, AccessibilityService) are
# kept automatically by the AGP-generated rules.

# Kotlin coroutines: the ServiceLoader-based main dispatcher factory is resolved
# reflectively, and the debug agent / internal fields are touched by name.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Compose keeps its own rules via consumer ProGuard files; nothing extra needed.

# org.json ships in the Android platform.
-dontwarn org.json.**

# Strip verbose logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
