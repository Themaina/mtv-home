package com.mtv.launcher.ambient

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager

/** Ambient mode on demand, from the Settings row. Any button closes it. */
class AmbientActivity : Activity() {
    private lateinit var view: AmbientView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = AmbientView(this)
        setContentView(view)
        view.start()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        view.stop()
        super.onDestroy()
    }
}
