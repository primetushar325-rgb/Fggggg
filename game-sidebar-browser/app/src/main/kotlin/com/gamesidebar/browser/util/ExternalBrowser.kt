package com.gamesidebar.browser.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Connectivity + "leave the app" helpers.
 *
 * Two legitimate reasons to hand a URL to another app exist, and both are implemented here:
 *  1. Google refuses OAuth inside third-party WebViews, so sign-in goes to a Custom Tab;
 *  2. the user explicitly asks to open the page in their normal browser.
 *
 * Nothing is handed over silently, and no credentials are ever stored by this app.
 */
object ExternalBrowser {

    /** Opens in a Custom Tab when a browser supports it, otherwise falls back to ACTION_VIEW. */
    fun open(context: Context, url: String): Boolean {
        val uri = Uri.parse(url)
        return try {
            val intent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .intent
                .apply {
                    data = uri
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
            true
        } catch (customTabsUnavailable: ActivityNotFoundException) {
            openWithSystemBrowser(context, uri)
        } catch (security: SecurityException) {
            openWithSystemBrowser(context, uri)
        }
    }

    fun openWithSystemBrowser(context: Context, uri: Uri): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (notFound: ActivityNotFoundException) {
        false
    }

    fun share(context: Context, url: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (notFound: ActivityNotFoundException) {
            // No sharing target installed; nothing to recover from.
        }
    }
}

/**
 * Connectivity state for the error surface.
 *
 * A callback-based monitor rather than polling: registering once and updating a volatile flag costs
 * nothing while the overlay is idle, which matters for Gaming Mode.
 */
class NetworkMonitor(context: Context) {

    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    @Volatile
    var isOnline: Boolean = true
        private set

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            isOnline = true
        }

        override fun onLost(network: Network) {
            isOnline = capabilities()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            isOnline = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    fun start() {
        isOnline = capabilities()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        try {
            manager?.registerDefaultNetworkCallback(callback)
        } catch (tooMany: RuntimeException) {
            // The system caps callbacks per app; staying with the last known state is acceptable.
        }
    }

    fun stop() {
        try {
            manager?.unregisterNetworkCallback(callback)
        } catch (notRegistered: IllegalArgumentException) {
            // Already unregistered.
        }
    }

    private fun capabilities(): NetworkCapabilities? =
        manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) }
}
