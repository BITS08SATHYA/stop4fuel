# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.stopforfuel.app.data.remote.dto.** { *; }
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes EnclosingMethod
