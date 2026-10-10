package echo.player.domain

import echo.player.domain.model.Playlist
import echo.player.domain.model.PlaylistEntry
import echo.player.domain.model.Track

data class ArtistGroup(val name: String, val tracks: List<Track>)
data class AlbumGroup(val name: String, val artist: String, val tracks: List<Track>)
data class FolderGroup(val name: String, val tracks: List<Track>)

/** A playlist row resolved against the current library; [track] is null when the media is missing. */
data class ResolvedPlaylistEntry(val position: Int, val entry: PlaylistEntry, val track: Track?) {
    val isAvailable: Boolean get() = track != null
}

/** MediaStore RELATIVE_PATH looks like "Music/English/"; the folder identity is that path without the slash. */
fun folderOf(relativePath: String): String {
    val folder = relativePath.trim().trimEnd('/')
    return folder.ifBlank { "Root" }
}

fun List<Track>.groupedByArtist(): List<ArtistGroup> =
    groupBy { it.artist }
        .entries
        .sortedBy { it.key.lowercase() }
        .map { (artist, tracks) -> ArtistGroup(artist, tracks) }

fun List<Track>.groupedByAlbum(): List<AlbumGroup> =
    groupBy { it.album }
        .entries
        .sortedBy { it.key.lowercase() }
        .map { (album, tracks) ->
            AlbumGroup(album, tracks.first().albumArtist.ifBlank { Track.UNKNOWN_ARTIST }, tracks)
        }

fun List<Track>.groupedByFolder(): List<FolderGroup> =
    groupBy { folderOf(it.relativePath) }
        .entries
        .sortedBy { it.key.lowercase() }
        .map { (folder, tracks) -> FolderGroup(folder, tracks) }

/**
 * Resolves a playlist's stored entries (in stored order) against the live library.
 * Entries whose media is no longer present are kept, with a null track, so user data is not destroyed.
 */
fun Playlist.resolveAgainst(tracksById: Map<String, Track>): List<ResolvedPlaylistEntry> =
    entries.mapIndexed { index, entry -> ResolvedPlaylistEntry(index, entry, tracksById[entry.trackId]) }

fun countLabel(count: Int, singular: String, plural: String = singular + "s"): String =
    "$count ${if (count == 1) singular else plural}"
