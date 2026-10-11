# Kotlin, Hilt, Room, Retrofit and OkHttp ship their own consumer rules. Keeping
# whole packages of them here (kotlin.**, dagger.hilt.**, androidx.room.**,
# retrofit2.**, com.google.gson.**) only stopped R8 from shrinking them.

# Retrofit / OkHttp
-dontwarn okhttp3.**

# Coil
-dontwarn coil.**

# Gson (2.10, no bundled rules): it reads fields reflectively by name, and
# Retrofit reads generic signatures off the service interfaces.
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# App models — DTOs and the enums they carry are (de)serialized by field name.
-keep class com.burixer85.cs2skinflip.core.domain.model.** { *; }
-keep class com.burixer85.cs2skinflip.core.data.local.** { *; }
-keep class com.burixer85.cs2skinflip.core.data.remote.** { *; }

# Google Play Services (missing_rules.txt from R8)
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
