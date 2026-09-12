# Game SideBar Browser - release shrinking rules.
#
# Nothing in this app is reached by reflection except Room's generated code (kept by the Room
# Gradle plugin's own consumer rules) and WebView's JavaScript entry points, which this app
# deliberately does not expose. Keep rules are therefore minimal on purpose: an over-broad keep
# set is how overlay utilities end up shipping 3x the code they need.

# WebView: the class is instantiated reflectively by the platform on some OEM builds.
-keep class android.webkit.WebView { *; }
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String);
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *(android.webkit.WebView, int);
}

# Room entities/DAOs are generated; keep entity fields used by generated mappers.
-keep class com.gamesidebar.browser.data.db.** { *; }

# Kotlin metadata for DataStore serializers is retained by the library's consumer rules.
-dontwarn org.jetbrains.annotations.**
