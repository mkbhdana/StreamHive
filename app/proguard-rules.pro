# ProGuard rules for StreamHive

# Google API Client
-dontwarn com.google.api.**
-dontwarn com.google.auth.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Retrofit
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Media3 / ExoPlayer
-dontwarn androidx.media3.**

# MPV Android lib is loaded through reflection/JNI.
-keep class is.xyz.mpv.** { *; }
-keep class com.mkbhdana.streamhive.player.mpv.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn is.xyz.mpv.**

# Room
-dontwarn androidx.room.paging.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Google APIs
-dontwarn com.google.api.client.**
-dontwarn com.google.common.**

# Apache / Javax warnings from Google API / HTTP Client
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**

# Keep data models for JSON Serialization
-keep class com.mkbhdana.streamhive.data.model.** { *; }
-keep class com.mkbhdana.streamhive.data.tmdb.** { *; }
