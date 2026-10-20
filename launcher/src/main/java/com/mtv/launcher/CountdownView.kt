package com.mtv.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextSwitcher
import android.widget.TextView

/**
 * Days · hours · minutes · seconds to a target time. Each unit slides when it changes,
 * like a departure board. Ticks only while attached to the window.
 */
class CountdownView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {

    private val handler = Handler(Looper.getMainLooper())
    private var target = 0L
    private var numberSize = 22f
    private var labelSize = 9f
    private val units = listOf("D", "H", "M", "S")
    private val switchers = mutableListOf<TextSwitcher>()
    private val shown = IntArray(4) { -1 }
    var onFinished: (() -> Unit)? = null

    private val tick = object : Runnable {
        override fun run() {
            render(animate = true)
            handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000)
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun setStyle(numberSp: Float, labelSp: Float) {
        numberSize = numberSp
        labelSize = labelSp
        removeAllViews()
        switchers.clear()
        units.forEachIndexed { i, unit ->
            val box = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(context.dp(if (i == 0) 0 else 6), 0, context.dp(6), 0)
            }
            val switcher = TextSwitcher(context).apply {
                setFactory {
                    TextView(context).apply {
                        textSize = numberSize
                        setTextColor(0xFFFFFFFF.toInt())
                        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                    }
                }
                inAnimation = AnimationUtils.loadAnimation(context, R.anim.digit_in)
                outAnimation = AnimationUtils.loadAnimation(context, R.anim.digit_out)
            }
            val label = TextView(context).apply {
                text = unit
                textSize = labelSize
                setTextColor(0x99FFFFFF.toInt())
                gravity = Gravity.CENTER
            }
            box.addView(switcher)
            box.addView(label)
            addView(box)
            switchers += switcher
        }
        shown.fill(-1)
        render(animate = false)
    }

    fun setTarget(millis: Long) {
        if (switchers.isEmpty()) setStyle(numberSize, labelSize)
        if (target != millis) {
            target = millis
            shown.fill(-1)
        }
        render(animate = false)
    }

    private fun render(animate: Boolean) {
        val left = ((target - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        val values = intArrayOf((left / 86400).toInt(), ((left / 3600) % 24).toInt(), ((left / 60) % 60).toInt(), (left % 60).toInt())
        values.forEachIndexed { i, v ->
            if (i < switchers.size && v != shown[i]) {
                val text = if (i == 0) v.toString() else "%02d".format(v)
                if (animate && shown[i] >= 0) switchers[i].setText(text) else switchers[i].setCurrentText(text)
                shown[i] = v
            }
        }
        if (left == 0L && target > 0) onFinished?.invoke()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }
}
