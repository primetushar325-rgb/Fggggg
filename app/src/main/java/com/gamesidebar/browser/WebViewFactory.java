package com.gamesidebar.browser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

/**
 * DO NOT recreate WebView when resizing/dragging/collapsing/reopening/video mode.
 * Preserve session/cookies/cache. Avoid webView.destroy() unless activity destroy.
 * Factory ensures singleton WebView reused.
 */
public class WebViewFactory {
    private static CustomWebView sWebView;

    @SuppressLint("SetJavaScriptEnabled")
    public static synchronized CustomWebView getOrCreate(Context ctx) {
        if (sWebView != null) return sWebView;
        Context appCtx = ctx.getApplicationContext();
        sWebView = new CustomWebView(appCtx);
        WebSettings s = sWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.setSafeBrowsingEnabled(true);
        }
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(sWebView, true);
        }
        // Important: keep WebView alive, do not destroy on overlay hide
        return sWebView;
    }

    public static synchronized void destroyIfNeeded() {
        if (sWebView != null) {
            sWebView.destroy();
            sWebView = null;
        }
    }

    // Never call destroy on collapse/resize/hide - only on service destroy
}
