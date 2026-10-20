package com.mtv.launcher.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.mtv.launcher.notify.NotificationService

data class Track(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val position: Long,
    val duration: Long,
    val updatedAt: Long,
)

/**
 * Follows whatever app is playing (YouTube Music, Spotify, YouTube…) through Android's
 * media sessions. Allowed because our notification listener is enabled.
 */
object NowPlaying {

    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<() -> Unit>()
    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var appContext: Context? = null

    @Volatile var track: Track? = null
        private set

    fun observe(context: Context, l: () -> Unit) {
        listeners += l
        start(context.applicationContext)
        l()
    }

    fun stopObserving(l: () -> Unit) { listeners -= l }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()

    fun previous() = controller?.transportControls?.skipToPrevious()

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() { pick(manager?.let { sessions(it) }.orEmpty()) }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }

    private fun start(context: Context) {
        if (manager != null) return
        appContext = context
        val m = context.getSystemService(MediaSessionManager::class.java) ?: return
        runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent(context), main)
            manager = m
            pick(sessions(m))
        }
    }

    private fun listenerComponent(context: Context) = ComponentName(context, NotificationService::class.java)

    private fun sessions(m: MediaSessionManager): List<MediaController> =
        runCatching { m.getActiveSessions(listenerComponent(appContext!!)) }.getOrDefault(emptyList())

    /** Prefer whatever is playing; otherwise the most recent session. */
    private fun pick(list: List<MediaController>) {
        val chosen = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = chosen
            chosen?.registerCallback(callback, main)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val md = c?.metadata
        val state = c?.playbackState
        track = if (c == null || md == null || state == null ||
            state.state == PlaybackState.STATE_STOPPED || state.state == PlaybackState.STATE_NONE) {
            null
        } else {
            val pm = appContext?.packageManager
            Track(
                packageName = c.packageName,
                appLabel = runCatching { pm!!.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString() }.getOrDefault(c.packageName),
                title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "",
                artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "",
                art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
                playing = state.state == PlaybackState.STATE_PLAYING,
                position = state.position,
                duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
                updatedAt = state.lastPositionUpdateTime,
            ).takeIf { it.title.isNotBlank() }
        }
        listeners.toList().forEach { it() }
    }
}
