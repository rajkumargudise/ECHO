package echo.player.playback

import androidx.media3.common.PlaybackException

enum class PreviousAction { RESTART_TRACK, GO_TO_PREVIOUS }

/** Queue and position to put back into the player after a restart. */
data class SavedPlayback(
    val queueIds: List<String>,
    val index: Int,
    val positionMs: Long,
    val repeatMode: EchoRepeatMode,
    val shuffleEnabled: Boolean,
)

data class RestorePlan(val ids: List<String>, val index: Int, val positionMs: Long)

/** Playback decisions that do not need a player, so they can be unit tested on the JVM. */
object PlaybackLogic {
    /** Same threshold Media3 uses for its own "previous" handling (notification, headset buttons). */
    const val RESTART_THRESHOLD_MS = 3_000L

    private const val ID_SEPARATOR = "\n"

    fun previousAction(positionMs: Long, hasPrevious: Boolean): PreviousAction =
        if (positionMs > RESTART_THRESHOLD_MS || !hasPrevious) {
            PreviousAction.RESTART_TRACK
        } else {
            PreviousAction.GO_TO_PREVIOUS
        }

    fun clampStartIndex(queueSize: Int, requested: Int): Int =
        if (queueSize <= 0) 0 else requested.coerceIn(0, queueSize - 1)

    fun mapError(errorCode: Int): PlaybackError = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        -> PlaybackError.FILE_UNAVAILABLE

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        -> PlaybackError.UNSUPPORTED_OR_CORRUPT

        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> PlaybackError.NETWORK

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        -> PlaybackError.STREAM_UNAVAILABLE

        else -> PlaybackError.GENERIC
    }

    fun encodeIds(ids: List<String>): String = ids.joinToString(ID_SEPARATOR)

    fun decodeIds(encoded: String?): List<String> =
        if (encoded.isNullOrEmpty()) emptyList() else encoded.split(ID_SEPARATOR).filter { it.isNotEmpty() }

    /**
     * Turns a saved session into a queue that only contains tracks still in the library. Missing tracks are
     * dropped from the *restored queue* only; nothing is deleted from playlists or favorites.
     * Returns null when nothing playable survives.
     */
    fun planRestore(saved: SavedPlayback, availableIds: Set<String>): RestorePlan? {
        val surviving = saved.queueIds.filter { it in availableIds }
        if (surviving.isEmpty()) return null

        val savedCurrent = saved.queueIds.getOrNull(saved.index)
        val currentIndex = savedCurrent?.let { surviving.indexOf(it) } ?: -1
        if (currentIndex >= 0) {
            return RestorePlan(surviving, currentIndex, saved.positionMs.coerceAtLeast(0L))
        }

        // The track that was playing is gone: land on whatever now sits where it was, from the start.
        val survivorsBefore = saved.queueIds.take(saved.index.coerceAtLeast(0)).count { it in availableIds }
        return RestorePlan(surviving, survivorsBefore.coerceAtMost(surviving.lastIndex), 0L)
    }
}
