-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.zbxapp.**$$serializer { *; }
-keepclassmembers class com.zbxapp.** {
    *** Companion;
}
-keepclasseswithmembers class com.zbxapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keep class okhttp3.internal.publicsuffix.PublicSuffixDatabase { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.debug.**
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn sun.misc.SignalHandler

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keep class androidx.room.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-dontwarn androidx.room.paging.**

# Timber
-dontwarn org.jetbrains.annotations.**
-keep class timber.log.** { *; }
-assumenosideeffects class timber.log.Timber$Tree {
    public void v(...);
    public void d(...);
}

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# App data classes referenced by Room/serialization
-keep class com.zbxapp.data.api.models.** { *; }
-keep class com.zbxapp.data.cache.** { *; }
