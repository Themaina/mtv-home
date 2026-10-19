package com.mtv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
    private val path = Path()
    private var points: List<Double> = emptyList()

    fun set(points: List<Double>, color: Int) {
        this.points = points
        paint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (points.size < 2) return
        val pad = paint.strokeWidth
        val w = width - 2 * pad
        val h = height - 2 * pad
        val lo = points.min()
        val range = (points.max() - lo).takeIf { it > 0 } ?: 1.0
        path.reset()
        points.forEachIndexed { i, v ->
            val x = pad + w * i / (points.size - 1)
            val y = pad + h - (h * (v - lo) / range).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }
}
