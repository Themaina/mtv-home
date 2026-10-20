package com.mtv.launcher

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.format.DateFormat
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.widget.TextView
import java.util.Date

/**
 * Inter (SIL Open Font License, see docs/licenses/Inter-OFL.txt) for clocks and countdowns:
 * bolder than Roboto Light, with tabular figures so digits don't shift as time ticks.
 */
object Fonts {
    @Volatile private var inter: Typeface? = null

    private fun inter(view: TextView): Typeface =
        inter ?: view.resources.getFont(R.font.inter).also { inter = it }

    /** "4:24" in full weight with a small, dimmer "AM" (12-hour clocks only). */
    fun time(context: Context, date: Date = Date()): CharSequence {
        val text = DateFormat.getTimeFormat(context).format(date)
        val marker = Regex("\\s?([AaPp]\\.?\\s?[Mm]\\.?)$").find(text) ?: return text
        val span = SpannableString(text)
        span.setSpan(RelativeSizeSpan(0.42f), marker.range.first, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(ForegroundColorSpan(0xB3FFFFFF.toInt()), marker.range.first, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return span
    }

    /** [weight] 100–900; [opticalSize] matches Inter's opsz axis (14–32) to the display size. */
    fun clock(view: TextView, weight: Int, opticalSize: Int = 32, tracking: Float = -0.02f) {
        view.typeface = inter(view)
        view.fontVariationSettings = "'wght' $weight, 'opsz' $opticalSize"
        view.fontFeatureSettings = "tnum, ss01" // tabular digits, open shapes for 6/9
        view.letterSpacing = tracking
        view.includeFontPadding = false
    }
}
