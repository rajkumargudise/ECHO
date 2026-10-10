package echo.player.online

import echo.player.data.local.HistoryDao
import echo.player.data.local.RecentPlayEntity
import echo.player.data.local.RecentSearchEntity
import echo.player.domain.model.EchoLanguage
import echo.player.domain.model.RemoteMediaId
import echo.player.domain.model.RemoteTrack

enum class PlayOrigin { LOCAL, ONLINE }

/** Where a local track is filed in history: source id "local", track id = its content URI string. */
const val LOCAL_SOURCE_ID = "local"

data class RecentPlay(
    val sourceId: String,
    val sourceTrackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val lastPlayedAt: Long,
    val positionMs: Long,
    val origin: PlayOrigin,
    val language: EchoLanguage = EchoLanguage.UNKNOWN,
) {
    val isLocal: Boolean get() = origin == PlayOrigin.LOCAL

    companion object {
        fun of(track: RemoteTrack, now: Long) = RecentPlay(
            sourceId = track.sourceId,
            sourceTrackId = track.sourceTrackId,
            title = track.title,
            artist = track.artist,
            album = track.album,
            artworkUrl = track.artworkUrl,
            lastPlayedAt = now,
            positionMs = 0L,
            origin = PlayOrigin.ONLINE,
            language = track.language,
        )
    }
}

/** Decides when a played item is worth a history entry. */
object PlayRecorder {
    /** Pause/resume of the same item, or no item, records nothing; a different item does. */
    fun shouldRecord(lastRecordedMediaId: String?, currentMediaId: String?): Boolean =
        !currentMediaId.isNullOrEmpty() && currentMediaId != lastRecordedMediaId

    /** (sourceId, sourceTrackId, origin) for a Media3 mediaId. Local ids are content URIs. */
    fun identify(mediaId: String): Triple<String, String, PlayOrigin> {
        val remote = RemoteMediaId.parse(mediaId)
        return if (remote != null) Triple(remote.first, remote.second, PlayOrigin.ONLINE) else Triple(LOCAL_SOURCE_ID, mediaId, PlayOrigin.LOCAL)
    }
}

/** Recent searches and recent plays, kept on the device only. */
class HistoryRepository(
    private val dao: HistoryDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** False (and nothing stored) when the text is blank. */
    suspend fun recordSearch(raw: String): Boolean {
        val display = SearchText.normalize(raw)
        if (display.isEmpty()) return false
        dao.upsertRecentSearch(RecentSearchEntity(SearchText.key(display), display, clock()))
        dao.trimRecentSearches(MAX_SEARCHES)
        return true
    }

    suspend fun recentSearches(): List<String> = dao.recentSearches(MAX_SEARCHES).map { it.displayQuery }

    suspend fun removeSearch(raw: String) = dao.removeRecentSearch(SearchText.key(raw))

    suspend fun clearSearches() = dao.clearRecentSearches()

    suspend fun recordPlay(play: RecentPlay) {
        dao.upsertRecentPlay(
            RecentPlayEntity(
                sourceId = play.sourceId,
                sourceTrackId = play.sourceTrackId,
                title = play.title,
                artist = play.artist,
                album = play.album,
                artworkUrl = play.artworkUrl,
                lastPlayedAt = play.lastPlayedAt.takeIf { it > 0 } ?: clock(),
                positionMs = play.positionMs.coerceAtLeast(0L),
                origin = play.origin.name,
                language = play.language.name,
            ),
        )
        dao.trimRecentPlays(MAX_PLAYS)
    }

    suspend fun recentPlays(): List<RecentPlay> = dao.recentPlays(MAX_PLAYS).map {
        RecentPlay(
            sourceId = it.sourceId,
            sourceTrackId = it.sourceTrackId,
            title = it.title,
            artist = it.artist,
            album = it.album,
            artworkUrl = it.artworkUrl,
            lastPlayedAt = it.lastPlayedAt,
            positionMs = it.positionMs,
            origin = PlayOrigin.entries.firstOrNull { o -> o.name == it.origin } ?: PlayOrigin.ONLINE,
            language = EchoLanguage.fromName(it.language),
        )
    }

    suspend fun updatePosition(sourceId: String, sourceTrackId: String, positionMs: Long) =
        dao.updateRecentPlayPosition(sourceId, sourceTrackId, positionMs.coerceAtLeast(0L))

    suspend fun removePlay(sourceId: String, sourceTrackId: String) = dao.removeRecentPlay(sourceId, sourceTrackId)

    suspend fun clearPlays() = dao.clearRecentPlays()

    companion object {
        const val MAX_SEARCHES = 20
        const val MAX_PLAYS = 50
    }
}
