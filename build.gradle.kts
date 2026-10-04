plugins {
    id("com.android.application") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
}

android {
    namespace = "com.kipat.probe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kipat.probe"
        // Spec section 17 recommends API 26 as the floor. Keep it here so the
        // probe surfaces pre-Q behaviour differences (no setTranslucent, no
        // frame-commit callback) instead of hiding them behind a higher minSdk.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-probe"
    }

    buildTypes {
        // Debug only on purpose. Do not let this module reach a release track.
        debug {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

