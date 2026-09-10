package com.gamesidebar.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.gamesidebar.browser.ui.browser.BrowserScreen
import com.gamesidebar.browser.ui.theme.GameSidebarColors
import com.gamesidebar.browser.ui.theme.GameSidebarTheme

/**
 * In-app browser.
 *
 * Also registered as a VIEW handler for http/https, so "Open with Game SideBar" works from other
 * apps - and, more importantly, so the app remains a complete browser for users who never grant the
 * overlay permission.
 */
class BrowserActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val url = when (intent?.action) {
            Intent.ACTION_VIEW -> intent?.dataString
            else -> intent?.getStringExtra(EXTRA_URL)
        }
        setContent {
            GameSidebarTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = GameSidebarColors.Background) {
                    BrowserScreen(initialUrl = url)
                }
            }
        }
    }

    companion object {
        const val EXTRA_URL = "com.gamesidebar.browser.extra.URL"

        fun start(context: Context, url: String? = null) {
            val intent = Intent(context, BrowserActivity::class.java).apply {
                url?.let { putExtra(EXTRA_URL, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
