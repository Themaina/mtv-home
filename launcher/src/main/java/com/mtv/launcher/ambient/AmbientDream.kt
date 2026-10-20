package com.mtv.launcher.ambient

import android.service.dreams.DreamService

/** The TV's screensaver (Settings › Device preferences › Screen saver, or set by the installer). */
class AmbientDream : DreamService() {
    private var view: AmbientView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false // any remote button wakes the TV
        isFullscreen = true
        isScreenBright = true
        view = AmbientView(this).also { setContentView(it) }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        view?.start()
    }

    override fun onDreamingStopped() {
        view?.stop()
        super.onDreamingStopped()
    }
}
