package echo.player.data.local

import android.content.Context
import echo.player.domain.MusicRoot
import echo.player.domain.MusicRoots
import echo.player.domain.PlaylistLogic
import echo.player.domain.TrackSource
import echo.player.domain.model.Playlist
import echo.player.domain.model.PlaylistEntry
import echo.player.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LibrarySnapshot(
    val tracks: List<Track>,
    val favoriteIds: Set<String>,
    val playlists: List<Playlist>,
)

class LibraryRepository(
    context: Context,
    private val trackSource: TrackSource = MediaStoreDataSource(context),
) {
    private val database = EchoDatabase.getInstance(context)
    private val dao = database.echoDao()

    /**
     * Loads the library for the folders the user chose. Favorites and playlists are stored by track id,
     * independent of which folders are selected, so changing folders never deletes them; entries whose
     * track is outside the selected folders simply show as unavailable.
     */
    suspend fun loadLibrary(roots: List<MusicRoot>): LibrarySnapshot = withContext(Dispatchers.IO) {
        val tracks = trackSource.loadTracks()
            .filter { MusicRoots.accepts(roots, it.volumeId, it.relativePath) }
        val favoriteIds = dao.favoriteTrackIds().toSet()
        val playlists = dao.playlists().map { playlistEntity ->
            val orderedTracks = dao.playlistTracks(playlistEntity.id).sortedBy { it.position }
            Playlist(
                id = playlistEntity.id,
                name = playlistEntity.name,
                trackIds = orderedTracks.map { it.trackId },
                createdAt = playlistEntity.createdAt,
                entries = orderedTracks.map {
                    PlaylistEntry(it.trackId, it.titleSnapshot, it.artistSnapshot, it.albumSnapshot)
                },
            )
        }
        LibrarySnapshot(
            tracks = tracks.map { it.copy(isFavorite = favoriteIds.contains(it.id)) },
            favoriteIds = favoriteIds,
            playlists = playlists,
        )
    }

    suspend fun toggleFavorite(track: Track) = withContext(Dispatchers.IO) {
        val favoriteIds = dao.favoriteTrackIds().toSet()
        if (favoriteIds.contains(track.id)) {
            dao.removeFavorite(track.id)
        } else {
            dao.addFavorite(FavoriteTrackEntity(trackId = track.id))
        }
    }

    suspend fun favoriteIds(): Set<String> = withContext(Dispatchers.IO) { dao.favoriteTrackIds().toSet() }

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        val cleanName = PlaylistLogic.cleanName(name).ifBlank { "My Playlist" }
        dao.createPlaylist(PlaylistEntity(name = cleanName))
    }

    suspend fun renamePlaylist(playlistId: Long, name: String) = withContext(Dispatchers.IO) {
        if (name.isNotBlank()) {
            dao.renamePlaylist(playlistId, name)
        }
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        dao.deletePlaylist(playlistId)
    }

    /** Returns true when the song was added, false when the playlist already had it. */
    suspend fun addTrackToPlaylist(playlistId: Long, track: Track): Boolean = withContext(Dispatchers.IO) {
        val existing = dao.playlistTracks(playlistId)
        if (PlaylistLogic.shouldAdd(existing.map { it.trackId }, track.id)) {
            val nextPosition = PlaylistLogic.nextPosition(existing.map { it.position })
            dao.addTrackToPlaylist(
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = track.id,
                    position = nextPosition,
                    titleSnapshot = track.title,
                    artistSnapshot = track.artist,
                    albumSnapshot = track.album,
                )
            )
            true
        } else {
            false
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) = withContext(Dispatchers.IO) {
        dao.removeTrackFromPlaylist(playlistId, trackId)
    }

    suspend fun reorderPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        val tracks = dao.playlistTracks(playlistId).sortedBy { it.position }.toMutableList()
        if (fromIndex in tracks.indices && toIndex in tracks.indices && fromIndex != toIndex) {
            val item = tracks.removeAt(fromIndex)
            tracks.add(toIndex, item)
            tracks.forEachIndexed { index, entity ->
                dao.addTrackToPlaylist(
                    entity.copy(position = index)
                )
            }
        }
    }
}
