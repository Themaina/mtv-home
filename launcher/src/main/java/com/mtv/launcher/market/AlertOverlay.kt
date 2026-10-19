package com.mtv.launcher.market

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.mtv.launcher.R
import com.mtv.launcher.dp

/**
 * A small banner in the top-right corner over whatever is playing. It never takes focus
 * or touches, so the remote keeps controlling the app underneath. Needs the
 * "display over other apps" permission, which the installer grants over adb.
 */
object AlertOverlay {

    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<MarketAlert>()
    private var showing: View? = null

    fun show(context: Context, alert: MarketAlert) = main.post {
        if (!Settings.canDrawOverlays(context)) return@post
        queue.addLast(alert)
        if (showing == null) next(context.applicationContext)
    }

    private fun next(context: Context) {
        val alert = queue.removeFirstOrNull() ?: return
        val wm = context.getSystemService(WindowManager::class.java)
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_alert, null)
        view.findViewById<TextView>(R.id.alert_title).text = alert.title
        view.findViewById<TextView>(R.id.alert_detail).text = alert.detail
        view.findViewById<View>(R.id.alert_bar).setBackgroundColor(
            when (alert.positive) { true -> 0xFF30D158.toInt(); false -> 0xFFFF453A.toInt(); null -> 0xFFFFD60A.toInt() },
        )
        val params = WindowManager.LayoutParams(
            context.dp(300), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = context.dp(24)
            y = context.dp(24)
        }
        try {
            wm.addView(view, params)
        } catch (e: RuntimeException) {
            return
        }
        showing = view
        view.alpha = 0f
        view.translationX = context.dp(40).toFloat()
        view.animate().alpha(1f).translationX(0f).setDuration(250).start()
        main.postDelayed({
            view.animate().alpha(0f).setDuration(300).withEndAction {
                runCatching { wm.removeView(view) }
                showing = null
                next(context)
            }.start()
        }, SHOW_MS)
    }

    private const val SHOW_MS = 9_000L
}
