plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.mkbhdana.streamhive"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mkbhdana.streamhive"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
        jniLibs {
            pickFirsts.add("**/*.so")
        }
    }
}

dependencies {
    // Core
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    // Activity & Navigation
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Compose TV
    implementation(libs.tv.material)

    // Media3 / ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(libs.media3.session)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.common.ktx)
    implementation(libs.media3.inspector.frame)
    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Network
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.gson)

    // Google Auth & Drive
    implementation(libs.google.auth.library)
    implementation(libs.google.api.client)
    implementation(libs.google.api.services.drive)

    // Security (Encrypted SharedPreferences)
    implementation(libs.security.crypto)

    // Image Loading
    implementation(libs.coil.compose)

    // Splash Screen & Startup
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.startup:startup-runtime:1.1.1")

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // MPV Player (community artifact by abdallahmehiz)
    // implementation("io.github.abdallahmehiz:mpv-android-lib:0.1.12")

    // NanoHTTPD (local proxy server for secured streaming)
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // FFmpeg Decoder Extension (optional - build from source)
    // Place the built AAR in app/libs/ and uncomment:
    // implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("lib-decoder-*.aar"))))
    implementation("io.github.anilbeesetti:nextlib-media3ext:1.10.0-0.12.1") // To add media3 software decoders and extensions
    implementation("io.github.anilbeesetti:nextlib-mediainfo:1.10.0-0.12.1")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)

    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
