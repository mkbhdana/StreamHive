# ProGuard rules for StreamHive

# Google API Client
-keep class com.google.api.** { *; }
-keep class com.google.auth.** { *; }
-dontwarn com.google.api.**
-dontwarn com.google.auth.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.mkbhdana.streamhive.data.model.** { *; }
-keep class com.mkbhdana.streamhive.auth.ServiceAccountClient$ServiceAccountJson { *; }
-keep class com.mkbhdana.streamhive.auth.OAuth2Client$TokenResponse { *; }
-keep class com.mkbhdana.streamhive.auth.ServiceAccountClient$TokenResponse { *; }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Retrofit
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# MPV Android lib is loaded through reflection/JNI.
-keep class is.xyz.mpv.** { *; }
-keep class com.mkbhdana.streamhive.player.mpv.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn is.xyz.mpv.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Google APIs
-dontwarn com.google.api.client.**
-dontwarn com.google.common.**
-keep class com.google.api.services.drive.** { *; }

# Apache / Javax warnings from Google API / HTTP Client
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**
