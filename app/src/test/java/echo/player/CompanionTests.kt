package echo.player

import echo.player.companion.CompanionBrain
import echo.player.companion.CompanionLogic
import echo.player.companion.CompanionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionTests {
    private val dt = 0.033f

    /** Steps the brain for [seconds] with fixed inputs. */
    private fun CompanionBrain.run(
        seconds: Float,
        hasTrack: Boolean = true,
        playing: Boolean = true,
        track: String? = "a",
        level: Float = 0.6f,
    ) {
        var t = 0f
        while (t < seconds) {
            update(dt, hasTrack, playing, track, level)
            t += dt
        }
    }

    private fun playingBrain(level: Float = 0.6f): CompanionBrain = CompanionBrain().apply { run(2f, level = level) }

    // region state mapping

    @Test
    fun playbackStateMapsToTheRestingState() {
        assertEquals(CompanionState.DANCING, CompanionLogic.baseState(hasTrack = true, isPlaying = true))
        assertEquals(CompanionState.PAUSED, CompanionLogic.baseState(hasTrack = true, isPlaying = false))
        assertEquals(CompanionState.SLEEPING, CompanionLogic.baseState(hasTrack = false, isPlaying = false))
        assertEquals("no track beats a stale playing flag", CompanionState.SLEEPING, CompanionLogic.baseState(hasTrack = false, isPlaying = true))
    }

    @Test
    fun playingMusicDances() {
        assertEquals(CompanionState.DANCING, playingBrain(0.5f).state)
    }

    @Test
    fun pausedIsSadAndSleepy() {
        val brain = playingBrain()
        brain.run(0.1f, playing = false)
        assertEquals(CompanionState.PAUSED, brain.state)
        brain.run(2f, playing = false)
        assertTrue("eyes sleepy", brain.eyeOpen < 0.5f)
        assertTrue("display dimmed", brain.glow < 0.4f)
        assertTrue("posture lowered", brain.slump > 0.6f)
    }

    @Test
    fun pausingStopsTheDanceRightAway() {
        val brain = playingBrain(0.8f)
        assertTrue(brain.energy > 0.2f)
        brain.update(dt, true, false, "a", 0.8f)
        assertEquals("the state changes on the very first update after the pause", CompanionState.PAUSED, brain.state)
        brain.run(1.5f, playing = false)
        assertTrue("movement dies away, bob=${brain.bob}", kotlin.math.abs(brain.bob) < 0.03f)
        assertTrue(brain.arms < 0.03f)
    }

    @Test
    fun noTrackSleepsWithTheDisplayOff() {
        val brain = playingBrain()
        brain.run(3f, hasTrack = false, playing = false, track = null, level = 0f)
        assertEquals(CompanionState.SLEEPING, brain.state)
        assertTrue(brain.glow < 0.1f)
        assertTrue(brain.eyeOpen < 0.05f)
        assertTrue(brain.isSettled)
    }

    @Test
    fun aStaleLevelCannotMakeASleepingCompanionDance() {
        val brain = CompanionBrain()
        brain.run(2f, hasTrack = false, playing = false, track = null, level = 1f)
        assertEquals(CompanionState.SLEEPING, brain.state)
        assertEquals(0f, brain.energy, 0.001f)
    }

    @Test
    fun resumingWakesUpThenDances() {
        val brain = playingBrain()
        brain.run(2f, playing = false)
        assertEquals(CompanionState.PAUSED, brain.state)

        brain.update(dt, true, true, "a", 0.6f)
        assertEquals(CompanionState.WAKING, brain.state)
        brain.run(0.3f)
        assertEquals("still waking", CompanionState.WAKING, brain.state)
        brain.run(0.8f)
        assertEquals(CompanionState.DANCING, brain.state)
    }

    @Test
    fun startingFromNothingAlsoWakesUp() {
        val brain = CompanionBrain()
        brain.run(1f, hasTrack = false, playing = false, track = null, level = 0f)
        brain.update(dt, true, true, "a", 0.6f)
        assertEquals(CompanionState.WAKING, brain.state)
        brain.run(1f)
        assertEquals(CompanionState.DANCING, brain.state)
    }

    @Test
    fun aTrackChangeCelebratesBrieflyThenDancesAgain() {
        val brain = playingBrain()
        brain.update(dt, true, true, "b", 0.6f)
        assertEquals(CompanionState.CELEBRATING, brain.state)
        brain.run(0.4f, track = "b")
        assertEquals(CompanionState.CELEBRATING, brain.state)
        brain.run(1f, track = "b")
        assertEquals(CompanionState.DANCING, brain.state)
    }

    @Test
    fun aTrackChangeWhilePausedDoesNotCelebrate() {
        val brain = playingBrain()
        brain.run(1f, playing = false)
        brain.update(dt, true, false, "b", 0f)
        assertEquals(CompanionState.PAUSED, brain.state)
    }

    @Test
    fun theSameTrackDoesNotRetriggerTheCelebration() {
        val brain = playingBrain()
        brain.run(5f, track = "a")
        assertTrue(brain.state != CompanionState.CELEBRATING)
    }

    // endregion

    // region energy

    @Test
    fun energyIsNormalizedAndBounded() {
        assertEquals(0f, CompanionLogic.normalizeEnergy(0f), 0f)
        assertEquals(0f, CompanionLogic.normalizeEnergy(CompanionLogic.ENERGY_FLOOR), 0.0001f)
        assertEquals(1f, CompanionLogic.normalizeEnergy(CompanionLogic.ENERGY_CEILING), 0.0001f)
        assertEquals(1f, CompanionLogic.normalizeEnergy(1f), 0f)
        assertTrue(CompanionLogic.normalizeEnergy(0.6f) in 0f..1f)
        var previous = -1f
        for (i in 0..100) {
            val v = CompanionLogic.normalizeEnergy(i / 100f)
            assertTrue("monotonic", v >= previous)
            previous = v
        }
    }

    @Test
    fun energyIsSmoothedNotJumpedTo() {
        val brain = playingBrain(0.4f)
        val start = brain.energy
        brain.update(dt, true, true, "a", 1f)
        val oneStep = brain.energy
        assertTrue("one frame must not reach the target", oneStep < 0.6f)
        assertTrue(oneStep >= start)
        var last = oneStep
        repeat(30) {
            brain.update(dt, true, true, "a", 1f)
            assertTrue("rises steadily", brain.energy >= last - 1e-6f)
            last = brain.energy
        }
        assertTrue(last > 0.9f)
    }

    @Test
    fun loudMusicGetsMoreEnergeticAndQuietCalmsAgainWithHysteresis() {
        val brain = playingBrain(0.5f)
        assertEquals(CompanionState.DANCING, brain.state)
        brain.run(3f, level = 0.95f)
        assertEquals(CompanionState.ENERGETIC, brain.state)
        // A small dip must not drop it straight back (hysteresis), a real drop does.
        brain.run(0.2f, level = 0.72f)
        assertEquals(CompanionState.ENERGETIC, brain.state)
        brain.run(4f, level = 0.4f)
        assertEquals(CompanionState.DANCING, brain.state)
    }

    @Test
    fun louderMusicDancesBiggerThanQuietMusic() {
        fun peakBob(level: Float): Float {
            val brain = CompanionBrain()
            brain.run(2.5f, level = level)
            var peak = 0f
            var t = 0f
            while (t < 3f) {
                brain.update(dt, true, true, "a", level)
                peak = maxOf(peak, kotlin.math.abs(brain.bob))
                t += dt
            }
            return peak
        }
        assertTrue(peakBob(0.95f) > peakBob(0.4f))
    }

    @Test
    fun theDanceFollowsTheAudioNotARandomNumber() {
        // Two brains fed identical audio move identically: there is no hidden randomness.
        val a = CompanionBrain()
        val b = CompanionBrain()
        var t = 0f
        while (t < 4f) {
            val level = 0.5f + 0.4f * kotlin.math.sin(t * 3f)
            a.update(dt, true, true, "a", level)
            b.update(dt, true, true, "a", level)
            assertEquals(a.bob, b.bob, 0f)
            assertEquals(a.tilt, b.tilt, 0f)
            t += dt
        }
    }

    @Test
    fun outputsStayInRangeWhateverTheInput() {
        val brain = CompanionBrain()
        var seed = 12345L
        fun next(): Float {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return ((seed ushr 33) % 1000) / 1000f
        }
        repeat(2000) { i ->
            val playing = (i / 120) % 2 == 0
            val track = if (i % 211 == 0) "t$i" else "t0"
            brain.update(dt, hasTrack = i % 500 < 450, isPlaying = playing, trackId = track, meterLevel = next() * 1.4f - 0.2f)
            assertTrue(brain.bob in -1f..1f)
            assertTrue(brain.tilt in -1f..1f)
            assertTrue(brain.eyeOpen in 0f..1f)
            assertTrue(brain.glow in 0f..1.0001f)
            assertTrue(brain.slump in 0f..1f)
            assertTrue(brain.arms in 0f..1.0001f)
            assertTrue(brain.energy in 0f..1f)
            assertFalse(brain.bob.isNaN() || brain.tilt.isNaN() || brain.glow.isNaN())
        }
    }

    // endregion
}
