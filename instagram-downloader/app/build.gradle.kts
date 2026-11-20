plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mediafetch.downloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mediafetch.downloader"
        minSdk = 23
        targetSdk = 35
        versionCode = 9
        versionName = "1.0.8"
        multiDexEnabled = true
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Retain the original SDK payload for compatibility with the established APK footprint.
    // No Tapsell initialization or ad display calls are present in MainActivity.
    implementation("ir.tapsell.plus:tapsell-plus-sdk-android:2.3.3")
    implementation("androidx.multidex:multidex:2.0.1")
}
