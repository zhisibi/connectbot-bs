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
