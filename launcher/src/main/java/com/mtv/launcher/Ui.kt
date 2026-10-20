package com.mtv.launcher

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateUtils
import android.view.View

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun timeAgo(millis: Long): String =
    if (millis <= 0L) "" else DateUtils.getRelativeTimeSpanString(
        millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()

/** Cheap "blur": shrink to a few dozen pixels and let bilinear filtering smear it back up. */
fun Bitmap.toBackdrop(): Bitmap = Bitmap.createScaledBitmap(this, 48, 27, true)

/** tvOS-like focus: lift, grow and cast a shadow; caption fades in when [caption] is given. */
fun View.applyFocusLift(focused: Boolean, animate: Boolean, caption: View? = null, captionVisible: Boolean = true) {
    // Wide cards grow less so they don't push into their neighbours (TV focus range 1.025–1.1).
    val grow = if (width > context.dp(200)) 1.04f else 1.08f
    val scale = if (focused) grow else 1f
    val lift = if (focused) context.dp(10).toFloat() else 0f
    (parent as? View)?.translationZ = if (focused) 1f else 0f
    if (animate) {
        animate().scaleX(scale).scaleY(scale).translationZ(lift).setDuration(140).start()
        caption?.animate()?.alpha(if (focused && captionVisible) 1f else 0f)?.setDuration(140)?.start()
    } else {
        scaleX = scale; scaleY = scale; translationZ = lift
        caption?.alpha = if (focused && captionVisible) 1f else 0f
    }
}
