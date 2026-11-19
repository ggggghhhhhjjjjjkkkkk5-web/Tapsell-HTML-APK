plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.padafand.instagramdownloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.padafand.instagramdownloader"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
}
