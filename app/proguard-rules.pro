# ProGuard rules for DrivePlay

# Google API Client
-keep class com.google.api.** { *; }
-keep class com.google.auth.** { *; }
-dontwarn com.google.api.**
-dontwarn com.google.auth.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.driveplay.app.data.model.** { *; }
-keep class com.driveplay.app.auth.ServiceAccountClient$ServiceAccountJson { *; }
-keep class com.driveplay.app.auth.OAuth2Client$TokenResponse { *; }
-keep class com.driveplay.app.auth.ServiceAccountClient$TokenResponse { *; }

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
