package com.gamesidebar.core.tests

import com.gamesidebar.core.browser.UrlResolver
import com.gamesidebar.core.harness.Assert
import com.gamesidebar.core.harness.TestCase
import com.gamesidebar.core.model.SearchEngine

object UrlResolverTests {

    val cases: List<TestCase> = listOf(
        TestCase("blank input is empty") {
            Assert.equals(UrlResolver.Resolution.Empty, UrlResolver.resolve("   ", SearchEngine.GOOGLE))
        },
        TestCase("absolute https url is loaded as-is") {
            val r = UrlResolver.resolve("https://youtube.com/watch?v=1", SearchEngine.GOOGLE)
            Assert.equals(UrlResolver.Resolution.Url("https://youtube.com/watch?v=1"), r)
        },
        TestCase("uppercase scheme is recognised") {
            Assert.equals("https", UrlResolver.schemeOf("HTTPS://Example.com/a"))
        },
        TestCase("bare host gets https") {
            Assert.equals(
                UrlResolver.Resolution.Url("https://web.whatsapp.com"),
                UrlResolver.resolve("web.whatsapp.com", SearchEngine.GOOGLE),
            )
        },
        TestCase("host with path and port is a url") {
            Assert.equals(
                UrlResolver.Resolution.Url("https://192.168.0.5:8080/index.html"),
                UrlResolver.resolve("192.168.0.5:8080/index.html", SearchEngine.GOOGLE),
            )
        },
        TestCase("localhost is a url, not a search") {
            Assert.equals(
                UrlResolver.Resolution.Url("https://localhost:3000"),
                UrlResolver.resolve("localhost:3000", SearchEngine.GOOGLE),
            )
        },
        TestCase("plain text becomes a search") {
            Assert.equals(
                UrlResolver.Resolution.Search("best gaming headsets"),
                UrlResolver.resolve("best gaming headsets", SearchEngine.GOOGLE),
            )
        },
        TestCase("text containing a dot and a space is still a search") {
            val r = UrlResolver.resolve("patch notes 1.2 fps", SearchEngine.GOOGLE)
            Assert.that(r is UrlResolver.Resolution.Search) { "expected search, got $r" }
        },
        TestCase("google search url is built and encoded") {
            val url = UrlResolver.absoluteUrl("hello world & more", SearchEngine.GOOGLE)
            Assert.equals("https://www.google.com/search?q=hello%20world%20%26%20more", url)
        },
        TestCase("duckduckgo and bing templates are used") {
            Assert.equals(
                "https://duckduckgo.com/?q=kotlin",
                UrlResolver.absoluteUrl("kotlin", SearchEngine.DUCKDUCKGO),
            )
            Assert.equals("https://www.bing.com/search?q=kotlin", UrlResolver.absoluteUrl("kotlin", SearchEngine.BING))
        },
        TestCase("empty input falls back to the engine home page") {
            Assert.equals("https://duckduckgo.com/", UrlResolver.absoluteUrl("", SearchEngine.DUCKDUCKGO))
        },
        TestCase("query encoding covers unicode") {
            Assert.equals("%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82", UrlResolver.encodeQuery("привет"))
        },
        TestCase("host extraction ignores credentials, port and query") {
            Assert.equals("example.com", UrlResolver.hostOf("https://user:pw@example.com:8443/a?b=1#c"))
        },
        TestCase("display url drops scheme and trailing slash") {
            Assert.equals("www.google.com", UrlResolver.displayUrl("https://www.google.com/"))
        },
        TestCase("canonical key normalises scheme, case and trailing slash") {
            Assert.equals(
                UrlResolver.canonicalKey("https://Example.COM/a/b/"),
                UrlResolver.canonicalKey("http://example.com/a/b"),
            )
        },
        TestCase("canonical key keeps query strings distinct") {
            Assert.notEquals(
                UrlResolver.canonicalKey("https://example.com/a?x=1"),
                UrlResolver.canonicalKey("https://example.com/a?x=2"),
            )
        },
        TestCase("file scheme is not turned into a search") {
            val r = UrlResolver.resolve("file:///sdcard/a.txt", SearchEngine.GOOGLE)
            Assert.equals(UrlResolver.Resolution.Url("file:///sdcard/a.txt"), r)
        },
    )
}
