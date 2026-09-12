package com.gamesidebar;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gamesidebar.util.PrefsManager;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_OVERLAY = 1001;
    private PrefsManager prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new PrefsManager(this);

        Button btn = findViewById(R.id.btnStartOverlay);
        CheckBox cb = findViewById(R.id.cbAutoHide);
        cb.setChecked(prefs.isAutoHide());
        cb.setOnCheckedChangeListener((v, c) -> prefs.setAutoHide(c));

        btn.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, REQ_OVERLAY);
                Toast.makeText(this, R.string.grant_overlay_permission, Toast.LENGTH_LONG).show();
            } else {
                startOverlay();
            }
        });
    }

    private void startOverlay() {
        Intent i = new Intent(this, FloatingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
        // FIX: Do NOT finish with keyboard logic interference; keep activity
        Toast.makeText(this, "Sidebar opened over game", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startOverlay();
            }
        }
    }

    // FIX: Back priority - if handling not needed, just finish activity, not overlay
    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
