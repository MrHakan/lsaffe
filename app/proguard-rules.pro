# DeckWatch R8 keep rules.

# SQLCipher loads its native library reflectively.
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# kotlinx.serialization — keep serializers for our models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.deckwatch.**$$serializer { *; }
-keepclassmembers class com.deckwatch.** { *** Companion; }
-keepclasseswithmembers class com.deckwatch.** { kotlinx.serialization.KSerializer serializer(...); }
