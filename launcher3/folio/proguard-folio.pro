# Loom's own R8 rules, on top of launcher3/proguard.flags (GrapheneOS's Launcher3 rules).

# Keep line numbers so crash reports still point at the right lines.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Loom's own classes keep their names, like the rest of com.android.** in proguard.flags,
# so stack traces stay readable. They can still be shrunk and optimized.
-keep,allowshrinking,allowoptimization,allowaccessmodification class app.folio.** { *; }

# slf4j (pulled in by the article reader) looks for an optional logging backend that isn't
# there; it falls back to doing nothing.
-dontwarn org.slf4j.impl.**

# protobuf-lite reads its generated messages' fields by name (the launcher's logging and saved data).
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }

# SharedPreferences only holds its change listeners weakly. A field that keeps one alive is
# never read, so R8 would drop it and the listener would stop firing (the feed panel missed
# Style changes). Keep every such field.
-keepclassmembers class * {
    android.content.SharedPreferences$OnSharedPreferenceChangeListener *;
}
