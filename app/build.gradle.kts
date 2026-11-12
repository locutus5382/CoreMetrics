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
        versionCode = 8
        versionName = "0.8.0-analytics-alpha"
        manifestPlaceholders["coremetricsAppLabel"] = "CoreMetrics"
        manifestPlaceholders["coremetricsTileLabel"] = "CoreMetrics Record"
        manifestPlaceholders["coremetricsHudTileLabel"] = "CM · HUD"
    }
    buildTypes {
        create("v08themes") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".v08themes"
            versionNameSuffix = "-themes"
            manifestPlaceholders["coremetricsAppLabel"] = "CoreMetrics v0.8 Themes"
            manifestPlaceholders["coremetricsTileLabel"] = "CM v0.8 Themes Record"
            manifestPlaceholders["coremetricsHudTileLabel"] = "CM v0.8 Themes · HUD"
        }
        create("v08base") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".v08base"
            versionNameSuffix = "-analytics"
            manifestPlaceholders["coremetricsAppLabel"] = "CoreMetrics v0.8 Analytics"
            manifestPlaceholders["coremetricsTileLabel"] = "CM v0.8 Analytics Record"
            manifestPlaceholders["coremetricsHudTileLabel"] = "CM v0.8 Analytics · HUD"
        }
        create("v07test") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".v07test"
            versionNameSuffix = "-side-by-side"
            manifestPlaceholders["coremetricsAppLabel"] = "CoreMetrics v0.7 Test"
            manifestPlaceholders["coremetricsTileLabel"] = "CoreMetrics v0.7 Record"
        }
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            manifestPlaceholders["coremetricsAppLabel"] = "CoreMetrics Preview"
            manifestPlaceholders["coremetricsTileLabel"] = "CoreMetrics Preview Record"
        }
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
}
