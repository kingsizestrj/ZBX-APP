-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.zbxapp.**$$serializer { *; }
-keepclassmembers class com.zbxapp.** {
    *** Companion;
}
-keepclasseswithmembers class com.zbxapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}
