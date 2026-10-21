package com.mtv.launcher.tv

import android.content.Context
import org.json.JSONObject

/**
 * One-press sound presets on the TV's own sound engine (Toptech ITvAudio), with the previous
 * settings saved so they can be restored. Sound modes: 0 Sports, 1 Standard, 2 Movie/Voice,
 * 3 Music, 4 User (the only mode whose 7-band EQ is editable). EQ bands are low → high.
 */
object TvSound {
    // ITvAudio call numbers
    private const val SET_SOUND_MODE = 1; private const val GET_SOUND_MODE = 2
    private const val SET_SURROUND = 15; private const val GET_SURROUND = 16
    private const val SET_EQ = 21; private const val GET_EQ = 22
    private const val SET_TRUE_BASS = 33; private const val GET_TRUE_BASS = 34
    private const val SET_AVC = 13; private const val GET_AVC = 14
    private const val GET_DAP = 69; private const val SET_DAP = 70
    private const val SET_BASS_SWITCH = 23; private const val GET_BASS_SWITCH = 24
    private const val SET_BASS_VOL = 25; private const val GET_BASS_VOL = 26
    const val USER_MODE = 4
    private const val BANDS = 7

    /** "Like a JBL speaker": punchy upper bass, less mud, forward vocals, crisp top. */
    val JBL_EQ = intArrayOf(6, 4, -1, 1, 3, 4, 3)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("sound_backup", Context.MODE_PRIVATE)

    fun read(): JSONObject = JSONObject().apply {
        put("mode", TvPicture.snd(GET_SOUND_MODE)); put("surround", TvPicture.snd(GET_SURROUND))
        put("trueBass", TvPicture.snd(GET_TRUE_BASS)); put("avc", TvPicture.snd(GET_AVC)); put("dap", TvPicture.snd(GET_DAP))
        put("bassSwitch", TvPicture.snd(GET_BASS_SWITCH)); put("bassVol", TvPicture.snd(GET_BASS_VOL))
    }

    private fun userEq(): List<Int?> {
        val mode = TvPicture.snd(GET_SOUND_MODE)
        if (mode != USER_MODE) TvPicture.snd(SET_SOUND_MODE, USER_MODE)
        return (0 until BANDS).map { TvPicture.snd(GET_EQ, it) }
    }

    /** Saves the current state once (so repeated presses never overwrite the true original). */
    private fun backup(ctx: Context) {
        if (prefs(ctx).contains("state")) return
        val state = read()
        val eq = userEq() // also switches to User; that's where presets live
        state.put("userEq", eq.joinToString(","))
        prefs(ctx).edit().putString("state", state.toString()).apply()
    }

    fun applyJbl(ctx: Context): String {
        backup(ctx)
        TvPicture.snd(SET_SOUND_MODE, USER_MODE)
        JBL_EQ.forEachIndexed { band, v -> TvPicture.snd(SET_EQ, band, v) }
        TvPicture.snd(SET_TRUE_BASS, 1)
        TvPicture.snd(SET_SURROUND, 1)
        TvPicture.snd(SET_DAP, 1)
        // Bass enhancer at a moderate level: small speakers distort if pushed hard.
        val sw = TvPicture.snd(SET_BASS_SWITCH, 1)
        val vol = TvPicture.snd(SET_BASS_VOL, 50)
        return summary() + " (bassSwitch→$sw bassVol→$vol)"
    }

    fun restore(ctx: Context): String {
        val raw = prefs(ctx).getString("state", null) ?: return "nothing saved"
        val s = JSONObject(raw)
        TvPicture.snd(SET_SOUND_MODE, USER_MODE)
        s.optString("userEq").split(",").mapNotNull { it.toIntOrNull() }.forEachIndexed { band, v -> TvPicture.snd(SET_EQ, band, v) }
        TvPicture.snd(SET_TRUE_BASS, s.optInt("trueBass")); TvPicture.snd(SET_SURROUND, s.optInt("surround"))
        TvPicture.snd(SET_AVC, s.optInt("avc")); TvPicture.snd(SET_DAP, s.optInt("dap"))
        TvPicture.snd(SET_BASS_SWITCH, s.optInt("bassSwitch")); TvPicture.snd(SET_BASS_VOL, s.optInt("bassVol"))
        TvPicture.snd(SET_SOUND_MODE, s.optInt("mode", 1))
        prefs(ctx).edit().remove("state").apply()
        return summary()
    }

    fun summary(): String = read().toString() + " eq=" + (0 until BANDS).map { TvPicture.snd(GET_EQ, it) }.joinToString(",")
}
