# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt / Dagger generated code
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *** serializer(...); }

# Data classes used as manifest payloads must keep field names for JSON
-keep class com.siroha.resourcetransfer.domain.model.** { *; }

# Shizuku — ShizukuHelper calls Shizuku.newProcess via reflection, and the
# Shizuku app itself binds to ShizukuProvider/ShizukuService by class name,
# so none of this can be renamed or stripped.
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

