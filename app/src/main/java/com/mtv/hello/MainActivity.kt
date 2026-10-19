package com.mtv.hello

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private var presses = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        presses = savedInstanceState?.getInt(KEY_PRESSES) ?: 0

        val metrics = resources.displayMetrics
        findViewById<TextView>(R.id.info).text =
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})  •  " +
                "${metrics.widthPixels}×${metrics.heightPixels}  •  ${Build.HARDWARE}"

        val counter = findViewById<TextView>(R.id.counter)
        val countButton = findViewById<Button>(R.id.btn_count)
        val render = { counter.text = "Presses: $presses" }
        render()

        countButton.setOnClickListener { presses++; render() }
        findViewById<Button>(R.id.btn_reset).setOnClickListener { presses = 0; render() }

        countButton.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PRESSES, presses)
    }

    private companion object {
        const val KEY_PRESSES = "presses"
    }
}
