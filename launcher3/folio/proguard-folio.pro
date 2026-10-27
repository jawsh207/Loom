# Loom's own R8 rules, on top of launcher3/proguard.flags (GrapheneOS's Launcher3 rules).

# Keep line numbers so crash reports still point at the right lines.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Loom's own classes keep their names, like the rest of com.android.** in proguard.flags,
# so stack traces stay readable. They can still be shrunk and optimized.
-keep,allowshrinking,allowoptimization,allowaccessmodification class app.folio.** { *; }
