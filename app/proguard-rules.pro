# R8 / ProGuard rules for LightReader (minifyEnabled=true)

# --- Kotlinx Serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.will.lightreader.**$$serializer { *; }
-keepclassmembers class com.will.lightreader.** {
    *** Companion;
}
-keepclasseswithmembers class com.will.lightreader.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Room (defensive; KSP generates already-kept code) ---
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- Jsoup (org.jsoup is generally consumer-safe; keep utility internals) ---
-dontwarn org.jsoup.**

# --- OkHttp / Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Coil ---
-dontwarn coil.**

# WebView invokes these methods by their JavaScript-visible names.
-keepclassmembers class com.will.lightreader.source.web.WebViewFetcher$FetchBridge {
    @android.webkit.JavascriptInterface <methods>;
}
