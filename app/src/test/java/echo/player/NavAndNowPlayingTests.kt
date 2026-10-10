package echo.player

import echo.player.domain.NavState
import echo.player.domain.Screen
import echo.player.playback.EchoRepeatMode
import echo.player.playback.NowPlayingFormat
import echo.player.playback.PlaybackError
import echo.player.playback.PlaybackState
import echo.player.playback.PlaybackStatus
import echo.player.playback.QueueItem
import echo.player.playback.toNowPlayingModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavAndNowPlayingTests {
    // region navigation

    @Test
    fun startsOnTheLibraryWithNowhereToGoBack() {
        val nav = NavState()
        assertEquals(Screen.LIBRARY, nav.current)
        assertFalse(nav.canGoBack)
        assertEquals(nav, nav.back())
    }

    @Test
    fun miniPlayerOpensNowPlayingAndBackReturnsToTheLibrary() {
        val opened = NavState().open(Screen.NOW_PLAYING)
        assertEquals(Screen.NOW_PLAYING, opened.current)
        assertTrue(opened.canGoBack)
        assertEquals(Screen.LIBRARY, opened.back().current)
    }

    @Test
    fun openingTheCurrentScreenAgainDoesNotStackIt() {
        val nav = NavState().open(Screen.NOW_PLAYING).open(Screen.NOW_PLAYING)
        assertEquals(2, nav.stack.size)
    }

    @Test
    fun backUnwindsOneScreenAtATime() {
        val nav = NavState().open(Screen.MUSIC_FOLDERS).open(Screen.NOW_PLAYING)
        assertEquals(Screen.MUSIC_FOLDERS, nav.back().current)
        assertEquals(Screen.LIBRARY, nav.back().back().current)
        assertEquals(Screen.LIBRARY, nav.back().back().back().current)
    }

    // endregion

    // region now playing model

    private val queue = listOf(QueueItem("a", "One", "X"), QueueItem("b", "Two", "Y"), QueueItem("c", "Three", "Z"))

    private fun playing(
        position: Long = 0,
        isPlaying: Boolean = true,
        index: Int = 1,
        repeat: EchoRepeatMode = EchoRepeatMode.OFF,
        shuffle: Boolean = false,
        error: PlaybackError? = null,
        status: PlaybackStatus = PlaybackStatus.READY,
        duration: Long = 200_000,
    ) = PlaybackState(
        isConnected = true,
        currentTrackId = queue[index].trackId,
        title = queue[index].title,
        artist = queue[index].artist,
        album = "Album",
        isPlaying = isPlaying,
        status = status,
        positionMs = position,
        durationMs = duration,
        repeatMode = repeat,
        shuffleEnabled = shuffle,
        queue = queue,
        currentIndex = index,
        error = error,
    )

    @Test
    fun modelCarriesCurrentTrackMetadata() {
        val model = playing().toNowPlayingModel(0, emptySet())
        assertTrue(model.hasTrack)
        assertEquals("Two", model.title)
        assertEquals("Y", model.artist)
        assertEquals("Album", model.album)
        assertEquals("b", model.trackId)
    }

    @Test
    fun modelReportsPlayPauseState() {
        assertTrue(playing(isPlaying = true).toNowPlayingModel(0, emptySet()).isPlaying)
        assertFalse(playing(isPlaying = false).toNowPlayingModel(0, emptySet()).isPlaying)
    }

    @Test
    fun modelFormatsElapsedAndDurationAndProgress() {
        val model = playing(duration = 271_000).toNowPlayingModel(137_000, emptySet())
        assertEquals("2:17", model.elapsedText)
        assertEquals("4:31", model.durationText)
        assertEquals(137f / 271f, model.progress, 0.0001f)
        assertTrue(model.canSeek)
    }

    @Test
    fun modelHandlesHourLongTracksAndOutOfRangePositions() {
        assertEquals("1:01:05", NowPlayingFormat.formatTime(3_665_000))
        val model = playing(duration = 60_000).toNowPlayingModel(999_999, emptySet())
        assertEquals(1f, model.progress, 0f)
        assertEquals("1:00", model.elapsedText)
        assertEquals("0:00", NowPlayingFormat.formatTime(-5))
    }

    @Test
    fun seekingIsDisabledWithoutADurationOrWhileIdle() {
        assertFalse(playing(duration = 0).toNowPlayingModel(0, emptySet()).canSeek)
        assertFalse(playing(status = PlaybackStatus.IDLE).toNowPlayingModel(0, emptySet()).canSeek)
    }

    @Test
    fun modelReportsRepeatAndShuffle() {
        EchoRepeatMode.entries.forEach { mode ->
            assertEquals(mode, playing(repeat = mode).toNowPlayingModel(0, emptySet()).repeatMode)
        }
        assertTrue(playing(shuffle = true).toNowPlayingModel(0, emptySet()).shuffleEnabled)
        assertFalse(playing(shuffle = false).toNowPlayingModel(0, emptySet()).shuffleEnabled)
    }

    @Test
    fun favoriteFlagFollowsTheFavoriteSet() {
        assertTrue(playing().toNowPlayingModel(0, setOf("b")).isFavorite)
        assertFalse(playing().toNowPlayingModel(0, setOf("a", "c")).isFavorite)
        assertFalse(playing().toNowPlayingModel(0, emptySet()).isFavorite)
    }

    @Test
    fun queuePositionIsOneBased() {
        val model = playing(index = 2).toNowPlayingModel(0, emptySet())
        assertEquals("3 / 3", model.queuePositionText)
        assertEquals(3, model.queueSize)
        assertEquals(2, model.currentQueueIndex)
    }

    @Test
    fun errorIsPassedThrough() {
        assertEquals(PlaybackError.FILE_UNAVAILABLE, playing(error = PlaybackError.FILE_UNAVAILABLE).toNowPlayingModel(0, emptySet()).error)
        assertNull(playing().toNowPlayingModel(0, emptySet()).error)
    }

    @Test
    fun emptyStateHasNoTrackAndSafeText() {
        val model = PlaybackState().toNowPlayingModel(0, setOf("a"))
        assertFalse(model.hasTrack)
        assertEquals(NowPlayingFormat.NO_TRACK_TITLE, model.title)
        assertEquals("", model.queuePositionText)
        assertFalse(model.isFavorite)
        assertFalse(model.canSeek)
        assertEquals(0f, model.progress, 0f)
    }

    // endregion
}
