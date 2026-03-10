# ProGuard / R8 rules for SupaPhone

# Keep Supabase SDK classes
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }

# Keep Firebase Messaging
-keep class com.google.firebase.** { *; }

# Keep ML Kit
-keep class com.google.mlkit.** { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.supaphone.app.**$$serializer { *; }
