package echo.player.playback

/**
 * Everything the Now Playing and mini-player screens display, derived from the single playback state.
 * Keeping this as a pure mapping means the screens hold no playback logic of their own.
 */
data class NowPlayingModel(
    val hasTrack: Boolean,
    val trackId: String?,
    val title: String,
    val artist: String,
    val album: String,
    val isPlaying: Boolean,
    val isFavorite: Boolean,
    val canSeek: Boolean,
    val progress: Float,
    val elapsedText: String,
    val durationText: String,
    val repeatMode: EchoRepeatMode,
    val shuffleEnabled: Boolean,
    /** "3 / 12", empty when nothing is queued. */
    val queuePositionText: String,
    val queueSize: Int,
    val currentQueueIndex: Int,
    val error: PlaybackError?,
)

object NowPlayingFormat {
    const val NO_TRACK_TITLE = "Nothing playing"

    fun formatTime(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L) / 1000)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }
}

fun PlaybackState.toNowPlayingModel(positionMs: Long, favoriteIds: Set<String>): NowPlayingModel {
    val hasTrack = currentTrackId != null
    val safePosition = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L).takeIf { it > 0 } ?: Long.MAX_VALUE)
    return NowPlayingModel(
        hasTrack = hasTrack,
        trackId = currentTrackId,
        title = title?.takeIf { it.isNotBlank() } ?: NowPlayingFormat.NO_TRACK_TITLE,
        artist = artist.orEmpty(),
        album = album.orEmpty(),
        isPlaying = isPlaying,
        isFavorite = currentTrackId != null && currentTrackId in favoriteIds,
        canSeek = hasTrack && durationMs > 0 && status != PlaybackStatus.IDLE,
        progress = if (durationMs > 0) (safePosition.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
        elapsedText = NowPlayingFormat.formatTime(safePosition),
        durationText = NowPlayingFormat.formatTime(durationMs),
        repeatMode = repeatMode,
        shuffleEnabled = shuffleEnabled,
        queuePositionText = if (queue.isNotEmpty() && currentIndex >= 0) "${currentIndex + 1} / ${queue.size}" else "",
        queueSize = queue.size,
        currentQueueIndex = currentIndex,
        error = error,
    )
}
