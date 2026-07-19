# TLL release keep rules (R8). Each rule guards a runtime lookup that R8 can't see
# statically — trim only with a full on-device regression (sync, both engines, backup).

# --- Crash reports: keep readable stack traces (mapping.txt still needed for names) ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- libmpv JNI: native code resolves MPVLib and its event callbacks by name ---
-keep class dev.jdtech.mpv.** { *; }

# --- WorkManager: workers are re-instantiated by FQCN string (KoinWorkerFactory matches
#     workerClassName against ::class.java.name, and WorkManager persists the name in its DB
#     across app updates) ---
-keep class * extends androidx.work.ListenableWorker

# --- Persisted enum names: DataStore prefs, backup JSON, and per-setting modes
#     (ZoomMode, ThemeMode, StartupMode, AnimationLevel, ResumeMode,
#     EpgAutoRefresh, PlaylistAutoRefresh, ...) round-trip through Enum.name/valueOf.
#     Renaming a constant would silently reset settings and break old backups. ---
-keep enum com.thirutricks.tllplayer.** { *; }

# --- Keep data models to prevent Gson serialization/deserialization issues ---
-keep class com.thirutricks.tllplayer.models.** { *; }

# --- Keep Gua library classes ---
-keep class io.github.lizongying.** { *; }

# --- Keep generic type signatures for Gson TypeToken ---
-keepattributes Signature
-keepattributes *Annotation*
