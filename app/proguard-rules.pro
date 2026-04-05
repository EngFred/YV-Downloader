# ─── Hilt ────────────────────────────────────────────────────────────────────
-keep class com.engfred.yvd.YVDApplication { *; }
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# ─── Coroutines ───────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ─── Room ─────────────────────────────────────────────────────────────────────
# Keep all @Entity classes and their fields — R8 must NOT rename columns
# because Room maps them by name to the SQLite schema at runtime.
-keep @androidx.room.Entity class * { *; }

# Keep all @Dao interfaces — Room generates _Impl classes by name at compile
# time; if R8 renames the interface the generated impl won't be found.
-keep @androidx.room.Dao interface * { *; }

# Keep the database class itself and its generated _Impl
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class **_Impl { *; }

# Keep @TypeConverter methods — Room calls these by reflection
-keepclassmembers class * {
    @androidx.room.TypeConverter <methods>;
}

# Keep your specific Room classes by name as a safety net
-keep class com.engfred.yvd.data.local.DownloadQueueEntity { *; }
-keep class com.engfred.yvd.data.local.DownloadQueueDao { *; }
-keep class com.engfred.yvd.data.local.AppDatabase { *; }
-keep class com.engfred.yvd.data.local.Converters { *; }

# Suppress known Room/SQLite warnings
-dontwarn androidx.room.**

# ─── NewPipe Extractor ────────────────────────────────────────────────────────
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# ─── jsoup (pulled in by NewPipe) ─────────────────────────────────────────────
-dontwarn com.google.re2j.**
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# ─── Mozilla Rhino (pulled in by NewPipe for JS extraction) ───────────────────
-dontwarn java.beans.**
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# ─── javax.script ─────────────────────────────────────────────────────────────
-dontwarn javax.script.**

# ─── OkHttp ───────────────────────────────────────────────────────────────────
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**

# ─── WorkManager ──────────────────────────────────────────────────────────────
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# ─── Kotlin Serialization (if used anywhere) ──────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ─── Keep Kotlin metadata (needed by many reflection-based libs) ───────────────
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations