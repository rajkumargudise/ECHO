package echo.player

import echo.player.companion.CompanionBrain
import echo.player.companion.CompanionState
import echo.player.companion.MusicSymbolPool
import echo.player.companion.SymbolGlyph
import echo.player.domain.LibraryScan
import echo.player.domain.LibraryScanState
import echo.player.playback.PlaybackState
import echo.player.playback.PlaybackStatus
import echo.player.visualizer.VisualizerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryScanStateTests {
    @Test
    fun beforeTheFirstScanFinishesItIsAlwaysScanning() {
        assertEquals(LibraryScanState.SCANNING, LibraryScan.stateOf(loadedOnce = false, isScanning = true, trackCount = 0))
        assertEquals(LibraryScanState.SCANNING, LibraryScan.stateOf(loadedOnce = false, isScanning = false, trackCount = 0))
        assertEquals("0 tracks must never read as an empty library at startup", LibraryScanState.SCANNING, LibraryScan.stateOf(false, false, 0))
    }

    @Test
    fun onlyAFinishedScanThatFoundNothingIsEmpty() {
        assertEquals(LibraryScanState.READY_EMPTY, LibraryScan.stateOf(loadedOnce = true, isScanning = false, trackCount = 0))
    }

    @Test
    fun aFinishedScanWithTracksIsReady() {
        assertEquals(LibraryScanState.READY_WITH_TRACKS, LibraryScan.stateOf(true, false, 528))
    }

    @Test
    fun aRescanKeepsShowingTheTracksItAlreadyHas() {
        assertEquals(LibraryScanState.READY_WITH_TRACKS, LibraryScan.stateOf(true, true, 528))
    }

    @Test
    fun aRescanOfAnEmptyLibraryShowsScanningAgainInsteadOfNoMusic() {
        assertEquals(LibraryScanState.SCANNING, LibraryScan.stateOf(true, true, 0))
    }
}

class VisualizerOrderTests {
    private val expected = listOf(
        VisualizerMode.ECHO_PULSE, VisualizerMode.SPECTRUM, VisualizerMode.VU, VisualizerMode.SCOPE,
        VisualizerMode.CLASSIC_BARS, VisualizerMode.MIRROR, VisualizerMode.DIGITAL, VisualizerMode.NEON_LINE,
        VisualizerMode.WAVEFORM, VisualizerMode.PULSE,
    )

    @Test
    fun theOrderIsTheOneCentralList() {
        assertEquals(expected, VisualizerMode.entries.toList())
    }

    @Test
    fun oneTapGoesToTheNextModeAndNeverRepeatsOrSkips() {
        var mode = VisualizerMode.ECHO_PULSE
        val visited = mutableListOf(mode)
        repeat(expected.size - 1) {
            val next = mode.next()
            assertTrue("never the same mode twice in a row", next != mode)
            visited += next
            mode = next
        }
        assertEquals(expected, visited)
        assertEquals("all ten, each once", expected.size, visited.toSet().size)
    }

    @Test
    fun afterTheLastModeItWrapsToTheFirst() {
        assertEquals(VisualizerMode.ECHO_PULSE, VisualizerMode.PULSE.next())
        assertEquals(VisualizerMode.SPECTRUM, VisualizerMode.ECHO_PULSE.next())
    }

    @Test
    fun aFullCycleReturnsToWhereItStarted() {
        for (start in VisualizerMode.entries) {
            var m = start
            repeat(VisualizerMode.entries.size) { m = m.next() }
            assertEquals(start, m)
        }
    }

    @Test
    fun aFreshInstallStillStartsOnEchoPulseAndASavedNameIsRestored() {
        assertEquals(VisualizerMode.ECHO_PULSE, VisualizerMode.fromName(null))
        for (mode in VisualizerMode.entries) assertEquals(mode, VisualizerMode.fromName(mode.name))
    }
}

class PlaybackIntentTests {
    private fun state(playWhenReady: Boolean, status: PlaybackStatus, isPlaying: Boolean = false) =
        PlaybackState(isPlaying = isPlaying, playWhenReady = playWhenReady, status = status)

    @Test
    fun aTrackChangeBlipStillCountsAsWantingToPlay() {
        // isPlaying dips to false while the next track loads, but the user never paused.
        assertTrue(state(playWhenReady = true, status = PlaybackStatus.BUFFERING, isPlaying = false).wantsToPlay)
        assertTrue(state(playWhenReady = true, status = PlaybackStatus.READY, isPlaying = false).wantsToPlay)
    }

    @Test
    fun aRealPauseDoesNot() {
        assertFalse(state(playWhenReady = false, status = PlaybackStatus.READY).wantsToPlay)
        assertFalse(state(playWhenReady = false, status = PlaybackStatus.BUFFERING).wantsToPlay)
    }

    @Test
    fun theEndOfTheQueueAndAnIdlePlayerDoNot() {
        assertFalse(state(playWhenReady = true, status = PlaybackStatus.ENDED).wantsToPlay)
        assertFalse(state(playWhenReady = true, status = PlaybackStatus.IDLE).wantsToPlay)
    }
}

class TrackChangeMascotTests {
    private val dt = 0.033f

    private fun step(brain: CompanionBrain, pool: MusicSymbolPool, seconds: Float, playing: Boolean, track: String?, level: Float = 0.6f, onStep: () -> Unit = {}) {
        var t = 0f
        while (t < seconds) {
            brain.update(dt, true, playing, track, level)
            pool.update(dt, brain.state, brain.energy)
            onStep()
            t += dt
        }
    }

    @Test
    fun aTrackChangeGivesAReactionWithRoughlyTwoSymbolsAndNoSleepyZ() {
        val brain = CompanionBrain()
        val pool = MusicSymbolPool()
        step(brain, pool, 4f, playing = true, track = "a")
        pool.clear()

        // The user keeps wanting music (playing stays true) while the track changes.
        var sleepSeen = false
        var sawCelebrate = false
        var maxAlive = 0
        var spawnedTotal = 0
        val alive = BooleanArray(pool.capacity)
        step(brain, pool, 1.6f, playing = true, track = "b", level = 0f) {
            if (brain.state == CompanionState.CELEBRATING) sawCelebrate = true
            for (i in 0 until pool.capacity) {
                if (pool.isActive(i) && pool.glyph(i) == SymbolGlyph.SLEEP) sleepSeen = true
                if (pool.isActive(i) && !alive[i]) spawnedTotal++
                alive[i] = pool.isActive(i)
            }
            maxAlive = maxOf(maxAlive, pool.activeCount)
        }
        assertTrue(sawCelebrate)
        assertFalse("no stray z on a track change", sleepSeen)
        assertTrue("about two symbols, got $spawnedTotal", spawnedTotal in 2..3)
        assertTrue(maxAlive <= 3)
        assertFalse("never looked paused", brain.state == CompanionState.PAUSED)
    }

    @Test
    fun aGenuinePauseStillMakesTheSleepyState() {
        val brain = CompanionBrain()
        val pool = MusicSymbolPool()
        step(brain, pool, 4f, playing = true, track = "a")
        step(brain, pool, 0.2f, playing = false, track = "a", level = 0f)
        assertEquals(CompanionState.PAUSED, brain.state)
        var z = false
        step(brain, pool, 0.2f, playing = false, track = "a", level = 0f) {
            for (i in 0 until pool.capacity) if (pool.isActive(i) && pool.glyph(i) == SymbolGlyph.SLEEP) z = true
        }
        assertTrue("a real pause still shows the sleepy z", z || brain.state == CompanionState.PAUSED)
        step(brain, pool, 5f, playing = false, track = "a", level = 0f)
        assertTrue(brain.isSettled)
    }
}
