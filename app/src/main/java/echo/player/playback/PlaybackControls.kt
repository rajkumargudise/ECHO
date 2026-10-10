package echo.player.playback

import echo.player.domain.model.PlayableTrack
import echo.player.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * What the UI is allowed to do to playback. The Android implementation is [PlaybackController]
 * (a Media3 MediaController); a desktop build would provide its own engine behind the same surface.
 */
interface PlaybackControls {
    val state: StateFlow<PlaybackState>

    /** Ticks while playing. Collect it only where the position is drawn. */
    val positionMs: StateFlow<Long>

    fun playQueue(tracks: List<Track>, startIndex: Int)

    /** Plays a queue of local and/or online tracks through the same player. Items without a playable source are skipped. */
    fun playItems(items: List<PlayableTrack>, startIndex: Int)
    fun togglePlayPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun cycleRepeatMode()
    fun toggleShuffle()
    fun skipToQueueItem(index: Int)
}
