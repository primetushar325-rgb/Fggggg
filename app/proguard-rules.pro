# GameSound Pro keeps most of the app reachable via reflection-free Compose/Room code paths,
# so the default optimizer rules below are enough. Room and Media3 ship their own consumer
# rules. Keep crash reports readable if a crash reporter is ever added.
-keepattributes SourceFile,LineNumberTable

# Media3 / ExoPlayer (defensive; upstream consumer rules already cover these)
-dontwarn org.checkerframework.**

# kotlinx.coroutines
-dontwarn kotlinx.coroutines.**
