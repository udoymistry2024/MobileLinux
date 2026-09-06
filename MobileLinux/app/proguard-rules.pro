# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK installation.

# Keep proot process classes
-keep class com.mobilelinux.runtime.** { *; }
-keep class com.mobilelinux.terminal.** { *; }
-keep class com.mobilelinux.service.** { *; }

# Keep terminal session data
-keepclassmembers class com.mobilelinux.terminal.TerminalSession {
    *;
}

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Parcelize
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
