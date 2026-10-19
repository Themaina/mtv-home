package com.mtv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.mtv.launcher.config.UserConfig
import com.mtv.launcher.market.MarketService

/**
 * This TV re-opens its last input on power-up. If the user wants the dashboard first,
 * bring it forward once the TV player has started. Allowed from the background because
 * the installer grants "display over other apps".
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        MarketService.start(app)
        if (!UserConfig(app).bootToHome) return
        val pending = goAsync()
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching {
                app.startActivity(
                    Intent(app, HomeActivity::class.java)
                        .setAction(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            }
            pending.finish()
        }, BOOT_DELAY_MS)
    }

    private companion object {
        const val BOOT_DELAY_MS = 7_000L
    }
}
