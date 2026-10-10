package echo.player

import echo.player.widget.EchoWidgetState
import echo.player.widget.TrackPresentationState
import echo.player.widget.WidgetAction
import echo.player.widget.WidgetActions
import echo.player.widget.WidgetPlayerCommands
import echo.player.widget.WidgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePlayer(override var isPlaying: Boolean = false) : WidgetPlayerCommands {
    val calls = mutableListOf<String>()
    override fun play() { calls += "play"; isPlaying = true }
    override fun pause() { calls += "pause"; isPlaying = false }
    override fun next() { calls += "next" }
    override fun previous() { calls += "previous" }
}

private fun track(title: String = "Maanu Maakunu", artist: String = "DRGM", playing: Boolean = true, prev: Boolean = true, next: Boolean = true) =
    TrackPresentationState(hasTrack = true, title = title, artist = artist, isPlaying = playing, hasPrevious = prev, hasNext = next)

class WidgetStateTests {
    @Test
    fun noTrackShowsEchoAndNothingPlaying() {
        val s = EchoWidgetState(TrackPresentationState.NOTHING)
        assertEquals("ECHO", s.titleText)
        assertEquals("Nothing playing", s.subtitleText)
        assertEquals("IDLE", s.stateText)
        assertFalse(s.isPlaying)
        assertFalse(s.canNext)
        assertFalse(s.canPrevious)
        assertEquals("Play", s.playPauseDescription)
        assertEquals("Open ECHO", s.openDescription)
        assertFalse("with nothing loaded the tap lands on the library", s.opensNowPlaying)
    }

    @Test
    fun playingShowsTheTrackAndAPauseButton() {
        val s = EchoWidgetState(track(playing = true))
        assertEquals("Maanu Maakunu", s.titleText)
        assertEquals("DRGM", s.subtitleText)
        assertEquals("PLAYING", s.stateText)
        assertEquals("Pause", s.playPauseDescription)
        assertTrue(s.opensNowPlaying)
        assertEquals("Open ECHO Now Playing", s.openDescription)
    }

    @Test
    fun pausedShowsTheTrackAndAPlayButton() {
        val s = EchoWidgetState(track(playing = false))
        assertEquals("PAUSED", s.stateText)
        assertEquals("Play", s.playPauseDescription)
        assertEquals("Maanu Maakunu", s.titleText)
    }

    @Test
    fun aStoppedPlayerWithNoItemReturnsToIdle() {
        val before = EchoWidgetState(track())
        val after = EchoWidgetState(TrackPresentationState.NOTHING)
        assertNotEquals(before, after)
        assertEquals("IDLE", after.stateText)
    }

    @Test
    fun aTrackChangeReplacesTheTextAndTheEdgeButtons() {
        val a = EchoWidgetState(track("One", "A", next = true, prev = false))
        val b = EchoWidgetState(track("Two", "B", next = false, prev = true))
        assertEquals("One", a.titleText)
        assertEquals("Two", b.titleText)
        assertTrue(a.canNext && !a.canPrevious)
        assertTrue(!b.canNext && b.canPrevious)
    }

    @Test
    fun changedMetadataIsReflected() {
        assertEquals("New name", EchoWidgetState(track("New name")).titleText)
        assertEquals("", EchoWidgetState(track(artist = "")).subtitleText)
        assertEquals("Unknown track", EchoWidgetState(track(title = "  ")).titleText)
    }

    @Test
    fun theSummaryForScreenReadersNamesTheStateAndTrack() {
        assertEquals("ECHO, playing: Maanu Maakunu, DRGM", EchoWidgetState(track()).summaryDescription)
        assertEquals("ECHO, nothing playing", EchoWidgetState(TrackPresentationState.NOTHING).summaryDescription)
    }

    @Test
    fun everyControlHasAMeaningfulLabel() {
        val s = EchoWidgetState(track())
        for (label in listOf(s.playPauseDescription, s.previousDescription, s.nextDescription, s.openDescription, s.summaryDescription)) {
            assertTrue(label.isNotBlank())
            assertFalse("no technical ids in labels: $label", label.contains("w_") || label.contains("R.id") || label.contains("@"))
        }
        assertEquals("Previous track", s.previousDescription)
        assertEquals("Next track", s.nextDescription)
    }

    @Test
    fun theSameStateGivesTheSameWidgetForEveryInstance() {
        // All widgets (and any number of copies of one) are drawn from one immutable state: they cannot disagree.
        val source = track("Same", "Same")
        val a = EchoWidgetState(source)
        val b = EchoWidgetState(source)
        assertEquals(a, b)
        assertEquals(a.summaryDescription, b.summaryDescription)
        assertEquals(source, TrackPresentationState(true, "Same", "Same", true, true, true))
    }
}

class WidgetActionTests {
    @Test
    fun playAndPauseDoExactlyThat() {
        val p = FakePlayer(isPlaying = false)
        WidgetActions.apply(WidgetAction.PLAY, p)
        assertEquals(listOf("play"), p.calls)
        WidgetActions.apply(WidgetAction.PAUSE, p)
        assertEquals(listOf("play", "pause"), p.calls)
    }

    @Test
    fun toggleFollowsTheCurrentState() {
        val p = FakePlayer(isPlaying = true)
        WidgetActions.apply(WidgetAction.TOGGLE, p)
        assertEquals(listOf("pause"), p.calls)
        WidgetActions.apply(WidgetAction.TOGGLE, p)
        assertEquals(listOf("pause", "play"), p.calls)
    }

    @Test
    fun nextAndPreviousGoToThePlayer() {
        val p = FakePlayer()
        WidgetActions.apply(WidgetAction.NEXT, p)
        WidgetActions.apply(WidgetAction.PREVIOUS, p)
        assertEquals(listOf("next", "previous"), p.calls)
    }

    @Test
    fun theEndsOfTheQueueIgnoreTheButton() {
        val p = FakePlayer()
        WidgetActions.apply(WidgetAction.NEXT, p, canNext = false)
        WidgetActions.apply(WidgetAction.PREVIOUS, p, canPrevious = false)
        assertTrue(p.calls.isEmpty())
    }

    @Test
    fun everyActionHasItsOwnIntentActionAndRoundTrips() {
        val names = WidgetAction.entries.map { it.intentAction }
        assertEquals(names.size, names.toSet().size)
        for (a in WidgetAction.entries) assertEquals(a, WidgetAction.fromIntentAction(a.intentAction))
        assertEquals(null, WidgetAction.fromIntentAction("something.else"))
        assertEquals(null, WidgetAction.fromIntentAction(null))
    }

    @Test
    fun widgetActionsAreTheFivePlaybackCommandsPlusFavorite() {
        // Favorite is not a player command; it only flips the playing song in the favorites store.
        assertEquals(setOf("PLAY", "PAUSE", "TOGGLE", "NEXT", "PREVIOUS", "FAVORITE"), WidgetAction.entries.map { it.name }.toSet())
    }
}

class WidgetRecoveryTests {
    @Test
    fun stateSurvivesTheSnapshotRoundTrip() {
        val original = track("Bonafide Girl", "Shaggy")
        assertEquals(original, WidgetSnapshot.decode(WidgetSnapshot.encode(original)))
        val none = TrackPresentationState.NOTHING
        assertEquals(none, WidgetSnapshot.decode(WidgetSnapshot.encode(none)))
    }

    @Test
    fun aProcessThatWasKilledRebuildsTheWidgetButNeverShowsPlaying() {
        val saved = WidgetSnapshot.decode(WidgetSnapshot.encode(track(playing = true)))
        val shown = EchoWidgetState(WidgetSnapshot.asRestored(saved))
        assertEquals("Maanu Maakunu", shown.titleText)
        assertEquals("PAUSED", shown.stateText)
        assertEquals("Play", shown.playPauseDescription)
    }

    @Test
    fun missingOrDamagedSnapshotsFallBackToIdle() {
        assertEquals(TrackPresentationState.NOTHING, WidgetSnapshot.decode(null))
        assertEquals(TrackPresentationState.NOTHING, WidgetSnapshot.decode(""))
        assertEquals(TrackPresentationState.NOTHING, WidgetSnapshot.decode("garbage"))
    }

    @Test
    fun awkwardTitlesCannotBreakTheSnapshot() {
        val t = track(title = "A\u001FB\nC", artist = "x".repeat(500))
        val back = WidgetSnapshot.decode(WidgetSnapshot.encode(t))
        assertTrue(back.hasTrack)
        assertFalse(back.title.contains('\n'))
        assertTrue(back.artist.length <= 160)
    }
}
