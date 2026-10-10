package echo.player.platform.android

import android.content.Context
import echo.player.domain.MusicRoot
import echo.player.domain.MusicRootStore
import echo.player.domain.MusicRoots

/** Remembers the chosen music folders in app-private preferences. Local only. */
class AndroidMusicRootStore(context: Context) : MusicRootStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): List<MusicRoot> = MusicRoots.decode(prefs.getString(KEY_ROOTS, null))

    override fun save(roots: List<MusicRoot>) {
        prefs.edit().putString(KEY_ROOTS, MusicRoots.encode(roots)).apply()
    }

    private companion object {
        const val PREFS_NAME = "echo_music_roots"
        const val KEY_ROOTS = "roots"
    }
}
