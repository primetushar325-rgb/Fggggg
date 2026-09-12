package com.gamesidebar.browser;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ProgressBar;

import com.gamesidebar.util.PrefsManager;

/**
 * SAFE TARGETED PATCH - BrowserController
 * Fixes: SINGLE BACK button, keyboard GO, login handling, error recovery, video mode integration.
 */
public class BrowserController {

    private final Context ctx;
    private final CustomWebView webView;
    private final EditText etAddress;
    private final ProgressBar progressBar;
    private final View authBanner;
    private final PrefsManager prefs;
    private final VideoModeManager videoManager;

    private static final String DEFAULT_URL = "https://www.youtube.com";

    public BrowserController(Context ctx, CustomWebView webView, EditText etAddress, ProgressBar progressBar, View authBanner, PrefsManager prefs, VideoModeManager videoManager) {
        this.ctx = ctx;
        this.webView = webView;
        this.etAddress = etAddress;
        this.progressBar = progressBar;
        this.authBanner = authBanner;
        this.prefs = prefs;
        this.videoManager = videoManager;
        init();
    }

    private void init() {
        // Preserve WebView session - do NOT recreate
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Do NOT open in external Chrome by default - keep in sidebar
                // Only for blocked auth, we use CustomTabs via AuthHandler
                if (AuthHandler.isAuthBlockedUrl(url)) {
                    showAuthBlocked(url);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                etAddress.setText(url);
                hideAuthBanner();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                prefs.saveUrl(url);
                // Detect auth block pages
                if (AuthHandler.isAuthBlockedContent(view, url)) {
                    showAuthBlocked(url);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                progressBar.setVisibility(View.GONE);
                // DO NOT close sidebar on error - keep alive, show reload
                // Let WebView show its error page
            }
        });

        webView.setWebChromeClient(videoManager.getWebChromeClient(webView));

        // Address bar: FIX keyboard GO must NOT close sidebar
        etAddress.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                String input = etAddress.getText().toString().trim();
                if (!input.isEmpty()) {
                    String url = toUrl(input);
                    webView.loadUrl(url);
                    // FIX: Do NOT finish Activity / collapse overlay / treat as back
                    // Just hide keyboard and keep sidebar open
                    hideKeyboard();
                    // Keep sidebar visible - no collapse
                }
                return true;
            }
            return false;
        });

        // Restore last URL or default - do NOT reset on collapse/resize
        String lastUrl = prefs.getUrl(DEFAULT_URL);
        if (webView.getUrl() == null) {
            webView.loadUrl(lastUrl);
        }

        // Auth banner secure login button
        View btnSecureLogin = authBanner.findViewById(ctx.getResources().getIdentifier("btnSecureLogin", "id", ctx.getPackageName()));
        if (btnSecureLogin != null) {
            btnSecureLogin.setOnClickListener(v -> {
                String url = webView.getUrl() != null ? webView.getUrl() : prefs.getUrl(DEFAULT_URL);
                AuthHandler.openSecureLogin(ctx, url);
            });
        }
    }

    private String toUrl(String input) {
        if (input.startsWith("http://") || input.startsWith("https://")) return input;
        if (input.contains(".") && !input.contains(" ")) return "https://" + input;
        // Search query
        return "https://www.google.com/search?q=" + Uri.encode(input);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etAddress.getWindowToken(), 0);
        }
        // Do NOT close sidebar - keep open
        etAddress.clearFocus();
    }

    private void showAuthBlocked(String url) {
        authBanner.setVisibility(View.VISIBLE);
        // Do NOT try to bypass security - show explanation and secure login
    }

    private void hideAuthBanner() {
        authBanner.setVisibility(View.GONE);
    }

    /**
     * FIX: Correct back priority
     * IF keyboard visible -> hide keyboard (DO NOT close sidebar)
     * ELSE IF browser canGoBack -> browser back
     * ELSE -> follow existing sidebar behavior (collapse or do nothing)
     */
    public boolean handleBack(boolean isKeyboardVisible) {
        if (isKeyboardVisible) {
            hideKeyboard();
            return true; // consumed, do NOT close sidebar
        }
        if (videoManager.isVideoMode()) {
            videoManager.exitVideoMode();
            return true;
        }
        if (webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return false; // let overlay decide (collapse)
    }

    public void reload() { webView.reload(); }
    public void loadUrl(String url) { webView.loadUrl(url); }
    public CustomWebView getWebView() { return webView; }
}
