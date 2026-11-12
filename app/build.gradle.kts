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
        versionCode = 6
        versionName = "0.6.0-hud-analysis-fpslab-alpha"
        manifestPlaceholders["coremetricsAppLabel"] = "CoreMetrics"
        manifestPlaceholders["coremetricsTileLabel"] = "CoreMetrics Record"
    }
    buildTypes {
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
