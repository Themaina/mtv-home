package com.mtv.launcher.img

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import com.mtv.launcher.news.Http
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Minimal image loader sized for a 1 GB TV: RGB_565, down-sampled to the view size,
 * a small memory cache, and a disk cache that survives reboots.
 */
object ImageLoader {

    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val memory = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private var diskDir: File? = null

    fun init(context: Context) {
        if (diskDir != null) return
        val dir = File(context.cacheDir, "img").apply { mkdirs() }
        diskDir = dir
        pool.execute {
            // Drop images older than three days.
            val cutoff = System.currentTimeMillis() - 3 * 24 * 3600 * 1000L
            dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        }
    }

    /** Loads [url] into [view] at roughly [widthPx]×[heightPx]. [onBitmap] also runs on success. */
    fun load(url: String?, view: ImageView, widthPx: Int, heightPx: Int, onBitmap: ((Bitmap) -> Unit)? = null) {
        view.tag = url
        view.setImageDrawable(null)
        get(url, widthPx, heightPx) { bitmap ->
            if (view.tag == url) {
                view.setImageBitmap(bitmap)
                onBitmap?.invoke(bitmap)
            }
        }
    }

    /** Delivers the bitmap on the main thread; never called if loading fails. */
    fun get(url: String?, widthPx: Int, heightPx: Int, callback: (Bitmap) -> Unit) {
        if (url.isNullOrBlank()) return
        val key = "$url@$widthPx"
        memory.get(key)?.let {
            callback(it)
            return
        }
        pool.execute {
            val bitmap = runCatching { decode(fetch(url), widthPx, heightPx) }.getOrNull() ?: return@execute
            memory.put(key, bitmap)
            main.post { callback(bitmap) }
        }
    }

    private fun fetch(url: String): ByteArray {
        val file = diskDir?.let { File(it, sha1(url)) }
        if (file != null && file.exists()) return file.readBytes()
        val bytes = Http.get(url, maxBytes = 4 * 1024 * 1024)
        runCatching { file?.writeBytes(bytes) }
        return bytes
    }

    private fun decode(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
