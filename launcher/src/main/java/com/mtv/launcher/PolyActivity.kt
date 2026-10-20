package com.mtv.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.mtv.launcher.img.ImageLoader
import java.text.DateFormat
import java.util.Date

/** One Polymarket market at a time; left/right browses, OK opens it on polymarket.com. */
class PolyActivity : Activity() {

    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_poly)
        val id = intent.getStringExtra(EXTRA_ID)
        index = DataHub.poly.indexOfFirst { it.id == id }.coerceAtLeast(0)
        findViewById<View>(R.id.poly_page).requestFocus()
        show(0)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> { show(+1); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { show(-1); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                DataHub.poly.getOrNull(index)?.let { e ->
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(e.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun show(step: Int) {
        val events = DataHub.poly
        if (events.isEmpty()) return finish()
        index = (index + step + events.size) % events.size
        val e = events[index]
        val page = findViewById<View>(R.id.poly_page)
        if (step != 0) {
            page.translationX = (if (step > 0) 1 else -1) * dp(40).toFloat()
            page.alpha = 0f
            page.animate().translationX(0f).alpha(1f).setDuration(240).start()
        }
        findViewById<TextView>(R.id.poly_kicker).text = e.topic ?: getString(R.string.trending)
        val ends = if (e.endsAt > 0) " · " + getString(R.string.poly_ends, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(e.endsAt))) else ""
        findViewById<TextView>(R.id.poly_meta).text = getString(R.string.poly_volume, Format.money(e.volume24h)) + ends
        findViewById<TextView>(R.id.poly_position).text = "${index + 1} / ${events.size}"
        findViewById<TextView>(R.id.poly_title).text = e.title
        findViewById<TextView>(R.id.poly_description).text = e.description
        ImageLoader.load(e.image, findViewById<ImageView>(R.id.poly_icon), dp(34), dp(34))
        val box = findViewById<LinearLayout>(R.id.poly_outcomes)
        CardAdapter.bindOutcomes(box, e.outcomes.take(8), animate = true)
        for (i in 0 until box.childCount) {
            box.getChildAt(i).findViewById<TextView>(R.id.o_label).textSize = 14f
            box.getChildAt(i).findViewById<TextView>(R.id.o_pct).textSize = 14f
        }
    }

    companion object {
        private const val EXTRA_ID = "id"
        fun intent(ctx: Context, id: String) = Intent(ctx, PolyActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
