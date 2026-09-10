package com.gamesidebar.core.tests

import com.gamesidebar.core.download.Downloads
import com.gamesidebar.core.harness.Assert
import com.gamesidebar.core.harness.TestCase

object DownloadTests {

    val cases: List<TestCase> = listOf(
        TestCase("plain filename parameter") {
            Assert.equals("report.pdf", Downloads.fileNameFromContentDisposition("attachment; filename=report.pdf"))
        },
        TestCase("quoted filename with spaces") {
            Assert.equals(
                "match notes.txt",
                Downloads.fileNameFromContentDisposition("attachment; filename=\"match notes.txt\""),
            )
        },
        TestCase("RFC 5987 encoded filename wins and is decoded") {
            Assert.equals(
                "a b.pdf",
                Downloads.fileNameFromContentDisposition("attachment; filename=\"x.pdf\"; filename*=UTF-8''a%20b.pdf"),
            )
        },
        TestCase("missing or blank header yields null") {
            Assert.equals(null, Downloads.fileNameFromContentDisposition(null))
            Assert.equals(null, Downloads.fileNameFromContentDisposition("attachment"))
        },
        TestCase("filename from url ignores query and fragment") {
            Assert.equals("clip.mp4", Downloads.fileNameFromUrl("https://cdn.test/a/clip.mp4?token=1#t=2"))
            Assert.equals(null, Downloads.fileNameFromUrl("https://cdn.test/a/"))
            Assert.equals(null, Downloads.fileNameFromUrl(""))
        },
        TestCase("percent-encoded url names are decoded") {
            Assert.equals("my file.zip", Downloads.fileNameFromUrl("https://cdn.test/my%20file.zip"))
        },
        TestCase("path traversal is stripped") {
            Assert.equals("passwd", Downloads.sanitizeFileName("../../etc/passwd"))
            Assert.equals("download", Downloads.sanitizeFileName(".."))
        },
        TestCase("illegal and control characters are replaced") {
            Assert.equals("c.txt", Downloads.sanitizeFileName("a/b\\c.txt"), "path components are dropped")
            Assert.equals("a_b_c.txt", Downloads.sanitizeFileName("a?b*c.txt"))
            Assert.equals("ab.txt", Downloads.sanitizeFileName("a\u0000b.txt"))
            Assert.equals("q____.txt", Downloads.sanitizeFileName("q\"<>|.txt"))
        },
        TestCase("reserved windows names get a prefix") {
            Assert.equals("download_con.txt", Downloads.sanitizeFileName("con.txt"))
        },
        TestCase("very long names are truncated but keep the extension") {
            val long = "a".repeat(400) + ".mp4"
            val safe = Downloads.sanitizeFileName(long)
            Assert.that(safe.length <= Downloads.MAX_NAME_LENGTH) { "too long: ${safe.length}" }
            Assert.that(safe.endsWith(".mp4")) { "extension lost: $safe" }
        },
        TestCase("blank names fall back") {
            Assert.equals("download", Downloads.sanitizeFileName("   "))
            Assert.equals("page.bin", Downloads.sanitizeFileName("", fallback = "page.bin"))
        },
        TestCase("name resolution prefers the header, then the url, then the guess") {
            Assert.equals(
                "a.pdf",
                Downloads.resolveFileName("attachment; filename=a.pdf", "https://x.test/b.bin", "application/pdf", "guess.pdf"),
            )
            Assert.equals(
                "b.bin",
                Downloads.resolveFileName(null, "https://x.test/b.bin", "application/pdf", "guess.pdf"),
            )
            Assert.equals(
                "guess.pdf",
                Downloads.resolveFileName(null, "https://x.test/", "application/pdf", "guess.pdf"),
            )
        },
        TestCase("a missing extension is filled from the mime type") {
            Assert.equals("file.pdf", Downloads.resolveFileName("attachment; filename=file", null, "application/pdf", null))
        },
        TestCase("mime types are guessed from the extension") {
            Assert.equals("application/pdf", Downloads.guessMimeType("a.pdf"))
            Assert.equals("video/mp4", Downloads.guessMimeType("a.MP4"))
            Assert.equals("application/octet-stream", Downloads.guessMimeType("a.unknownext"))
        },
        TestCase("target collection follows the mime type") {
            Assert.equals("Pictures", Downloads.subdirFor("image/png", "a.png"))
            Assert.equals("Movies", Downloads.subdirFor("video/mp4", "a.mp4"))
            Assert.equals("Music", Downloads.subdirFor("audio/mpeg", "a.mp3"))
            Assert.equals("Download", Downloads.subdirFor("application/zip", "a.zip"))
            Assert.equals("Pictures", Downloads.subdirFor("application/octet-stream", "a.webp"))
        },
        TestCase("byte formatting is readable") {
            Assert.equals("Unknown size", Downloads.formatBytes(-1))
            Assert.equals("512 B", Downloads.formatBytes(512))
            Assert.equals("1.0 KB", Downloads.formatBytes(1024))
            Assert.equals("1.5 MB", Downloads.formatBytes(1_572_864))
        },
        TestCase("mime parameters are ignored when guessing") {
            Assert.equals("pdf", Downloads.extensionForMime("application/pdf; charset=utf-8"))
            Assert.equals("bin", Downloads.extensionForMime("application/x-mystery"))
        },
    )
}
