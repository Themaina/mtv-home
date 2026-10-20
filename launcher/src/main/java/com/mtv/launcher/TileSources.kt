package com.mtv.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.provider.Settings
import android.util.Log

/**
 * Builds the tiles shown on the home screen. Everything here may be slow on this TV's
 * 1.3 GHz CPU (banner decoding), so callers run it off the main thread.
 */
object TileSources {

    private const val TAG = "TileSources"

    /**
     * Toptech's TV player. Its exported TvActivity forwards to MainActivity, which picks the
     * source from the intent *action* (see TvApplication.resolveInputAndChannel in TopTechTvMenu.apk).
     */
    private const val TV_PLAYER_PKG = "com.toptech.tvmenu"
    private val TV_PLAYER = ComponentName(TV_PLAYER_PKG, "com.toptech.tvmenu.TvActivity")
    private const val ACTION_PREFIX = "com.toptech.tv.ACTION_"

    /** Packages never shown in the app grid. */
    private val HIDDEN_PACKAGES = setOf(
        "com.zeasn.toptech.titanos2.launcher", // the stock Titan OS launcher we replace
        "com.toptech.quickmenu",               // empty factory debug screen
    )

    private const val COLOR_LIVE = 0xFF2E7D32.toInt()
    private const val COLOR_HDMI = 0xFF1565C0.toInt()
    private const val COLOR_ANALOG = 0xFF4527A0.toInt()
    private const val COLOR_SETTINGS = 0xFF37474F.toInt()
    private const val COLOR_APP = 0xFF263238.toInt()

    fun apps(ctx: Context): List<Tile> {
        val pm = ctx.packageManager
        val byPackage = LinkedHashMap<String, Tile>()

        fun collect(category: String, useBanner: Boolean) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(query, 0)) {
                val ai = ri.activityInfo
                val pkg = ai.packageName
                if (pkg == ctx.packageName || pkg in HIDDEN_PACKAGES || pkg in byPackage) continue
                val banner = if (useBanner) ai.loadBanner(pm) else null
                byPackage[pkg] = Tile(
                    key = "app:$pkg/${ai.name}",
                    label = ri.loadLabel(pm).toString(),
                    intent = Intent(Intent.ACTION_MAIN)
                        .addCategory(category)
                        .setClassName(pkg, ai.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                    banner = banner,
                    icon = if (banner == null) ri.loadIcon(pm) else null,
                    color = COLOR_APP,
                    packageName = pkg,
                )
            }
        }

        // TV apps first so their banner wins; phone-only sideloads fall back to their icon.
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, useBanner = true)
        collect(Intent.CATEGORY_LAUNCHER, useBanner = false)
        return byPackage.values.toList()
    }

    fun inputs(ctx: Context): List<Tile> {
        if (!isInstalled(ctx, TV_PLAYER_PKG)) return standardInputs(ctx)

        val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        val all = try {
            tim?.tvInputList.orEmpty()
        } catch (e: RuntimeException) {
            Log.w(TAG, "TV input list unavailable", e)
            emptyList()
        }
        val hardware = all.filter { it.serviceInfo?.packageName?.startsWith("com.realtek") == true }

        val tiles = mutableListOf<Tile>()
        val hasTuner = hardware.any { it.type == TvInputInfo.TYPE_TUNER }
        if (hasTuner || hardware.isEmpty()) {
            tiles += sourceTile(ctx, "TV", ctx.getString(R.string.live_tv), R.drawable.ic_live_tv, COLOR_LIVE)
        }

        // HDMI input ids end in a hardware id that increases with the port number.
        val hdmi = hardware.filter { it.type == TvInputInfo.TYPE_HDMI }.sortedBy { it.id }
        val hdmiCount = if (hdmi.isEmpty()) 3 else hdmi.size
        for (i in 0 until hdmiCount) {
            val label = hdmi.getOrNull(i)?.let { labelOf(ctx, it) } ?: "HDMI ${i + 1}"
            tiles += sourceTile(ctx, "HDMI_${i + 1}", label, R.drawable.ic_input, COLOR_HDMI)
        }
        if (hardware.any { it.type == TvInputInfo.TYPE_COMPOSITE }) {
            tiles += sourceTile(ctx, "AV", "AV", R.drawable.ic_input, COLOR_ANALOG)
        }
        if (hardware.any { it.type == TvInputInfo.TYPE_COMPONENT }) {
            tiles += sourceTile(ctx, "YPBPR", "Component", R.drawable.ic_input, COLOR_ANALOG)
        }
        return tiles
    }

    /**
     * Other TVs: open hardware inputs the standard Android TV way, through whichever
     * live-TV app handles passthrough channel URIs. Inputs nothing can open are left out.
     */
    private fun standardInputs(ctx: Context): List<Tile> {
        val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        val pm = ctx.packageManager
        return runCatching { tim.tvInputList }.getOrDefault(emptyList())
            .filter { it.isPassthroughInput }
            .sortedBy { it.id }
            .mapNotNull { info ->
                val intent = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.id))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(pm) == null) return@mapNotNull null
                val color = if (info.type == TvInputInfo.TYPE_HDMI) COLOR_HDMI else COLOR_ANALOG
                Tile("input:${info.id}", labelOf(ctx, info), intent, icon = ctx.getDrawable(R.drawable.ic_input), color = color)
            }
    }

    /**
     * Minutes each app was on screen over the last two weeks, from Android's usage stats.
     * Needs the "usage access" app-op, which the installer grants; empty without it.
     */
    fun minutesUsed(ctx: Context): Map<String, Long> = runCatching {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val now = System.currentTimeMillis()
        usm.queryAndAggregateUsageStats(now - 14L * 86_400_000L, now).mapValues { (_, u) -> u.totalTimeInForeground / 60_000L }
    }.getOrDefault(emptyMap())

    /** Apps given big cards in the "Today" row, so they're left out of "Your apps". */
    val FEATURED_PACKAGES = setOf(YOUTUBE, YOUTUBE_TIZENTUBE, YOUTUBE_MUSIC, F1TV)

    /** Big YouTube and YouTube Music cards. Music offers the Play Store when it isn't installed. */
    fun featured(ctx: Context): List<BigAppCard> {
        val pm = ctx.packageManager
        fun launchIntent(pkg: String) = (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val cards = mutableListOf<BigAppCard>()
        val ytPkg = listOf(YOUTUBE, YOUTUBE_TIZENTUBE).firstOrNull { launchIntent(it) != null }
        if (ytPkg != null) {
            cards += BigAppCard("big:yt", Brand.YOUTUBE, ctx.getString(R.string.yt_main), ctx.getString(R.string.yt_open), launchIntent(ytPkg)!!, ytPkg)
        }
        val music = launchIntent(YOUTUBE_MUSIC)
        cards += if (music != null) {
            BigAppCard("big:ytm", Brand.YOUTUBE_MUSIC, ctx.getString(R.string.ytm_sub_installed), ctx.getString(R.string.ytm_open), music, YOUTUBE_MUSIC)
        } else {
            BigAppCard(
                "big:ytm", Brand.YOUTUBE_MUSIC, ctx.getString(R.string.ytm_sub_missing), ctx.getString(R.string.ytm_install),
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$YOUTUBE_MUSIC")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                null,
            )
        }
        launchIntent(F1TV)?.let { cards += BigAppCard("big:f1tv", Brand.F1TV, ctx.getString(R.string.f1tv_sub), ctx.getString(R.string.f1tv_open), it, F1TV) }
        return cards
    }

    private const val F1TV = "com.formulaone.production"
    private const val YOUTUBE = "com.google.android.youtube.tv"
    private const val YOUTUBE_TIZENTUBE = "io.gh.reisxd.tizentube.cobalt"
    private const val YOUTUBE_MUSIC = "com.google.android.youtube.tvmusic"

    fun settings(ctx: Context): List<Tile> {
        val pm = ctx.packageManager
        val personalize = Tile(
            key = "settings:personalize",
            label = ctx.getString(R.string.personalize),
            intent = Intent(ctx, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            icon = ctx.getDrawable(R.drawable.ic_person),
            color = 0xFF8E5A00.toInt(),
        )
        return listOf(personalize) + listOf(
            Triple(Settings.ACTION_SETTINGS, R.string.tv_settings, R.drawable.ic_settings),
            Triple(Settings.ACTION_WIFI_SETTINGS, R.string.network, R.drawable.ic_wifi),
            Triple(Settings.ACTION_SOUND_SETTINGS, R.string.sound, R.drawable.ic_volume),
            Triple(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, R.string.manage_apps, R.drawable.ic_apps),
            Triple(Settings.ACTION_DATE_SETTINGS, R.string.date_time, R.drawable.ic_clock),
        ).mapNotNull { (action, labelRes, iconRes) ->
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(pm) == null) return@mapNotNull null
            Tile(
                key = "settings:$action",
                label = ctx.getString(labelRes),
                intent = intent,
                icon = ctx.getDrawable(iconRes),
                color = COLOR_SETTINGS,
            )
        }
    }

    private fun sourceTile(ctx: Context, source: String, label: String, iconRes: Int, color: Int) = Tile(
        key = "input:$source",
        label = label,
        intent = Intent(ACTION_PREFIX + source)
            .setComponent(TV_PLAYER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        icon = ctx.getDrawable(iconRes),
        color = color,
    )

    private fun labelOf(ctx: Context, info: TvInputInfo): String =
        info.loadCustomLabel(ctx)?.toString()?.takeIf { it.isNotBlank() }
            ?: info.loadLabel(ctx).toString()

    private fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
