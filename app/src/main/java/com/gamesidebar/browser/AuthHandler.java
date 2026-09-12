package com.gamesidebar.browser;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;

import androidx.browser.customtabs.CustomTabsIntent;

/**
 * SAFE auth handling for sites blocking embedded WebView login.
 * Do NOT bypass security, fake auth, capture passwords, or store tokens plaintext.
 * Use CustomTabs / system-supported OAuth when blocked.
 */
public class AuthHandler {

    // Known patterns that indicate site blocks embedded login
    private static final String[] BLOCKED_PATTERNS = {
        "accounts.google.com", // Google blocks WebView
        "embedded", "webview_blocked", "disallowed_useragent"
    };

    public static boolean isAuthBlockedUrl(String url) {
        if (url == null) return false;
        String l = url.toLowerCase();
        // Google OAuth explicitly blocks WebView
        if (l.contains("accounts.google.com") && (l.contains("oauth") || l.contains("signin"))) {
            return true;
        }
        return false;
    }

    public static boolean isAuthBlockedContent(WebView view, String url) {
        // Check title for block messages (simplified)
        String title = view.getTitle();
        if (title != null) {
            String t = title.toLowerCase();
            if (t.contains("does not allow") || t.contains("embedded") || t.contains("browser not supported")) {
                return true;
            }
        }
        return false;
    }

    public static void openSecureLogin(Context ctx, String url) {
        try {
            // Use CustomTabs - safe Android auth flow, not bypassing security
            CustomTabsIntent intent = new CustomTabsIntent.Builder().build();
            intent.intent.setData(Uri.parse(url));
            // Use FLAG_ACTIVITY_NEW_TASK because we are from service
            intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent.intent);
        } catch (Exception e) {
            // Fallback to browser
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Exception ignored) {}
        }
        // Never store passwords/tokens; after auth, return to sidebar manually when supported
    }
}
