package com.mtv.launcher.calib

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * Calibration test patterns, drawn in exact RGB at the panel's 1280×720. Key content stays in
 * the top two-thirds so the instruction panel never covers it. Recorded once as video clips
 * (res/raw/cal_*.mp4) so they pass through the TV's picture engine like real films.
 */
class PatternView(context: Context, private val pattern: Int) : View(context) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        label.textSize = h * 0.03f
        when (pattern) {
            BLACK -> blackLevel(c, w, h)
            WHITE -> whiteLevel(c, w, h)
            GREY -> greyscale(c, w, h)
            COLOUR -> colour(c, w, h)
            SHARP -> sharpness(c, w, h)
            else -> reference(c, w, h)
        }
    }

    private fun grey(v: Int) = Color.rgb(v, v, v)

    /** Bars just above black (≈1–8%) on true black. Brightness right: 1% hidden, 2% just visible. */
    private fun blackLevel(c: Canvas, w: Float, h: Float) {
        c.drawColor(Color.BLACK)
        val levels = intArrayOf(3, 5, 8, 13, 20)
        val names = arrayOf("1%", "2%", "3%", "5%", "8%")
        bars(c, w, h, levels, names, 0xFF5A5A5A.toInt())
    }

    /** Near-white bars (92–99%) on full white. Contrast right: all bars still separate. */
    private fun whiteLevel(c: Canvas, w: Float, h: Float) {
        c.drawColor(Color.WHITE)
        val levels = intArrayOf(235, 240, 245, 250, 253)
        val names = arrayOf("92%", "94%", "96%", "98%", "99%")
        bars(c, w, h, levels, names, 0xFF8C8C8C.toInt())
    }

    private fun bars(c: Canvas, w: Float, h: Float, levels: IntArray, names: Array<String>, labelColor: Int) {
        val bw = w * 0.11f; val gap = w * 0.04f
        val total = levels.size * bw + (levels.size - 1) * gap
        var x = (w - total) / 2; val top = h * 0.12f; val bottom = h * 0.58f
        label.color = labelColor
        levels.forEachIndexed { i, v ->
            p.color = grey(v); c.drawRect(x, top, x + bw, bottom, p)
            c.drawText(names[i], x + bw / 2, bottom + h * 0.05f, label)
            x += bw + gap
        }
    }

    /** 11 steps 0–100% and a smooth ramp: greys should look neutral with no colour tint or banding. */
    private fun greyscale(c: Canvas, w: Float, h: Float) {
        c.drawColor(grey(30))
        val sw = w / 11f
        for (i in 0..10) { p.color = grey((i * 255) / 10); c.drawRect(i * sw, h * 0.06f, (i + 1) * sw, h * 0.34f, p) }
        val steps = 256
        val rw = w / steps
        for (i in 0 until steps) { p.color = grey(i); c.drawRect(i * rw, h * 0.38f, (i + 1) * rw + 1, h * 0.62f, p) }
    }

    /** Saturation ramps for each primary/secondary, 10–100%. Colour right: top steps still distinct. */
    private fun colour(c: Canvas, w: Float, h: Float) {
        c.drawColor(grey(40))
        val hues = intArrayOf(0, 120, 240, 180, 300, 60)
        val rows = hues.size; val rh = h * 0.6f / rows; val sw = w * 0.9f / 10; val x0 = w * 0.05f
        hues.forEachIndexed { r, hue ->
            for (s in 1..10) {
                p.color = Color.HSVToColor(floatArrayOf(hue.toFloat(), s / 10f, 1f))
                c.drawRect(x0 + (s - 1) * sw, h * 0.04f + r * rh, x0 + s * sw - 2, h * 0.04f + (r + 1) * rh - 3, p)
            }
        }
    }

    /** Fine lines on mid-grey. Sharpness right: no bright halos/outlines beside the black lines. */
    private fun sharpness(c: Canvas, w: Float, h: Float) {
        c.drawColor(grey(128))
        p.style = Paint.Style.STROKE
        for (i in 0 until 12) {
            p.strokeWidth = if (i % 2 == 0) 1f else 2f
            p.color = if (i % 3 == 0) Color.WHITE else Color.BLACK
            val inset = 30f + i * 22f
            c.drawRect(RectF(w * 0.06f + inset, h * 0.06f + inset * 0.5f, w * 0.5f - inset * 0.3f, h * 0.64f - inset * 0.5f), p)
        }
        p.strokeWidth = 2f; p.color = Color.BLACK
        c.drawCircle(w * 0.75f, h * 0.35f, h * 0.24f, p)
        p.strokeWidth = 1f
        c.drawCircle(w * 0.75f, h * 0.35f, h * 0.16f, p)
        p.style = Paint.Style.FILL
        for (i in 0 until 40) { // 1-pixel vertical line burst
            p.color = if (i % 2 == 0) Color.BLACK else Color.WHITE
            c.drawRect(w * 0.62f + i * 2, h * 0.02f, w * 0.62f + i * 2 + 1, h * 0.08f, p)
        }
        label.color = Color.BLACK; label.textSize = h * 0.022f
        c.drawText("The quick brown fox jumps over the lazy dog", w * 0.75f, h * 0.66f, label)
    }

    /** 75% colour bars plus skin tones and a neutral grey: should look natural, not green or magenta. */
    private fun reference(c: Canvas, w: Float, h: Float) {
        c.drawColor(grey(20))
        val bars = intArrayOf(0xBFBFBF, 0xBFBF00, 0x00BFBF, 0x00BF00, 0xBF00BF, 0xBF0000, 0x0000BF)
        val bw = w / bars.size
        bars.forEachIndexed { i, rgb -> p.color = 0xFF000000.toInt() or rgb; c.drawRect(i * bw, 0f, (i + 1) * bw, h * 0.32f, p) }
        val skins = intArrayOf(0xF3D3C1, 0xE8B897, 0xD39E7A, 0xB07D5C, 0x8D5A3C, 0x5E3A26)
        val sw = w * 0.8f / skins.size
        skins.forEachIndexed { i, rgb -> p.color = 0xFF000000.toInt() or rgb; c.drawRoundRect(RectF(w * 0.1f + i * sw + 6, h * 0.38f, w * 0.1f + (i + 1) * sw - 6, h * 0.6f), 18f, 18f, p) }
        label.color = 0xFFB0B0B0.toInt()
        c.drawText("Skin tones", w / 2, h * 0.65f, label)
    }

    companion object {
        const val BLACK = 0; const val WHITE = 1; const val GREY = 2; const val COLOUR = 3; const val SHARP = 4; const val REFERENCE = 5
        val CLIPS = intArrayOf(BLACK, WHITE, GREY, COLOUR, SHARP, REFERENCE)
    }
}
