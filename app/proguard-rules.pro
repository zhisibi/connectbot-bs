# Keep JSON model field names for Gson (cloud api)
-keep class com.boshconnect.ui.cloud.CloudSyncApi$* { *; }
-keepclassmembers class com.boshconnect.ui.cloud.CloudSyncApi$* {
    <fields>;
}
