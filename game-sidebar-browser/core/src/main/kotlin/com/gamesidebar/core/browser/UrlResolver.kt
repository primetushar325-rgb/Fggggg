package com.gamesidebar.core.browser

import com.gamesidebar.core.model.SearchEngine

/**
 * Decides what the address bar should do with what the user typed.
 *
 * This is the single source of truth shared by the overlay panel and the in-app browser, so both
 * behave identically: a bare host or a URL is loaded, anything else is handed to the selected
 * search engine.
 */
object UrlResolver {

    /** Result of classifying raw address-bar input. */
    sealed interface Resolution {
        data object Empty : Resolution
        /** Load this absolute URL. */
        data class Url(val url: String) : Resolution
        /** Search for this text with the active engine. */
        data class Search(val query: String) : Resolution
    }

    private val SCHEME_REGEX = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://")
    private val SCHEME_WORD_REGEX = Regex("[a-zA-Z][a-zA-Z0-9+.-]*")

    /**
     * Schemes that are valid without the `//` authority part. Without this allow-list,
     * "localhost:3000" or "example.com:8080" would be mistaken for "localhost:" / "example.com:"
     * schemes and routed away from the WebView.
     */
    private val OPAQUE_SCHEMES = setOf(
        "about", "blob", "content", "data", "file", "filesystem", "intent",
        "javascript", "mailto", "market", "sms", "tel", "view-source",
    )

    private val HOST_REGEX = Regex(
        "^([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}(:\\d{1,5})?(/\\S*)?$",
        RegexOption.IGNORE_CASE,
    )
    private val IPV4_REGEX = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d{1,5})?(/\\S*)?$")
    private val LOCAL_HOST_REGEX = Regex("^(localhost|10\\.0\\.2\\.2)(:\\d{1,5})?(/\\S*)?$", RegexOption.IGNORE_CASE)

    fun resolve(rawInput: String, engine: SearchEngine): Resolution {
        val input = rawInput.trim()
        if (input.isEmpty()) return Resolution.Empty

        // Anything carrying a scheme is treated as a URL, even unsafe ones: the app layer
        // (UrlSafety) is what refuses file://, javascript: and friends, so a user who types
        // "file://x" sees "scheme not supported" instead of a web search for that text.
        if (schemeOf(input) != null) return Resolution.Url(input)

        val candidate = input.substringBefore(' ')
        // No `.contains('.')` guard here: "localhost:3000" is a URL too. Each regex below already
        // requires whatever shape it accepts, so a bare word still falls through to search.
        val looksLikeHost = HOST_REGEX.matches(candidate) ||
            IPV4_REGEX.matches(candidate) ||
            LOCAL_HOST_REGEX.matches(candidate)

        return if (looksLikeHost && !input.contains(' ')) {
            Resolution.Url("https://$input")
        } else {
            Resolution.Search(input)
        }
    }

    /** "https" for "HTTPS://x.y", null when the input carries no usable scheme. */
    fun schemeOf(input: String): String? {
        val trimmed = input.trim()
        SCHEME_REGEX.find(trimmed)?.let { return it.groupValues[1].lowercase() }
        val colon = trimmed.indexOf(':')
        if (colon <= 0) return null
        val candidate = trimmed.substring(0, colon)
        if (!SCHEME_WORD_REGEX.matches(candidate)) return null
        val lower = candidate.lowercase()
        return if (lower in OPAQUE_SCHEMES) lower else null
    }

    fun hasScheme(input: String): Boolean = schemeOf(input) != null

    /**
     * Turns a resolved host/URL into something a WebView can load: scheme is added when missing.
     * Relative input is treated as a search term, never as a path.
     */
    fun absoluteUrl(input: String, engine: SearchEngine = SearchEngine.GOOGLE): String =
        when (val r = resolve(input, engine)) {
            is Resolution.Url -> r.url
            is Resolution.Search -> engine.searchUrl(encodeQuery(r.query))
            Resolution.Empty -> engine.homeUrl
        }

    /** Percent-encodes free text for use inside a query string. */
    fun encodeQuery(text: String): String {
        val sb = StringBuilder(text.length + 8)
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt().toChar()
            val safe = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '~'
            if (safe) {
                sb.append(c)
            } else {
                sb.append('%').append(HEX[(byte.toInt() shr 4) and 0x0F]).append(HEX[byte.toInt() and 0x0F])
            }
        }
        return sb.toString()
    }

    private val HEX = "0123456789ABCDEF".toCharArray()

    /** Host without credentials, port, path or query, lowercased; null when there is none. */
    fun hostOf(url: String): String? {
        val withoutScheme = if (hasScheme(url)) url.substringAfter("://", url) else url
        val authority = withoutScheme.substringBefore('/', "").substringBefore('?').substringBefore('#')
        val noCredentials = authority.substringAfterLast('@')
        val host = if (noCredentials.startsWith("[")) {
            noCredentials.substringBefore(']', noCredentials).removePrefix("[")
        } else {
            noCredentials.substringBefore(':')
        }
        return host.trim().lowercase().ifEmpty { null }
    }

    /** "https://www.example.com/path/" -> "www.example.com/path" for the compact URL bar. */
    fun displayUrl(url: String): String {
        if (url.isBlank()) return ""
        var out = url.substringAfter("://", url)
        if (out.endsWith("/")) out = out.dropLast(1)
        return out.ifBlank { url }
    }

    /** Drops fragment and trailing slash, lowercases the host: the key used for dedupe/bookmarks. */
    fun canonicalKey(url: String): String {
        val withoutFragment = url.substringBefore('#')
        val scheme = schemeOf(withoutFragment) ?: return withoutFragment.trimEnd('/').lowercase()
        val rest = withoutFragment.substringAfter("://", withoutFragment)
        val host = rest.substringBefore('/', "").lowercase().trimEnd('/')
        val tail = if (rest.length > host.length) rest.substring(host.length).trimEnd('/') else ""
        val normalizedScheme = if (scheme == "http") "https" else scheme
        return "$normalizedScheme://$host$tail".trimEnd('/')
    }
}
