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
        versionCode = 8
        versionName = "1.0.7"
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
    implementation("androidx.multidex:multidex:2.0.1")
}
