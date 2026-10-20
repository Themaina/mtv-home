package com.mtv.launcher

import android.os.Handler
import android.os.Looper
import com.mtv.launcher.cal.CalEvent
import com.mtv.launcher.f1.F1Snapshot
import com.mtv.launcher.poly.PolyEvent

/** Latest F1, Polymarket and calendar data, shared by the dashboard and the detail screens. */
object DataHub {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<() -> Unit>()

    @Volatile var f1: F1Snapshot? = null
    @Volatile var poly: List<PolyEvent> = emptyList()
    @Volatile var events: List<CalEvent> = emptyList()

    fun observe(l: () -> Unit) { listeners += l }
    fun stopObserving(l: () -> Unit) { listeners -= l }
    fun changed() = main.post { listeners.toList().forEach { it() } }
}
