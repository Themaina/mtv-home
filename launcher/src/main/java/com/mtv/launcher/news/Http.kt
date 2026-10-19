package com.mtv.launcher.news

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Tiny HTTP GET with manual redirects (HttpURLConnection won't follow http↔https). */
object Http {
    /** Browser-like, so news sites serve their normal pages. */
    const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 11; SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"

    /** Plain app identifier; Yahoo Finance rate-limits browser-looking clients that aren't browsers. */
    const val APP_UA = "mtv-home/0.3.1"

    fun get(url: String, maxBytes: Int = 3 * 1024 * 1024, userAgent: String = BROWSER_UA): ByteArray {
        var current = url
        repeat(4) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept-Language", "en")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    current = URL(URL(current), conn.getHeaderField("Location") ?: throw IOException("redirect without Location")).toString()
                    return@repeat
                }
                if (code !in 200..299) throw IOException("HTTP $code for $current")
                conn.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > maxBytes) break
                    }
                    return out.toByteArray()
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("too many redirects for $url")
    }
}
