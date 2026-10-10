package echo.player.ui

import echo.player.playback.PlaybackError

/** Short, human wording for playback problems. Technical detail stays in the log. */
fun PlaybackError.userMessage(): String = when (this) {
    PlaybackError.FILE_UNAVAILABLE -> "This file is no longer available."
    PlaybackError.NETWORK -> "Can't reach the online stream. Check your connection."
    PlaybackError.STREAM_UNAVAILABLE -> "This online track is unavailable right now."
    PlaybackError.UNSUPPORTED_OR_CORRUPT,
    PlaybackError.GENERIC,
    -> "Unable to play this track."
}
