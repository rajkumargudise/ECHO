package echo.player

import echo.player.domain.FolderGroup
import echo.player.domain.durationMs
import echo.player.domain.model.Track
import echo.player.ui.library.folderFacts
import echo.player.widget.EchoWidgetState
import echo.player.widget.TrackPresentationState
import echo.player.widget.WidgetAction
import echo.player.widget.WidgetActions
import echo.player.widget.WidgetLabels
import echo.player.widget.WidgetPlayerCommands
import echo.player.widget.WidgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private fun playing(
    position: Long = 0L, duration: Long = 0L, fav: Boolean = false, canFav: Boolean = true, isPlaying: Boolean = true,
) = TrackPresentationState(true, "Song", "Singer", isPlaying, true, true, position, duration, fav, canFav)

class WidgetProgressTests {
    @Test
    fun progressIsAPercentOfTheKnownLength() {
        val s = EchoWidgetState(playing(position = 60_000, duration = 240_000))
        assertTrue(s.showProgress)
        assertEquals(25, s.progressPercent)
        assertEquals(60_000L, s.elapsedMs)
        assertEquals("4:00", s.durationText)
    }

    @Test
    fun anUnknownLengthShowsNoBarAndNoTotal() {
        val s = EchoWidgetState(playing(position = 5_000, duration = 0))
        assertFalse(s.showProgress)
        assertEquals(0, s.progressPercent)
        assertEquals("", s.durationText)
    }

    @Test
    fun aPositionPastTheEndNeverOverflowsTheBar() {
        assertEquals(100, EchoWidgetState(playing(position = 999_000, duration = 100_000)).progressPercent)
        assertEquals(0, EchoWidgetState(playing(position = -5, duration = 100_000)).progressPercent)
    }

    @Test
    fun nothingPlayingShowsNoProgressAtAll() {
        val s = EchoWidgetState(TrackPresentationState.NOTHING)
        assertFalse(s.showProgress)
        assertEquals(0L, s.elapsedMs)
        assertEquals("", s.durationText)
    }

    @Test
    fun longTracksUseHours() {
        assertEquals("1:02:09", WidgetLabels.time(3_729_000))
        assertEquals("0:07", WidgetLabels.time(7_000))
        assertEquals("", WidgetLabels.time(0))
    }
}

class WidgetFavoriteTests {
    @Test
    fun theHeartFollowsFavoriteStateAndSaysWhatTapWillDo() {
        val off = EchoWidgetState(playing(fav = false))
        val on = EchoWidgetState(playing(fav = true))
        assertTrue(off.canFavorite && !off.isFavorite)
        assertEquals("Add to favorites", off.favoriteDescription)
        assertTrue(on.isFavorite)
        assertEquals("Remove from favorites", on.favoriteDescription)
    }

    @Test
    fun aTrackThatCannotBeAFavoriteHidesTheHeart() {
        val s = EchoWidgetState(playing(fav = true, canFav = false))
        assertFalse(s.canFavorite)
        assertFalse("never shown as a favorite when it cannot be one", s.isFavorite)
    }

    @Test
    fun noTrackMeansNoHeart() {
        assertFalse(EchoWidgetState(TrackPresentationState.NOTHING).canFavorite)
    }

    @Test
    fun favoriteIsAnActionButNotAPlayerCommand() {
        assertTrue(WidgetAction.FAVORITE in WidgetAction.entries)
        assertEquals(WidgetAction.FAVORITE, WidgetAction.fromIntentAction(WidgetAction.FAVORITE.intentAction))
        val calls = mutableListOf<String>()
        val player = object : WidgetPlayerCommands {
            override val isPlaying = false
            override fun play() { calls += "play" }
            override fun pause() { calls += "pause" }
            override fun next() { calls += "next" }
            override fun previous() { calls += "previous" }
        }
        WidgetActions.apply(WidgetAction.FAVORITE, player)
        assertTrue("the favorite button must not touch the player", calls.isEmpty())
    }
}

class WidgetSnapshotCompatibilityTests {
    @Test
    fun progressAndFavoriteSurviveTheRoundTrip() {
        val s = playing(position = 12_345, duration = 200_000, fav = true)
        assertEquals(s, WidgetSnapshot.decode(WidgetSnapshot.encode(s)))
    }

    @Test
    fun anOldSixFieldSnapshotStillDecodes() {
        val sep = '\u001F'
        val old = listOf("1", "0", "1", "1", "Old title", "Old artist").joinToString(sep.toString())
        val s = WidgetSnapshot.decode(old)
        assertTrue(s.hasTrack)
        assertEquals("Old title", s.title)
        assertEquals(0L, s.positionMs)
        assertFalse(s.isFavorite)
    }

    @Test
    fun aRestoredWidgetKeepsItsPositionButNeverShowsPlaying() {
        val restored = WidgetSnapshot.asRestored(playing(position = 30_000, duration = 120_000, isPlaying = true))
        assertFalse(restored.isPlaying)
        assertEquals(30_000L, restored.positionMs)
    }
}

class SwipeTabsAndTilesPolicyTests {
    private fun main(vararg parts: String) = File("src/main/java/echo/player/" + parts.joinToString("/"))

    @Test
    fun theLibraryTabsAreAPagerSoSwipingMovesBetweenThem() {
        val library = main("ui", "library", "LibraryScreen.kt").readText()
        assertTrue(library.contains("HorizontalPager"))
        assertTrue(library.contains("rememberPagerState"))
        assertEquals(6, echo.player.ui.LibraryTab.entries.size)
    }

    @Test
    fun foldersOfferBothTilesAndAList() {
        val folders = main("ui", "library", "FolderViews.kt").readText()
        assertTrue(folders.contains("LazyVerticalGrid"))
        assertTrue(folders.contains("IndexedLazyList"))
        assertTrue(folders.contains("Show folders as tiles") && folders.contains("Show folders as a list"))
    }

    @Test
    fun nowPlayingHasAPlusThatOpensTheSameChooser() {
        val np = main("ui", "nowplaying", "NowPlayingScreen.kt").readText()
        assertTrue(np.contains("AddToPlaylistToggle"))
        val app = main("MainActivity.kt").readText()
        assertTrue(app.contains("onAddToPlaylist = viewModel::addCurrentToPlaylist"))
        assertTrue(app.contains("PlaylistChooserDialog"))
    }

    @Test
    fun folderFactsComeFromTheRealTracks() {
        fun track(path: String, ms: Long): Track {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
            val t = unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, Track::class.java) as Track
            fun set(n: String, v: Any?) = Track::class.java.getDeclaredField(n).apply { isAccessible = true }.set(t, v)
            set("id", path + ms); set("relativePath", path); set("durationMs", ms); set("title", "t"); set("artist", "a"); set("album", "b")
            return t
        }
        val g = FolderGroup("Music/Telugu", listOf(track("Music/Telugu/", 120_000), track("Music/Telugu/", 60_000)))
        assertEquals(180_000L, g.durationMs)
        assertEquals("Music · 2 tracks · 3:00", folderFacts(g))
        assertEquals("1 track", folderFacts(FolderGroup("Root", listOf(track("", 0)))))
    }
}
