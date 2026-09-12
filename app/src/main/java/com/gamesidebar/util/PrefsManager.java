package com.gamesidebar.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SAFE TARGETED PATCH: Preserve existing persistence mechanism (SharedPreferences).
 * Do not introduce DB. Save only on drag end / resize end / collapse / lifecycle.
 * Fixes state persistence for X,Y,width,height,tab,url,collapsed,autoHide.
 */
public class PrefsManager {
    private static final String PREFS = "game_sidebar_prefs";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_W = "width";
    private static final String KEY_H = "height";
    private static final String KEY_COLLAPSED = "collapsed";
    private static final String KEY_URL = "current_url";
    private static final String KEY_TAB = "active_tab";
    private static final String KEY_AUTO_HIDE = "auto_hide_controls";

    private final SharedPreferences sp;

    public PrefsManager(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // Called ONLY on drag end / resize end / collapse / lifecycle - not per pixel
    public void saveBounds(int x, int y, int w, int h) {
        sp.edit().putInt(KEY_X, x).putInt(KEY_Y, y).putInt(KEY_W, w).putInt(KEY_H, h).apply();
    }

    public void saveCollapsed(boolean collapsed) {
        sp.edit().putBoolean(KEY_COLLAPSED, collapsed).apply();
    }

    public void saveUrl(String url) { sp.edit().putString(KEY_URL, url).apply(); }
    public void saveTab(int tab) { sp.edit().putInt(KEY_TAB, tab).apply(); }
    public void setAutoHide(boolean v) { sp.edit().putBoolean(KEY_AUTO_HIDE, v).apply(); }

    public int getX(int def) { return sp.getInt(KEY_X, def); }
    public int getY(int def) { return sp.getInt(KEY_Y, def); }
    public int getW(int def) { return sp.getInt(KEY_W, def); }
    public int getH(int def) { return sp.getInt(KEY_H, def); }
    public boolean isCollapsed() { return sp.getBoolean(KEY_COLLAPSED, false); }
    public String getUrl(String def) { return sp.getString(KEY_URL, def); }
    public int getTab(int def) { return sp.getInt(KEY_TAB, def); }
    public boolean isAutoHide() { return sp.getBoolean(KEY_AUTO_HIDE, true); } // DEFAULT ON
}
