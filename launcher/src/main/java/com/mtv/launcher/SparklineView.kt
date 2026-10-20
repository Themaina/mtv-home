package com.mtv.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** A day's price line, green when up and red when down. */
class SparklineView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2).toFloat().coerceAtLeast(2f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val area = Path()
    private var points: List<Double> = emptyList()
    private var progress = 1f
    private var animator: ValueAnimator? = null

    /** Shade under the line (used on the big chart). */
    var filled = false

    /** Sets the series; the line draws itself in when the data is new. */
    fun set(points: List<Double>, color: Int, animate: Boolean = true) {
        val changed = points != this.points
        this.points = points
        paint.color = color
        fill.shader = null
        if (changed && animate && points.size > 1) {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 700
                addUpdateListener { progress = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (points.size < 2) return
        val pad = paint.strokeWidth
        val w = width - 2 * pad
        val h = height - 2 * pad
        val lo = points.min()
        val range = (points.max() - lo).takeIf { it > 0 } ?: 1.0
        path.reset()
        val count = ((points.size - 1) * progress).toInt().coerceAtLeast(1)
        var lastX = pad
        for (i in 0..count) {
            val v = points[i]
            val x = pad + w * i / (points.size - 1)
            val y = pad + h - (h * (v - lo) / range).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            lastX = x
        }
        if (filled) {
            if (fill.shader == null) {
                fill.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), (paint.color and 0x00FFFFFF) or 0x55000000, paint.color and 0x00FFFFFF, Shader.TileMode.CLAMP)
            }
            area.reset()
            area.addPath(path)
            area.lineTo(lastX, height.toFloat())
            area.lineTo(pad, height.toFloat())
            area.close()
            canvas.drawPath(area, fill)
        }
        canvas.drawPath(path, paint)
    }
}
