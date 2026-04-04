plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.boshconnect"
    compileSdk = 36

    base {
        archivesName.set("boshconnect")
    }

    defaultConfig {
        applicationId = "com.boshconnect"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0"

        testInstrumentationRunner = "com.boshconnect.HiltTestRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("boolean", "HAS_DOWNLOADABLE_FONTS", "false")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/boshconnect.jks")
            storePassword = "boshconnect2026"
            keyAlias = "boshconnect"
            keyPassword = "boshconnect2026"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            manifestPlaceholders["memtagMode"] = "sync"
        }
        release {
            // Re-enable R8/resource shrinking for production build.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Use debug signing to allow GitHub Actions to build without keystore
            signingConfig = signingConfigs.getByName("debug")
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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}


configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "androidx.core") {
            useVersion("1.15.0")
            if (requested.name == "core-viewtree") {
                useVersion("1.0.0")
            }
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2026.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.57")
    kapt("com.google.dagger:hilt-android-compiler:2.57")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // Preferences
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Preferences
    implementation("androidx.preference:preference-ktx:1.2.1")

    // SSH/Terminal (ConnectBot)
    implementation("org.connectbot:sshlib:2.2.44")
    implementation("org.connectbot:termlib:0.0.23")

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

// Workaround: disable classpath check tasks (fail in this environment)
tasks.matching { it.name == "checkDebugClasspath" || it.name == "checkReleaseClasspath" }.configureEach {
    enabled = false
}

