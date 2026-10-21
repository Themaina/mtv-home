package com.mtv.launcher.tv

import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * Talks to the TV's own picture engine (Toptech "TvService", the same service its picture
 * menu uses). Works only on Toptech/Realtek TVs; everywhere else [available] is false.
 *
 * Call numbers come from the TV's ITvPicture / ITvCommon AIDL stubs (TopTechTvMenu.apk).
 */
object TvPicture {
    private const val TAG = "TvPicture"
    private const val SERVICE = "TvService"
    private const val TOKEN_SERVICE = "com.toptech.api.ITvService"
    private const val TOKEN_PICTURE = "com.toptech.api.ITvPicture"
    private const val TOKEN_COMMON = "com.toptech.api.ITvCommon"

    // ITvPicture
    const val SET_PICTURE_MODE = 1; const val GET_PICTURE_MODE = 2
    const val SET_VIDEO_ITEM = 8; const val GET_VIDEO_ITEM = 9
    const val SET_COLOR_TEMP = 12; const val GET_COLOR_TEMP = 13
    const val SET_NR = 16; const val GET_NR = 17
    const val SET_FILM = 37; const val GET_FILM = 38
    const val ENABLE_DLC = 39; const val DISABLE_DLC = 40; const val IS_DLC = 41
    const val ENABLE_DCC = 42; const val DISABLE_DCC = 43; const val IS_DCC = 44
    const val ENABLE_DBC = 45; const val DISABLE_DBC = 46; const val IS_DBC = 47
    const val GET_ITEM_BY_SOURCE = 49
    const val SET_MPEG_NR = 60; const val GET_MPEG_NR = 61

    // Video items (TvPictureManager.PICTURE_*)
    const val BRIGHTNESS = 0; const val CONTRAST = 1; const val SATURATION = 2; const val SHARPNESS = 3; const val HUE = 4; const val BACKLIGHT = 5

    private const val TOKEN_AUDIO = "com.toptech.api.ITvAudio"
    private var picture: IBinder? = null
    private var common: IBinder? = null
    private var audio: IBinder? = null

    val available: Boolean get() = binder("ITvPicture", TOKEN_PICTURE) != null

    private fun serviceManager(name: String): IBinder? = runCatching {
        Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, name) as IBinder?
    }.onFailure { Log.w(TAG, "ServiceManager lookup failed: $it") }.getOrNull()

    private fun binder(name: String, token: String): IBinder? {
        val cached = when (token) { TOKEN_PICTURE -> picture; TOKEN_AUDIO -> audio; else -> common }
        if (cached?.isBinderAlive == true) return cached
        val tv = serviceManager(SERVICE) ?: return null
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(TOKEN_SERVICE)
            data.writeString(name)
            tv.transact(1, data, reply, 0) // ITvService.getService(String)
            reply.readException()
            reply.readStrongBinder().also { when (token) { TOKEN_PICTURE -> picture = it; TOKEN_AUDIO -> audio = it; else -> common = it } }
        } catch (e: Exception) {
            Log.w(TAG, "getService($name) failed: $e"); null
        } finally { data.recycle(); reply.recycle() }
    }

    private fun call(token: String, code: Int, vararg args: Int): Int? {
        val b = binder(when (token) { TOKEN_PICTURE -> "ITvPicture"; TOKEN_AUDIO -> "ITvAudio"; else -> "ITvCommon" }, token) ?: return null
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(token)
            args.forEach { data.writeInt(it) }
            b.transact(code, data, reply, 0)
            reply.readException()
            reply.readInt()
        } catch (e: Exception) {
            Log.w(TAG, "call $code failed: $e"); null
        } finally { data.recycle(); reply.recycle() }
    }

    fun pic(code: Int, vararg args: Int): Int? = call(TOKEN_PICTURE, code, *args)

    /** ITvAudio (TopTech sound engine): codes from the TV's AIDL stub. */
    fun snd(code: Int, vararg args: Int): Int? = call(TOKEN_AUDIO, code, *args)

    /** Read-only snapshot of the TV's sound engine. */
    fun audioSnapshot(): String = buildString {
        fun g(name: String, code: Int) = append(" $name=${snd(code)}")
        g("soundMode", 2); g("bass", 6); g("treble", 8); g("balance", 10); g("avcMode", 12); g("avc", 14)
        g("surround", 16); g("outputMode", 18); g("spdifMode", 20); g("bassSwitch", 24); g("bassVol", 26)
        g("trueBass", 34); g("speakerVol", 48); g("speakerDelay", 49)
        g("dbxAvailable", 58); g("dbx", 59); g("dbxSonics", 61); g("dbxVolume", 63); g("dbxSurround", 65); g("dolbyDAP", 69)
        append(" eq=" + (0..9).map { snd(22, it) }.joinToString(","))
    }

    /** ITvCommon.getCurrentInputSource (34 = Android/storage, 23-26 = HDMI1-4). */
    fun currentSource(): Int? = call(TOKEN_COMMON, 6)

    /** Read-only snapshot for diagnostics. */
    fun snapshot(): String = buildString {
        append("source=${currentSource()} mode=${pic(GET_PICTURE_MODE)}")
        listOf("brightness" to BRIGHTNESS, "contrast" to CONTRAST, "saturation" to SATURATION, "sharpness" to SHARPNESS, "hue" to HUE, "backlight" to BACKLIGHT)
            .forEach { (n, i) -> append(" $n=${pic(GET_VIDEO_ITEM, i)}") }
        append(" colorTemp=${pic(GET_COLOR_TEMP)} nr=${pic(GET_NR)} mpegNr=${pic(GET_MPEG_NR)} film=${pic(GET_FILM)}")
        append(" dlc=${pic(IS_DLC)} dcc=${pic(IS_DCC)} dbc=${pic(IS_DBC)}")
        append(" bySource: (24,0)=${pic(GET_ITEM_BY_SOURCE, 24, BRIGHTNESS)} (0,24)=${pic(GET_ITEM_BY_SOURCE, BRIGHTNESS, 24)}")
        append(" (24,5)=${pic(GET_ITEM_BY_SOURCE, 24, BACKLIGHT)} (5,24)=${pic(GET_ITEM_BY_SOURCE, BACKLIGHT, 24)}")
        append(" (34,5)=${pic(GET_ITEM_BY_SOURCE, 34, BACKLIGHT)} (5,34)=${pic(GET_ITEM_BY_SOURCE, BACKLIGHT, 34)}")
    }
}
