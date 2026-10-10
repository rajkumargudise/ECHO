package echo.player.domain

import echo.player.domain.model.Playlist
import echo.player.domain.model.Track
import java.text.Normalizer
import java.util.Locale

/** How a list of local songs is ordered. Works on real library data; text compares as Unicode, so Indic scripts sort naturally. */
enum class TrackSort(val label: String) {
    A_Z("A–Z"),
    Z_A("Z–A"),
    RECENT("Recently added"),
    DURATION("Duration"),
    ARTIST("Artist"),
    ALBUM("Album"),
    ;

    fun apply(tracks: List<Track>): List<Track> {
        fun key(text: String) = text.lowercase(Locale.ROOT)
        val byTitle = compareBy<Track> { key(it.title) }.thenBy { it.id }
        return when (this) {
            A_Z -> tracks.sortedWith(byTitle)
            Z_A -> tracks.sortedWith(byTitle.reversed())
            RECENT -> tracks.sortedWith(compareByDescending<Track> { it.dateAdded }.then(byTitle))
            DURATION -> tracks.sortedWith(compareByDescending<Track> { it.durationMs }.then(byTitle))
            ARTIST -> tracks.sortedWith(compareBy<Track> { key(it.artist) }.then(byTitle))
            ALBUM -> tracks.sortedWith(compareBy<Track> { key(it.album) }.thenBy { it.trackNumber ?: Int.MAX_VALUE }.then(byTitle))
        }
    }
}

/** Folder names are MediaStore relative paths ("Music/Telugu"). These split one for display. */
object FolderPaths {
    private fun segments(name: String) = name.split('/').filter { it.isNotBlank() }

    /** The last segment: what the folder is called. */
    fun leaf(name: String): String = segments(name).lastOrNull() ?: name

    /** Where it sits ("Music"), empty for a top-level folder. */
    fun parent(name: String): String = segments(name).dropLast(1).joinToString(" › ")

    /** "Local Music › Music › Telugu". */
    fun breadcrumb(name: String): String = (listOf("Local Music") + segments(name)).joinToString(" › ")
}

val FolderGroup.durationMs: Long get() = tracks.sumOf { it.durationMs }

/** "3:05", "1:02:09"; blank when the duration is unknown. */
fun formatTrackDuration(ms: Long): String {
    if (ms <= 0L) return ""
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** One row of the "Add to playlist" chooser. */
data class PlaylistChoice(val id: Long, val name: String, val trackCount: Int, val alreadyHasTrack: Boolean)

/** The rules of adding a song to a playlist, kept free of Android so they are unit tested. */
object PlaylistLogic {
    /** A playlist holds a song once. */
    fun shouldAdd(existingTrackIds: Collection<String>, trackId: String): Boolean = trackId !in existingTrackIds

    fun nextPosition(positions: Collection<Int>): Int = (positions.maxOrNull() ?: -1) + 1

    fun choices(playlists: List<Playlist>, trackId: String): List<PlaylistChoice> =
        playlists.map { PlaylistChoice(it.id, it.name, it.entries.size, it.entries.any { e -> e.trackId == trackId }) }

    fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    fun addedMessage(playlistName: String) = "Added to $playlistName"
    fun alreadyInMessage(playlistName: String) = "Already in $playlistName"
    fun createdMessage(playlistName: String) = "Created $playlistName and added the song"
}

/** Search over the local library. Text is compared as normalized Unicode so composed/decomposed Indic text still matches. */
object LocalSearch {
    private fun fold(text: String) = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale.ROOT)

    /** Matches title, artist, album or folder path; blank matches everything. */
    fun matches(track: Track, query: String): Boolean {
        val q = fold(query.trim())
        if (q.isEmpty()) return true
        return fold(listOf(track.title, track.artist, track.album, track.relativePath).joinToString(" ")).contains(q)
    }
}
