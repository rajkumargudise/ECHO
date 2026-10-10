package echo.player.playback

import androidx.media3.common.Player

enum class EchoRepeatMode {
    OFF,
    ALL,
    ONE;

    fun toPlayerMode(): Int = when (this) {
        OFF -> Player.REPEAT_MODE_OFF
        ALL -> Player.REPEAT_MODE_ALL
        ONE -> Player.REPEAT_MODE_ONE
    }

    /** The order the repeat button steps through. */
    fun next(): EchoRepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }

    companion object {
        fun fromPlayerMode(mode: Int): EchoRepeatMode = when (mode) {
            Player.REPEAT_MODE_ALL -> ALL
            Player.REPEAT_MODE_ONE -> ONE
            else -> OFF
        }
    }
}

enum class PlaybackStatus { IDLE, BUFFERING, READY, ENDED }

enum class PlaybackError {
    /** The file behind the MediaStore entry could not be opened (deleted, moved, storage removed). */
    FILE_UNAVAILABLE,

    /** The file exists but could not be parsed or decoded. */
    UNSUPPORTED_OR_CORRUPT,

    /** An online track could not be reached: no connection or the connection timed out. */
    NETWORK,

    /** The online stream address was refused or has expired. */
    STREAM_UNAVAILABLE,

    GENERIC,
}

data class QueueItem(val trackId: String, val title: String, val artist: String)

/**
 * Everything the UI needs to know about playback. [positionMs] is a snapshot taken when the state last
 * changed; the continuously ticking position lives in [PlaybackController.positionMs] so the whole
 * screen does not recompose twice a second.
 */
data class PlaybackState(
    val isConnected: Boolean = false,
    val currentTrackId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val isPlaying: Boolean = false,

    /** The player's intent: true after Play, false only after a real pause. It stays true across a track change. */
    val playWhenReady: Boolean = false,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: EchoRepeatMode = EchoRepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val error: PlaybackError? = null,
) {
    /**
     * Whether the user wants music and it can play: Play was pressed and the player is ready or loading. Unlike
     * [isPlaying] it does not dip while the next track loads, so the mascot does not fall asleep between songs; a
     * real pause, the end of the queue or an error all make it false.
     */
    val wantsToPlay: Boolean
        get() = playWhenReady && (status == PlaybackStatus.READY || status == PlaybackStatus.BUFFERING)
}
