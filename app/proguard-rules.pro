# ==== kotlinx.serialization ====
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.focus.moment.**$$serializer { *; }
-keepclassmembers class com.focus.moment.** {
    *** Companion;
}
-keepclasseswithmembers class com.focus.moment.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ==== Ktor ====
-dontwarn io.ktor.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ==== Coroutines ====
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ==== Room ====
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# ==== Compose ====
-dontwarn androidx.compose.**
