// Simplified root build for sbssh integration
buildscript {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:1.9.22-1.0.16")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.48")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}
