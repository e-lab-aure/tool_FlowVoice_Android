# Keep data classes used with Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.flowvoice.android.api.** { *; }
-keep class com.flowvoice.android.settings.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
