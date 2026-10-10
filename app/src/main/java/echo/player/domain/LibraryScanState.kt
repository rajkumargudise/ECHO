package echo.player.domain

/** What the library list should say right now. Only [READY_EMPTY] may claim there is no music. */
enum class LibraryScanState {
    /** The first scan has not finished (or a rescan of an empty library is running): show scanning, never "no music". */
    SCANNING,

    READY_WITH_TRACKS,

    /** A scan has finished and found nothing. */
    READY_EMPTY,
}

object LibraryScan {
    fun stateOf(loadedOnce: Boolean, isScanning: Boolean, trackCount: Int): LibraryScanState = when {
        !loadedOnce -> LibraryScanState.SCANNING
        trackCount > 0 -> LibraryScanState.READY_WITH_TRACKS
        isScanning -> LibraryScanState.SCANNING
        else -> LibraryScanState.READY_EMPTY
    }
}
