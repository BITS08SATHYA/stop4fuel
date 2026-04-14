# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.stopforfuel.app.data.remote.dto.** { *; }
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes EnclosingMethod

# Tink (encrypted prefs) — errorprone annotations are compile-only
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
