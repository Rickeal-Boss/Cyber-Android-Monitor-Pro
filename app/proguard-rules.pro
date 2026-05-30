# Device Info Viewer ProGuard Rules

# Gson (JSON serialization)
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.example.deviceinfoviewer.data.model.** { *; }
