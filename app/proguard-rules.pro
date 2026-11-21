# Add project specific ProGuard rules here.
# Keep line numbers and source file names for crash diagnostics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin & Kotlinx Serialization (Navigation routes, QuadPoints, NormalizedPoint)
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.example.prix.ui.navigation.**$$serializer { *; }
-keepclassmembers class com.example.prix.ui.navigation.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.example.prix.core.model.**$$serializer { *; }
-keepclassmembers class com.example.prix.core.model.** {
    *** Companion;
}

# Room Database Entities, DAOs, and Database class
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.flow.**

# Apache PDFBox-Android & FontBox
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**
-dontwarn org.bouncycastle.**
-dontwarn javax.imageio.**
-dontwarn java.awt.**

# Google ML Kit Text Recognition (Latin)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.mlkit.**

# CameraX
-keep class androidx.camera.** { *; }
-keepclassmembers class * implements androidx.camera.core.impl.Config$Option { *; }
