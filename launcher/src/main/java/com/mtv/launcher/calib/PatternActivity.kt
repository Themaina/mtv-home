package com.mtv.launcher.calib

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager

/** Full-screen raw pattern (no UI) — used to record the calibration clips and for quick checks. */
class PatternActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(PatternView(this, intent.getIntExtra("pattern", 0)))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean { finish(); return true }
}
