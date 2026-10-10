package echo.player.playback

import android.content.Context

/** Local-only persistence of the last playback session (SharedPreferences; no network, no account). */
class PlaybackStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveQueue(ids: List<String>) {
        prefs.edit().putString(KEY_QUEUE, PlaybackLogic.encodeIds(ids)).apply()
    }

    fun saveProgress(index: Int, positionMs: Long, repeatMode: EchoRepeatMode, shuffleEnabled: Boolean) {
        prefs.edit()
            .putInt(KEY_INDEX, index)
            .putLong(KEY_POSITION, positionMs)
            .putString(KEY_REPEAT, repeatMode.name)
            .putBoolean(KEY_SHUFFLE, shuffleEnabled)
            .apply()
    }

    fun load(): SavedPlayback? {
        val ids = PlaybackLogic.decodeIds(prefs.getString(KEY_QUEUE, null))
        if (ids.isEmpty()) return null
        val repeat = prefs.getString(KEY_REPEAT, null)
            ?.let { name -> EchoRepeatMode.entries.firstOrNull { it.name == name } }
            ?: EchoRepeatMode.OFF
        return SavedPlayback(
            queueIds = ids,
            index = prefs.getInt(KEY_INDEX, 0),
            positionMs = prefs.getLong(KEY_POSITION, 0L),
            repeatMode = repeat,
            shuffleEnabled = prefs.getBoolean(KEY_SHUFFLE, false),
        )
    }

    private companion object {
        const val PREFS_NAME = "echo_playback"
        const val KEY_QUEUE = "queue"
        const val KEY_INDEX = "index"
        const val KEY_POSITION = "position_ms"
        const val KEY_REPEAT = "repeat_mode"
        const val KEY_SHUFFLE = "shuffle"
    }
}
