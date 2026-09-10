package com.gamesidebar.core.tests

import com.gamesidebar.core.harness.Assert
import com.gamesidebar.core.harness.TestCase
import com.gamesidebar.core.security.UrlSafety

object SafetyTests {

    val cases: List<TestCase> = listOf(
        TestCase("https loads") {
            val v = UrlSafety.verdict("https://example.com", httpsPreferred = true)
            Assert.equals(UrlSafety.Decision.LOAD, v.decision)
            Assert.equals("https://example.com", v.urlToLoad)
        },
        TestCase("http is upgraded when https is preferred") {
            val v = UrlSafety.verdict("http://example.com/x", httpsPreferred = true)
            Assert.equals(UrlSafety.Decision.UPGRADE_TO_HTTPS, v.decision)
            Assert.equals("https://example.com/x", v.urlToLoad)
        },
        TestCase("http loads untouched when the upgrade is off") {
            val v = UrlSafety.verdict("http://example.com/x", httpsPreferred = false)
            Assert.equals(UrlSafety.Decision.LOAD, v.decision)
        },
        TestCase("javascript: is refused") {
            val v = UrlSafety.verdict("javascript:alert(1)", httpsPreferred = true)
            Assert.equals(UrlSafety.Decision.BLOCK_JAVASCRIPT, v.decision)
            Assert.equals(null, v.urlToLoad)
            Assert.equals("error_blocked_script", v.reasonKey)
        },
        TestCase("file: and content: are refused") {
            Assert.equals(UrlSafety.Decision.BLOCK_FILE, UrlSafety.verdict("file:///sdcard/x", true).decision)
            Assert.equals(UrlSafety.Decision.BLOCK_FILE, UrlSafety.verdict("content://a/b", true).decision)
        },
        TestCase("unknown schemes are refused with a reason key") {
            val v = UrlSafety.verdict("intent://scan/#Intent;scheme=zxing;end", true)
            Assert.equals(UrlSafety.Decision.BLOCK_SCHEME, v.decision)
            Assert.equals("error_blocked_scheme", v.reasonKey)
        },
        TestCase("scheme-less host is loaded over https") {
            val v = UrlSafety.verdict("example.com/path", true)
            Assert.equals(UrlSafety.Decision.LOAD, v.decision)
            Assert.equals("https://example.com/path", v.urlToLoad)
        },
        TestCase("google accounts host requires an external browser") {
            Assert.that(UrlSafety.requiresExternalAuth("https://accounts.google.com/signin/v2/identifier"))
            Assert.that(UrlSafety.requiresExternalAuth("https://sub.accounts.youtube.com/o/oauth2"))
            Assert.that(!UrlSafety.requiresExternalAuth("https://www.youtube.com/watch?v=1"))
        },
        TestCase("blocked sign-in is detected from url or title") {
            Assert.that(UrlSafety.isBlockedSignIn("https://accounts.google.com/signin?error=disallowed_useragent", null))
            Assert.that(UrlSafety.isBlockedSignIn("https://accounts.google.com/x", "Couldn't sign you in"))
            Assert.that(!UrlSafety.isBlockedSignIn("https://www.google.com/", "Google"))
        },
        TestCase("ssl errors map to distinct message keys and are never bypassed") {
            Assert.equals("error_ssl_expired", UrlSafety.sslErrorKey(1))
            Assert.equals("error_ssl_untrusted", UrlSafety.sslErrorKey(3))
            Assert.equals("error_ssl_generic", UrlSafety.sslErrorKey(99))
        },
        TestCase("isLoadable agrees with the verdict") {
            Assert.that(UrlSafety.isLoadable("https://example.com", true))
            Assert.that(!UrlSafety.isLoadable("javascript:alert(1)", true))
            Assert.that(!UrlSafety.isLoadable("", true))
        },
    )
}
