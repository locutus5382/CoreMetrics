plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.locutus5382.coremetrics"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.locutus5382.coremetrics"
        minSdk = 31
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 32
        versionName = "0.9.6.6-unified-alpha"
        manifestPlaceholders["coremetricsAppLabel"] = "CoreMetrics"
        manifestPlaceholders["coremetricsTileLabel"] = "CoreMetrics REC"
        manifestPlaceholders["coremetricsHudTileLabel"] = "CoreMetrics HUD"
    }
    // One canonical app; all four finishes are selected at runtime.
    buildTypes {
        release {
            // R8 is enabled for public/release candidates; debug remains
            // unminified to retain stack traces and instrumentation parity.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        create("optimizedTest") {
            // Identical R8/shrinker settings to release, installable using
            // Android's debug certificate. Each CI runner may create a
            // different certificate: never promise cross-run upgrades.
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
