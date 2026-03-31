@file:Suppress("ktlint:standard:property-naming")

pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://mirrors.huaweicloud.com/repository/maven")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://mirrors.huaweicloud.com/repository/maven")
        google()
        mavenCentral()
    }
}

val TRANSLATIONS_ONLY: String? by settings

if (TRANSLATIONS_ONLY.isNullOrBlank()) {
    include(":app")
}
