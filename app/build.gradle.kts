plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.sbssh"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sbssh"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "com.sbssh.HiltTestRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            manifestPlaceholders["memtagMode"] = "sync"
        }
        release {
            isMinifyEnabled = false
            manifestPlaceholders["memtagMode"] = "async"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

configurations.all {
    resolutionStrategy.eachDependency {
        when (requested.group) {
            "androidx.core" -> useVersion("1.12.0")
            "androidx.lifecycle" -> useVersion("2.7.0")
            "androidx.savedstate" -> useVersion("1.2.0")
            "androidx.appcompat" -> useVersion("1.6.1")
            "androidx.compose.ui",
            "androidx.compose.runtime",
            "androidx.compose.foundation",
            "androidx.compose.animation",
            "androidx.compose.material" -> useVersion("1.6.0")
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2026.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-android-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // SSH/Terminal (ConnectBot)
    implementation("org.connectbot:sshlib:2.2.44")
    implementation("org.connectbot:termlib:0.0.18")

    // JSch for SFTP
    implementation("com.jcraft:jsch:0.1.55")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Timber
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Workaround: disable checkDebugClasspath task (fails in this environment)
tasks.matching { it.name == "checkDebugClasspath" }.configureEach {
    enabled = false
}

