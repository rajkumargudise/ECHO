package echo.player.domain.model

/**
 * A playlist membership row. The title/artist/album snapshots are captured when the song is added so
 * the row can still be shown (as unavailable) if the local file later disappears from MediaStore.
 */
data class PlaylistEntry(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
)

data class Playlist(
    val id: Long,
    val name: String,
    val trackIds: List<String>,
    val createdAt: Long = System.currentTimeMillis(),
    val entries: List<PlaylistEntry> = emptyList(),
)
