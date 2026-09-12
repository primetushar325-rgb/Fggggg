package com.gamesidebar.browser;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebView;

/**
 * CRITICAL FIX: WebView scroll must not be stolen by parent sidebar.
 * Touch ownership: HEADER -> drag, RESIZE HANDLE -> resize, WEBVIEW CONTENT -> webpage scroll.
 * Previous bug: parent onInterceptTouchEvent / swipeDetector intercepted vertical gestures.
 * Fix: This WebView correctly claims touch; parent SidebarPanelView fixes interception rules.
 */
public class CustomWebView extends WebView {

    private boolean isVideoMode = false;

    public CustomWebView(Context context) { super(context); }
    public CustomWebView(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Ensure parent does NOT intercept when touching WebView content
        // Request parent to not intercept for move events so scroll works
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // Claim touch for WebView
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        return super.onTouchEvent(event);
    }

    public void setVideoMode(boolean v) { isVideoMode = v; }
    public boolean isVideoMode() { return isVideoMode; }
}
