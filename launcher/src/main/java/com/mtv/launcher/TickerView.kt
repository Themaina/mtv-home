package com.mtv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * A finance-channel ticker: coloured segments scrolling right to left forever.
 * Draws with Paint (no layout per frame) and stops animating when hidden.
 */
class TickerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    data class Segment(val text: String, val color: Int = 0xE6FFFFFF.toInt(), val bold: Boolean = false)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = context.dp(13).toFloat() }
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private var segments: List<Segment> = emptyList()
    private var widths = FloatArray(0)
    private var total = 0f
    private var offset = 0f
    private var lastFrame = 0L
    private val speedPxPerSec = context.dp(46).toFloat()
    private val gap = context.dp(28).toFloat()

    fun setSegments(list: List<Segment>) {
        if (list == segments) return
        segments = list
        widths = FloatArray(list.size) { i -> measure(list[i]) }
        total = widths.sum() + gap * list.size
        invalidate()
    }

    private fun measure(s: Segment): Float {
        paint.typeface = if (s.bold) medium else regular
        return paint.measureText(s.text)
    }

    override fun onDraw(canvas: Canvas) {
        if (segments.isEmpty() || total <= 0f) return
        val now = System.nanoTime()
        if (lastFrame != 0L) offset = (offset + speedPxPerSec * (now - lastFrame) / 1e9f) % total
        lastFrame = now
        val baseline = height / 2f - (paint.descent() + paint.ascent()) / 2f
        var x = -offset
        // Draw enough copies to cover the width, so the loop is seamless.
        while (x < width) {
            for (i in segments.indices) {
                val s = segments[i]
                if (x + widths[i] > 0 && x < width) {
                    paint.typeface = if (s.bold) medium else regular
                    paint.color = s.color
                    canvas.drawText(s.text, x, baseline, paint)
                }
                x += widths[i] + gap
            }
        }
        if (isShown) postInvalidateOnAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        lastFrame = 0L
        if (visibility == VISIBLE) postInvalidateOnAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrame = 0L
        postInvalidateOnAnimation()
    }
}
