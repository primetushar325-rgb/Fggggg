package com.gamesidebar.core.security

import com.gamesidebar.core.browser.UrlResolver

/**
 * Every URL that reaches a WebView passes through here.
 *
 * The rules are deliberately boring: only http(s) is loaded in-panel, certificate errors are never
 * ignored, and sign-in flows that Google blocks inside a WebView are routed to Custom Tabs instead
 * of being worked around. Nothing here attempts to bypass DRM, anti-cheat, authentication or any
 * platform protection - the overlay is a plain floating utility.
 */
object UrlSafety {

    enum class Decision { LOAD, BLOCK_SCHEME, BLOCK_FILE, BLOCK_JAVASCRIPT, UPGRADE_TO_HTTPS }

    data class Verdict(val decision: Decision, val urlToLoad: String?, val reasonKey: String?)

    private val LOADABLE_SCHEMES = setOf("http", "https")

    /** Hosts where an embedded WebView login is refused by the provider; open externally instead. */
    private val EXTERNAL_AUTH_HOSTS = setOf(
        "accounts.google.com",
        "accounts.youtube.com",
        "accounts.google.co.in",
        "auth.google.com",
    )

    fun verdict(rawUrl: String, httpsPreferred: Boolean): Verdict {
        val url = rawUrl.trim()
        if (url.isEmpty()) return Verdict(Decision.BLOCK_SCHEME, null, "error_empty_url")

        val scheme = UrlResolver.schemeOf(url)
        if (scheme == null) {
            // No scheme: treat as an https host, which is what the address bar already produces.
            return Verdict(Decision.LOAD, "https://${url.trimEnd('/')}", null)
        }

        return when (scheme) {
            "https" -> Verdict(Decision.LOAD, url, null)
            "http" -> if (httpsPreferred) {
                Verdict(Decision.UPGRADE_TO_HTTPS, "https://" + url.removePrefix("http://"), null)
            } else {
                Verdict(Decision.LOAD, url, null)
            }

            "file", "content" -> Verdict(Decision.BLOCK_FILE, null, "error_blocked_file")
            "javascript" -> Verdict(Decision.BLOCK_JAVASCRIPT, null, "error_blocked_script")
            in LOADABLE_SCHEMES -> Verdict(Decision.LOAD, url, null)
            else -> Verdict(Decision.BLOCK_SCHEME, null, "error_blocked_scheme")
        }
    }

    fun isLoadable(rawUrl: String, httpsPreferred: Boolean): Boolean =
        verdict(rawUrl, httpsPreferred).decision.let { it == Decision.LOAD || it == Decision.UPGRADE_TO_HTTPS }

    /**
     * Google refuses OAuth inside third-party WebViews (`disallowed_useragent`). Rather than
     * spoofing an agent or storing credentials, the app offers a Custom Tab and returns.
     */
    fun requiresExternalAuth(url: String): Boolean {
        val host = UrlResolver.hostOf(url) ?: return false
        return EXTERNAL_AUTH_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    /**
     * Heuristic used to explain a failed sign-in instead of showing a blank page. Google answers
     * blocked embedded logins with "disallowed_useragent" in the redirect URL.
     */
    fun isBlockedSignIn(url: String, pageTitle: String?): Boolean {
        val lower = url.lowercase()
        if (lower.contains("disallowed_useragent")) return true
        if (lower.contains("accounts.google.com") && lower.contains("oauth") && lower.contains("error")) return true
        return pageTitle?.contains("couldn't sign you in", ignoreCase = true) == true
    }

    /** SSL errors are surfaced, never swallowed: this is the message key the UI shows. */
    fun sslErrorKey(primaryError: Int): String = when (primaryError) {
        0 -> "error_ssl_not_yet_valid"
        1 -> "error_ssl_expired"
        2 -> "error_ssl_id_mismatch"
        3 -> "error_ssl_untrusted"
        else -> "error_ssl_generic"
    }
}
