plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val tapsellAppKey = System.getenv("TAPSELL_APP_KEY") ?: ""

android {
    namespace = "com.mediafetch.downloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mediafetch.downloader"
        minSdk = 23
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"
        buildConfigField("String", "TAPSELL_APP_KEY", "\"" + tapsellAppKey + "\"")
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
    implementation("ir.tapsell.plus:tapsell-plus-sdk-android:2.3.3")
    implementation("androidx.multidex:multidex:2.0.1")
}
