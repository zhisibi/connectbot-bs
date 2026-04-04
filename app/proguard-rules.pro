# Gson / R8 rules for this app

# Needed for Gson TypeToken to work after R8
-keepattributes Signature
-keepattributes *Annotation*

# Keep JSON model field names for Gson (cloud api)
-keep class com.boshconnect.ui.cloud.CloudSyncApi$* { *; }
-keepclassmembers class com.boshconnect.ui.cloud.CloudSyncApi$* {
    <fields>;
}

# Keep backup models used with Gson + TypeToken (defined in SettingsViewModel.kt)
-keep class com.boshconnect.ui.settings.BackupEnvelope { *; }
-keep class com.boshconnect.ui.settings.BackupItem { *; }
-keepclassmembers class com.boshconnect.ui.settings.BackupEnvelope { <fields>; }
-keepclassmembers class com.boshconnect.ui.settings.BackupItem { <fields>; }

# Also keep SettingsViewModel nested/inner models if any get rewritten
-keep class com.boshconnect.ui.settings.SettingsViewModel$* { *; }

# Keep all classes and members from the terminal emulation library
# R8 optimization might be breaking native/internal calls related to color schemes
-keep class org.connectbot.terminal.** { *; }
-keep class org.connectbot.service.TerminalManager { *; }
-keep class org.connectbot.service.TerminalBridge { *; }
-keep class com.boshconnect.connectbot.data.ColorSchemePresets { *; }

# Keep SSH/SFTP related libraries (JSch)
-keep class com.jcraft.jsch.** { *; }

# Keep App's transport layer
-keep class com.boshconnect.transport.** { *; }

# Keep Room entities and DAOs (data models)
-keep class com.boshconnect.data.db.** { *; }
-keep class com.boshconnect.connectbot.data.entity.** { *; }
-keep class com.boshconnect.connectbot.data.dao.** { *; }

# Keep JZlib and JASS classes (referenced by JSch)
# R8 is aggressively removing these, causing build failures.
-keep class com.jcraft.jzlib.** { *; }
-keep class org.ietf.jgss.** { *; }

# Tell R8 to ignore missing classes from JZlib and JASS (JSch dependencies)
# These may be optional or not fully supported on Android, and currently cause build failure.
-dontwarn com.jcraft.jzlib.**
-dontwarn org.ietf.jgss.**
