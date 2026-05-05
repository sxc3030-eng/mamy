# =============================================================================
# MamY V1 — Release ProGuard / R8 Rules
# =============================================================================
# Goal: keep enough metadata for reflection-driven libraries (Room, Hilt,
# kotlinx.serialization, JNI) while still letting R8 shrink + obfuscate the
# rest. Tested with ./gradlew :app:assembleRelease.
# -----------------------------------------------------------------------------

# Annotation + signature metadata (kept across the board so reflection + Kotlin
# metadata + nullability annotations survive)
-keepattributes *Annotation*,SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations

# Source file rewriting for stack traces in crash logs (keep mapping readable)
-renamesourcefileattribute SourceFile

# -----------------------------------------------------------------------------
# Room (entities, DAOs, type converters)
# -----------------------------------------------------------------------------
-keep class com.mamy.android.data.db.entity.** { *; }
-keep class com.mamy.android.data.db.dao.** { *; }
-keep class com.mamy.android.data.db.MamYDatabase { *; }
-keep class com.mamy.android.data.db.MamYDatabase_Impl { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep @androidx.room.TypeConverter class *
-keep @androidx.room.Database class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# -----------------------------------------------------------------------------
# Hilt / Dagger generated classes
# -----------------------------------------------------------------------------
-keep class **.Hilt_* { *; }
-keep class **_HiltModules { *; }
-keep class **_HiltModules$* { *; }
-keep class **_Factory { *; }
-keep class **_Factory$* { *; }
-keep class dagger.hilt.** { *; }
-keep class dagger.internal.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.Module class *
-keep @dagger.Provides class *
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}

# -----------------------------------------------------------------------------
# Kotlin metadata + serialization
# -----------------------------------------------------------------------------
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mamy.android.**$$serializer { *; }
-keepclassmembers class com.mamy.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.mamy.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }

# -----------------------------------------------------------------------------
# Coroutines
# -----------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembernames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepclassmembernames class kotlinx.coroutines.CoroutineExceptionHandler {}

# -----------------------------------------------------------------------------
# SQLCipher (net.zetetic) — Room with encryption
# -----------------------------------------------------------------------------
-keep class net.zetetic.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.zetetic.**
-dontwarn net.sqlcipher.**

# -----------------------------------------------------------------------------
# Whisper.cpp JNI bindings
# -----------------------------------------------------------------------------
-keep class com.mamy.android.data.stt.jni.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclassmembers class * {
    native <methods>;
}

# -----------------------------------------------------------------------------
# Picovoice Porcupine wake-word
# -----------------------------------------------------------------------------
-keep class ai.picovoice.** { *; }
-dontwarn ai.picovoice.**

# -----------------------------------------------------------------------------
# Bouncy Castle (used by AES export pipeline)
# -----------------------------------------------------------------------------
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class javax.naming.** { *; }
-dontwarn javax.naming.**

# -----------------------------------------------------------------------------
# AndroidX / Compose / Lifecycle
# -----------------------------------------------------------------------------
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(...); }
-keepclassmembers class ** {
    @androidx.lifecycle.* <methods>;
}

# Compose runtime helpers
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# WorkManager workers
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# -----------------------------------------------------------------------------
# OkHttp / Retrofit / kotlinx.serialization wire format
# -----------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.** { *; }

# -----------------------------------------------------------------------------
# Google Play services / Credentials API
# -----------------------------------------------------------------------------
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-keep class com.google.android.libraries.identity.** { *; }
-dontwarn com.google.android.libraries.identity.**

# -----------------------------------------------------------------------------
# Misc — keep app entry points
# -----------------------------------------------------------------------------
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Activity
-keep class com.mamy.android.MamYApplication { *; }

# Avoid stripping enum values used by reflection / serialization
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Parcelable CREATOR fields
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Don't fail on missing optional deps
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Keep crash-friendly line numbers
-keepattributes LineNumberTable,SourceFile
