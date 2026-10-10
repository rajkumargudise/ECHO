package echo.player.data.local

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tells every part of the app that favorites changed (the library list, Now Playing, the widgets), whichever one made
 * the change. Listeners run on whatever thread called [changed]; they must hand off to their own thread.
 */
object FavoriteSignal {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun add(listener: () -> Unit) {
        listeners.addIfAbsent(listener)
    }

    fun remove(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun changed() {
        for (l in listeners) l()
    }
}

/** Favorites by track id, straight to the database; used where there is no library list (the widget button). */
object FavoriteStore {
    /** Adds the track to favorites, or removes it when it already is one. Returns true when it is now a favorite. */
    suspend fun toggle(context: Context, trackId: String): Boolean {
        val dao = EchoDatabase.getInstance(context).echoDao()
        val now = if (trackId in dao.favoriteTrackIds()) {
            dao.removeFavorite(trackId)
            false
        } else {
            dao.addFavorite(FavoriteTrackEntity(trackId = trackId))
            true
        }
        FavoriteSignal.changed()
        return now
    }

    suspend fun ids(context: Context): Set<String> = EchoDatabase.getInstance(context).echoDao().favoriteTrackIds().toSet()
}
