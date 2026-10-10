package echo.player.platform.android

import android.content.Context

/** Small user preferences that are not part of the library database. */
class AndroidSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("echo_settings", Context.MODE_PRIVATE)

    init {
        // The retired FM radio kept presets here; nothing reads them any more.
        context.applicationContext.deleteSharedPreferences("echo_radio")
    }

    var showCompanion: Boolean
        get() = prefs.getBoolean(KEY_SHOW_COMPANION, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_COMPANION, value).apply()
        }

    /** Folders as tiles (true) or as a list (false). */
    var folderTiles: Boolean
        get() = prefs.getBoolean(KEY_FOLDER_TILES, false)
        set(value) {
            prefs.edit().putBoolean(KEY_FOLDER_TILES, value).apply()
        }

    /** True once the first-run welcome hints have been shown (or skipped). */
    var welcomeSeen: Boolean
        get() = prefs.getBoolean(KEY_WELCOME_SEEN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_WELCOME_SEEN, value).apply()
        }

    var visualizerModeName: String?
        get() = prefs.getString(KEY_VISUALIZER_MODE, null)
        set(value) {
            prefs.edit().putString(KEY_VISUALIZER_MODE, value).apply()
        }

    private companion object {
        const val KEY_SHOW_COMPANION = "show_companion"
        const val KEY_VISUALIZER_MODE = "visualizer_mode"
        const val KEY_FOLDER_TILES = "folder_tiles"
        const val KEY_WELCOME_SEEN = "welcome_seen"
    }
}
