package com.gamesidebar.browser;

import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

/**
 * FIX video mode randomly closing + 0.1s reset loop.
 * Root cause: duplicate onShowCustomView/onHideCustomView callbacks, repeated handler loops,
 * fullscreen toggles, WebView recreation, overlay visibility changes.
 * Solution: ONE reliable state, boolean guard, ignore duplicate callbacks, NO loops.
 *
 * States: NORMAL -> VIDEO_MODE -> NORMAL, NORMAL -> COLLAPSED -> NORMAL
 * Never VIDEO_MODE -> COLLAPSED automatically.
 */
public class VideoModeManager {

    public enum State { NORMAL, VIDEO_MODE, COLLAPSED }

    private State current = State.NORMAL;
    private boolean guard = false; // prevents rapid hide/show loop
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;

    private final Runnable onEnterVideo;
    private final Runnable onExitVideo;

    public VideoModeManager(Runnable onEnter, Runnable onExit) {
        this.onEnterVideo = onEnter;
        this.onExitVideo = onExit;
    }

    public synchronized boolean isVideoMode() { return current == State.VIDEO_MODE; }
    public synchronized State getState() { return current; }

    public synchronized void enterVideoMode(View view, WebChromeClient.CustomViewCallback callback) {
        if (guard) return; // ignore duplicate callback within guard window
        if (current == State.VIDEO_MODE) return; // already in video, ignore duplicate
        guard = true;
        current = State.VIDEO_MODE;
        customView = view;
        customCallback = callback;
        if (onEnterVideo != null) onEnterVideo.run();
        // release guard after short delay but NOT via loop; single post
        if (view != null) view.postDelayed(() -> guard = false, 500);
        else guard = false;
    }

    public synchronized void exitVideoMode() {
        if (guard && current != State.VIDEO_MODE) {
            // still allow exit if we are exiting video even during guard
        }
        if (current != State.VIDEO_MODE) return;
        if (guard) {
            // if guard active, still allow but prevent re-entry loop
        }
        guard = true;
        current = State.NORMAL;
        if (customCallback != null) {
            try { customCallback.onCustomViewHidden(); } catch (Exception ignored) {}
        }
        customView = null;
        customCallback = null;
        if (onExitVideo != null) onExitVideo.run();
        // guard reset single
        if (customView != null) customView.postDelayed(() -> guard = false, 500);
        else {
            // use a view from container - fallback immediate reset after 500 via handler on main thread
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.postDelayed(() -> guard = false, 500);
        }
    }

    public synchronized void setCollapsed(boolean collapsed) {
        if (collapsed) {
            // Never automatically VIDEO_MODE -> COLLAPSED unless user explicitly collapses
            // But if user explicitly collapses, we must exit video mode cleanly first
            if (current == State.VIDEO_MODE) {
                exitVideoMode();
            }
            current = State.COLLAPSED;
        } else {
            if (current == State.COLLAPSED) current = State.NORMAL;
        }
    }

    // Called from WebChromeClient to safely handle duplicates
    public WebChromeClient getWebChromeClient(WebView webView) {
        return new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                enterVideoMode(view, callback);
            }
            @Override
            public void onHideCustomView() {
                exitVideoMode();
            }
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
            }
        };
    }
}
