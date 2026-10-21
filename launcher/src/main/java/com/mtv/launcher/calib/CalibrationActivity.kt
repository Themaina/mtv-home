package com.mtv.launcher.calib

import android.app.Activity
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import com.mtv.launcher.R
import com.mtv.launcher.dp
import com.mtv.launcher.tv.TvPicture

/**
 * Guided picture calibration. Each step plays a test pattern *as video* (so it goes through the
 * TV's picture engine exactly like films and YouTube) and offers the matching native controls,
 * changed live through the TV's own picture service.
 *
 * Remote: ▲▼ choose a control · ◀▶ adjust · OK next step · Back previous step.
 */
class CalibrationActivity : Activity() {

    private class Control(
        val label: String,
        val read: () -> Int?,
        val write: (Int) -> Unit,
        val values: List<Int>,          // allowed values in order
        val name: (Int) -> String = { it.toString() },
    )

    private class Step(val title: String, val clip: Int, val text: String, val controls: List<Control>)

    private lateinit var video: VideoView
    private lateinit var kicker: TextView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var rows: LinearLayout
    private lateinit var hint: TextView
    private val native = TvPicture.available
    private var steps: List<Step> = emptyList()
    private var stepIndex = 0
    private var controlIndex = 0
    private var original: Map<String, Int?> = emptyMap()
    private var clipShowing = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        original = snapshot()
        steps = buildSteps()
        show(0)
    }

    // ---- native controls ----

    private fun item(label: String, id: Int, range: IntRange = 0..100) = Control(
        label, { TvPicture.pic(TvPicture.GET_VIDEO_ITEM, id) }, { TvPicture.pic(TvPicture.SET_VIDEO_ITEM, id, it) }, range.toList(),
    )

    private fun toggle(label: String, isCode: Int, on: Int, off: Int) = Control(
        label, { TvPicture.pic(isCode) }, { TvPicture.pic(if (it == 1) on else off) }, listOf(0, 1), { if (it == 1) "On" else "Off" },
    )

    private val pictureMode = Control("Picture mode", { TvPicture.pic(TvPicture.GET_PICTURE_MODE) }, { TvPicture.pic(TvPicture.SET_PICTURE_MODE, it) },
        listOf(9, 0, 1, 4), { mapOf(9 to "Standard", 0 to "User", 1 to "Vivid", 4 to "Movie")[it] ?: "Mode $it" })
    private val colorTemp = Control("Colour temperature", { TvPicture.pic(TvPicture.GET_COLOR_TEMP) }, { TvPicture.pic(TvPicture.SET_COLOR_TEMP, it) },
        listOf(4, 1, 3), { mapOf(1 to "Normal", 3 to "Warm", 4 to "Cool")[it] ?: "#$it" })
    private val nr = Control("Noise reduction", { TvPicture.pic(TvPicture.GET_NR) }, { TvPicture.pic(TvPicture.SET_NR, it) },
        listOf(0, 1, 2, 3, 4), { listOf("Off", "Low", "Medium", "High", "Auto").getOrElse(it) { "#$it" } })
    private val mpegNr = Control("MPEG noise reduction", { TvPicture.pic(TvPicture.GET_MPEG_NR) }, { TvPicture.pic(TvPicture.SET_MPEG_NR, it) },
        listOf(0, 1, 2, 3), { listOf("Off", "Low", "Medium", "High").getOrElse(it) { "#$it" } })

    private fun buildSteps(): List<Step> = listOf(
        Step("Start", R.raw.cal_ref, "Choose User so your changes are kept. Warm is closest to how films and shows are graded.",
            listOf(pictureMode, colorTemp)),
        Step("Black level", R.raw.cal_black, "Lower Brightness until the 1% bar vanishes into the black, while the 2% bar is still just visible.",
            listOf(item("Brightness", TvPicture.BRIGHTNESS))),
        Step("White level", R.raw.cal_white, "Raise Contrast until bars start merging into the white, then back off until 98% and 99% are separate. Backlight sets how bright the room light is: lower it at night.",
            listOf(item("Contrast", TvPicture.CONTRAST), item("Backlight", TvPicture.BACKLIGHT))),
        Step("Colour", R.raw.cal_colour, "Raise Colour until the right-hand squares in each row start looking the same, then back off a few steps.",
            listOf(item("Colour", TvPicture.SATURATION))),
        Step("Sharpness", R.raw.cal_sharp, "Lower Sharpness until no bright outline hugs the black lines, while the small text stays crisp.",
            listOf(item("Sharpness", TvPicture.SHARPNESS))),
        Step("Natural look", R.raw.cal_ref, "Skin should look natural, neither green nor pink: adjust Tint only if it isn't. For clean HD sources, noise reduction is best Off.",
            listOf(item("Tint", TvPicture.HUE), nr, mpegNr)),
        Step("Enhancements", R.raw.cal_grey, "The TV's image enhancers. Try each: keep what looks better on real content. Greys here should stay neutral and the ramp smooth.",
            listOf(toggle("Dynamic contrast", TvPicture.IS_DLC, TvPicture.ENABLE_DLC, TvPicture.DISABLE_DLC),
                toggle("Dynamic colour", TvPicture.IS_DCC, TvPicture.ENABLE_DCC, TvPicture.DISABLE_DCC),
                toggle("Dynamic backlight", TvPicture.IS_DBC, TvPicture.ENABLE_DBC, TvPicture.DISABLE_DBC),
                colorTemp)),
        Step("Done", R.raw.cal_ref, "Your picture is set. OK to finish and keep it, or ◀ to put every setting back the way it was.", emptyList()),
    )

    private fun snapshot(): Map<String, Int?> = if (!native) emptyMap() else mapOf(
        "mode" to TvPicture.pic(TvPicture.GET_PICTURE_MODE), "ct" to TvPicture.pic(TvPicture.GET_COLOR_TEMP),
        "nr" to TvPicture.pic(TvPicture.GET_NR), "mpegNr" to TvPicture.pic(TvPicture.GET_MPEG_NR),
        "dlc" to TvPicture.pic(TvPicture.IS_DLC), "dcc" to TvPicture.pic(TvPicture.IS_DCC), "dbc" to TvPicture.pic(TvPicture.IS_DBC),
    ) + (0..5).associate { "item$it" to TvPicture.pic(TvPicture.GET_VIDEO_ITEM, it) }

    private fun revert() {
        val o = original
        o["mode"]?.let { TvPicture.pic(TvPicture.SET_PICTURE_MODE, it) }
        (0..5).forEach { i -> o["item$i"]?.let { TvPicture.pic(TvPicture.SET_VIDEO_ITEM, i, it) } }
        o["ct"]?.let { TvPicture.pic(TvPicture.SET_COLOR_TEMP, it) }
        o["nr"]?.let { TvPicture.pic(TvPicture.SET_NR, it) }
        o["mpegNr"]?.let { TvPicture.pic(TvPicture.SET_MPEG_NR, it) }
        listOf(Triple("dlc", TvPicture.ENABLE_DLC, TvPicture.DISABLE_DLC), Triple("dcc", TvPicture.ENABLE_DCC, TvPicture.DISABLE_DCC),
            Triple("dbc", TvPicture.ENABLE_DBC, TvPicture.DISABLE_DBC)).forEach { (k, on, off) -> o[k]?.let { TvPicture.pic(if (it == 1) on else off) } }
    }

    // ---- UI ----

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        video = VideoView(this)
        root.addView(video, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.calib_panel)
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        kicker = text(11f, 0xFFFFD60A.toInt(), bold = true)
        title = text(18f, 0xFFFFFFFF.toInt(), bold = true)
        body = text(12.5f, 0xE6FFFFFF.toInt()).apply { setPadding(0, dp(3), 0, dp(6)) }
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        hint = text(10.5f, 0xA6FFFFFF.toInt()).apply { setPadding(0, dp(6), 0, 0) }
        listOf(kicker, title, body, rows, hint).forEach { panel.addView(it) }
        root.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            setMargins(dp(44), 0, dp(44), dp(24))
        })
        setContentView(root)
    }

    private fun text(size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun play(clip: Int) {
        if (clip == clipShowing) return
        clipShowing = clip
        video.setVideoURI(Uri.parse("android.resource://$packageName/$clip"))
        video.setOnPreparedListener { mp -> mp.isLooping = true; mp.setVolume(0f, 0f); video.start() }
        // A single-frame clip can end immediately; just restart it.
        video.setOnCompletionListener { video.seekTo(0); video.start() }
        video.setOnErrorListener { _, _, _ -> true }
    }

    private fun show(index: Int) {
        stepIndex = index.coerceIn(0, steps.lastIndex)
        controlIndex = 0
        val step = steps[stepIndex]
        play(step.clip)
        kicker.text = "Calibrate · Step ${stepIndex + 1} of ${steps.size}" + if (!native) " · patterns only on this TV" else ""
        title.text = step.title
        body.text = step.text
        renderControls()
        hint.text = when {
            step.controls.isEmpty() -> "OK finish   ·   ◀ revert everything   ·   Back previous step"
            else -> "▲▼ choose   ·   ◀▶ adjust   ·   OK next step   ·   Back previous step"
        }
    }

    private fun renderControls() {
        rows.removeAllViews()
        val controls = if (native) steps[stepIndex].controls else emptyList()
        controls.forEachIndexed { i, c ->
            val v = c.read()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(5), dp(10), dp(5))
                setBackgroundResource(if (i == controlIndex) R.drawable.calib_row_on else 0)
            }
            row.addView(text(13f, 0xFFFFFFFF.toInt(), bold = i == controlIndex).apply { this.text = c.label }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (c.values.size > 5) {
                val track = FrameLayout(this).apply { setBackgroundResource(R.drawable.bar_track) }
                val fill = View(this).apply { setBackgroundResource(R.drawable.bar_fill); pivotX = 0f; scaleX = ((v ?: 0) / 100f).coerceIn(0f, 1f) }
                track.addView(fill, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                row.addView(track, LinearLayout.LayoutParams(dp(220), dp(5)).apply { marginEnd = dp(14) })
            }
            row.addView(text(13f, 0xFFFFD60A.toInt(), bold = true).apply {
                this.text = if (v == null) "—" else (if (i == controlIndex) "◀ ${c.name(v)} ▶" else c.name(v))
                gravity = Gravity.END
            }, LinearLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.WRAP_CONTENT))
            rows.addView(row)
        }
    }

    private fun adjust(delta: Int) {
        val c = steps[stepIndex].controls.getOrNull(controlIndex) ?: return
        val current = c.read() ?: return
        val idx = c.values.indexOf(current).let { if (it < 0) c.values.indexOfFirst { v -> v >= current }.coerceAtLeast(0) else it }
        val next = c.values[(idx + delta).coerceIn(0, c.values.lastIndex)]
        if (next != current) c.write(next)
        renderControls()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val controls = if (native) steps[stepIndex].controls else emptyList()
        val fast = if (event.repeatCount > 3) 3 else 1
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { controlIndex = (controlIndex - 1).coerceAtLeast(0); renderControls() }
            KeyEvent.KEYCODE_DPAD_DOWN -> { controlIndex = (controlIndex + 1).coerceAtMost((controls.size - 1).coerceAtLeast(0)); renderControls() }
            KeyEvent.KEYCODE_DPAD_RIGHT -> adjust(fast)
            KeyEvent.KEYCODE_DPAD_LEFT -> if (steps[stepIndex].controls.isEmpty()) { revert(); finish() } else adjust(-fast)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (stepIndex == steps.lastIndex) finish() else show(stepIndex + 1)
            KeyEvent.KEYCODE_BACK -> if (stepIndex == 0) finish() else show(stepIndex - 1)
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }
}
