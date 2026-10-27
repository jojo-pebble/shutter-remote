plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.jojo.pebbleshutter"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.jojo.pebbleshutter"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Связь с часами через приложение Pebble от Core Devices
    implementation(libs.pebblekit2.client)
}
