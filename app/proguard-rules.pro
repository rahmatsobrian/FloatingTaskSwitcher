# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }

# Shizuku
-keep class rikka.shizuku.** { *; }

# libsu
-keep class com.topjohnwu.superuser.** { *; }

# Room entities
-keep class com.rahmatsobrian.floatingtaskswitcher.data.local.entity.** { *; }

-keepattributes *Annotation*
-keepattributes Signature
