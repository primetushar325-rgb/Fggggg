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

    // BUG #1 FIX: Proper auth handoff — only OAuth flow uses CustomTab, normal browsing stays inside WebView
    private static final String[] EXTERNAL_AUTH_HOSTS = {
        "accounts.google.com", "accounts.youtube.com", "accounts.google.co.in", "auth.google.com"
    };

    public static boolean isAuthBlockedUrl(String url) {
        if (url == null) return false;
        String l = url.toLowerCase();
        // Host-based check (like UrlSafety.requiresExternalAuth) — Google blocks WebView OAuth
        for (String host : EXTERNAL_AUTH_HOSTS) {
            if (l.contains(host)) return true;
        }
        // Legacy check
        if (l.contains("accounts.google.com") && (l.contains("oauth") || l.contains("signin"))) {
            return true;
        }
        if (l.contains("disallowed_useragent")) return true;
        return false;
    }

    public static boolean isAuthBlockedContent(WebView view, String url) {
        String title = view.getTitle();
        // Check URL for disallowed_useragent (Google's blocked embedded login response)
        if (url != null && url.toLowerCase().contains("disallowed_useragent")) return true;
        if (title != null) {
            String t = title.toLowerCase();
            if (t.contains("does not allow") || t.contains("embedded") || t.contains("browser not supported") || t.contains("couldn't sign you in") || t.contains("could not sign you in")) {
                return true;
            }
        }
        // Also check current URL via isAuthBlockedUrl
        if (url != null && isAuthBlockedUrl(url)) return true;
        return false;
    }

    public static boolean isBlockedSignIn(String url, String title) {
        if (url != null && url.toLowerCase().contains("disallowed_useragent")) return true;
        if (url != null && url.toLowerCase().contains("accounts.google.com") && url.toLowerCase().contains("error")) return true;
        if (title != null && title.toLowerCase().contains("couldn't sign you in")) return true;
        return isAuthBlockedUrl(url) || isAuthBlockedContent(null, url);
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
