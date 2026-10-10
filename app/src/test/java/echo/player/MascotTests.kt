package echo.player

import echo.player.companion.AnimationRate
import echo.player.companion.CompanionBrain
import echo.player.companion.CompanionState
import echo.player.companion.MascotAnimationPolicy
import echo.player.companion.MascotExpression
import echo.player.companion.MusicSymbolPool
import echo.player.companion.StaticMascotPose
import echo.player.companion.SymbolGlyph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MusicSymbolTests {
    private val dt = 0.033f

    /** Runs [seconds] of the pool in [state] at [energy]; returns how many symbols appeared (by counting new slots). */
    private fun MusicSymbolPool.run(seconds: Float, state: CompanionState, energy: Float, onStep: (MusicSymbolPool) -> Unit = {}) {
        var t = 0f
        while (t < seconds) {
            update(dt, state, energy)
            onStep(this)
            t += dt
        }
    }

    private fun MusicSymbolPool.spawnedWhile(seconds: Float, state: CompanionState, energy: Float): Int {
        var previous = BooleanArray(capacity)
        var spawned = 0
        run(seconds, state, energy) { pool ->
            for (i in 0 until pool.capacity) {
                val now = pool.isActive(i)
                if (now && (!previous[i] || pool.progress(i) < 0.03f)) spawned++
                previous[i] = now
            }
        }
        return spawned
    }

    @Test
    fun aQuietPoolHasNothingAlive() {
        val pool = MusicSymbolPool()
        assertTrue(pool.isIdle)
        assertEquals(0, pool.activeCount)
        pool.run(3f, CompanionState.SLEEPING, 0f)
        assertTrue(pool.isIdle)
    }

    @Test
    fun neverMoreThanTheFewSymbolsAreAliveAtOnce() {
        val pool = MusicSymbolPool(capacity = 5)
        var peak = 0
        pool.run(30f, CompanionState.ENERGETIC, 1f) { peak = maxOf(peak, it.activeCount) }
        assertTrue("peak=$peak", peak in 1..4)
        assertTrue(peak <= pool.capacity)
    }

    @Test
    fun louderMusicMakesMoreSymbols() {
        val quiet = MusicSymbolPool().spawnedWhile(20f, CompanionState.DANCING, 0.05f)
        val loud = MusicSymbolPool().spawnedWhile(20f, CompanionState.ENERGETIC, 1f)
        assertTrue("quiet=$quiet loud=$loud", loud > quiet)
        assertTrue("quiet music still makes some", quiet >= 3)
    }

    @Test
    fun noMusicSymbolsAppearWhilePausedOrAsleep() {
        for (state in listOf(CompanionState.PAUSED, CompanionState.SLEEPING)) {
            val pool = MusicSymbolPool()
            assertEquals(0, pool.spawnedWhile(10f, state, 1f))
        }
    }

    @Test
    fun pausingLetsOneSleepySymbolAppearOnceThenEverythingFades() {
        val pool = MusicSymbolPool()
        pool.run(6f, CompanionState.DANCING, 0.6f)
        assertTrue(pool.activeCount > 0)

        pool.update(dt, CompanionState.PAUSED, 0f)
        val sleepy = (0 until pool.capacity).filter { pool.isActive(it) && pool.glyph(it) == SymbolGlyph.SLEEP }
        assertEquals("one z on pausing", 1, sleepy.size)

        var sleepSeen = 1
        var previousSleepActive = true
        pool.run(8f, CompanionState.PAUSED, 0f) {
            val active = (0 until it.capacity).any { i -> it.isActive(i) && it.glyph(i) == SymbolGlyph.SLEEP }
            if (active && !previousSleepActive) sleepSeen++
            previousSleepActive = active
        }
        assertEquals("the z is not repeated while staying paused", 1, sleepSeen)
        assertTrue("everything is gone and the pool can stop", pool.isIdle)
    }

    @Test
    fun theMusicSymbolsHurryAwayWhenPaused() {
        val pool = MusicSymbolPool()
        pool.run(6f, CompanionState.DANCING, 0.6f)
        pool.update(dt, CompanionState.PAUSED, 0f)
        pool.run(1.2f, CompanionState.PAUSED, 0f)
        val musical = (0 until pool.capacity).count { pool.isActive(it) && pool.glyph(it) != SymbolGlyph.SLEEP }
        assertEquals("all music symbols are gone within about a second", 0, musical)
    }

    @Test
    fun wakingMakesOneSymbol() {
        val pool = MusicSymbolPool()
        pool.update(dt, CompanionState.WAKING, 0.2f)
        assertEquals(1, pool.activeCount)
        assertEquals(SymbolGlyph.NOTE, (0 until pool.capacity).first { pool.isActive(it) }.let(pool::glyph))
    }

    @Test
    fun aCelebrationMakesAtMostTwo() {
        val pool = MusicSymbolPool()
        var spawned = 0
        pool.update(dt, CompanionState.CELEBRATING, 0.5f)
        pool.run(1f, CompanionState.CELEBRATING, 0.5f)
        for (i in 0 until pool.capacity) if (pool.isActive(i)) spawned++
        assertTrue("alive=$spawned", spawned in 1..2)
    }

    @Test
    fun symbolsRiseFadeAndAreReused() {
        val pool = MusicSymbolPool()
        pool.update(dt, CompanionState.WAKING, 0.5f)
        val slot = (0 until pool.capacity).first { pool.isActive(it) }
        var lastRise = -1f
        var sawFull = false
        var steps = 0
        while (pool.isActive(slot) && steps < 400) {
            pool.update(dt, CompanionState.DANCING, 0f)
            if (pool.isActive(slot)) {
                assertTrue("rises", pool.rise(slot) >= lastRise)
                lastRise = pool.rise(slot)
                assertTrue(pool.alpha(slot) in 0f..1f)
                if (pool.alpha(slot) >= 0.99f) sawFull = true
            }
            steps++
        }
        assertTrue(sawFull)
        assertFalse("it ends", pool.isActive(slot) && steps >= 400)
        // The same slots keep being reused: the pool never grows.
        pool.run(20f, CompanionState.ENERGETIC, 1f)
        assertEquals(5, pool.capacity)
    }

    @Test
    fun theSameAudioAlwaysGivesTheSameSymbols() {
        val a = MusicSymbolPool()
        val b = MusicSymbolPool()
        var t = 0f
        while (t < 15f) {
            val energy = 0.5f + 0.4f * kotlin.math.sin(t * 1.3f)
            a.update(dt, CompanionState.DANCING, energy)
            b.update(dt, CompanionState.DANCING, energy)
            for (i in 0 until a.capacity) {
                assertEquals(a.isActive(i), b.isActive(i))
                if (a.isActive(i)) {
                    assertEquals(a.x(i), b.x(i), 0f)
                    assertEquals(a.glyph(i), b.glyph(i))
                }
            }
            t += dt
        }
    }

    @Test
    fun clearingFreesEverything() {
        val pool = MusicSymbolPool()
        pool.run(5f, CompanionState.ENERGETIC, 1f)
        pool.clear()
        assertTrue(pool.isIdle)
    }

    @Test
    fun symbolsDriftSidewaysButStayNearTheHead() {
        val pool = MusicSymbolPool()
        pool.run(30f, CompanionState.ENERGETIC, 1f) {
            for (i in 0 until it.capacity) if (it.isActive(i)) assertTrue(abs(it.x(i)) < 1f)
        }
    }
}

class MascotStateTests {
    private val dt = 0.033f
    private fun CompanionBrain.run(seconds: Float, playing: Boolean = true, hasTrack: Boolean = true, level: Float = 0.6f, track: String? = "a") {
        var t = 0f
        while (t < seconds) { update(dt, hasTrack, playing, track, level); t += dt }
    }

    private fun motion(level: Float): Float {
        val brain = CompanionBrain()
        brain.run(3f, level = level)
        var bob = 0f
        var arms = 0f
        var t = 0f
        while (t < 4f) {
            brain.update(dt, true, true, "a", level)
            bob = maxOf(bob, abs(brain.bob)); arms = maxOf(arms, brain.arms)
            t += dt
        }
        return bob + arms
    }

    @Test
    fun lowEnergyMeansLowMotionAndHighEnergyMeansMore() {
        val low = motion(0.38f)
        val high = motion(0.95f)
        assertTrue("low=$low high=$high", high > low * 1.4f)
        assertTrue("low energy is still gentle", low < 1.2f)
    }

    @Test
    fun theSleepyPoseArrivesThenTheBrainIsSettled() {
        val brain = CompanionBrain()
        brain.run(3f)
        brain.run(0.1f, playing = false)
        assertFalse("not settled straight after pausing", brain.isSettled)
        brain.run(4f, playing = false)
        assertTrue(brain.isSettled)
        assertEquals(CompanionState.PAUSED, brain.state)
    }

    @Test
    fun emptyStateExpressionsAreStillAndMatchTheirMood() {
        val asleep = StaticMascotPose(MascotExpression.SLEEPING)
        assertEquals(CompanionState.SLEEPING, asleep.state)
        assertTrue(asleep.eyeOpen < 0.05f && asleep.glow < 0.1f && asleep.slump > 0.9f)
        val waiting = StaticMascotPose(MascotExpression.WAITING)
        assertTrue(waiting.eyeOpen > 0.9f && waiting.slump < 0.1f)
        val unavailable = StaticMascotPose(MascotExpression.UNAVAILABLE)
        assertEquals(CompanionState.PAUSED, unavailable.state)
        assertTrue(unavailable.glow < 0.4f)
        for (e in MascotExpression.entries) {
            val p = StaticMascotPose(e)
            assertEquals("a still pose does not move", 0f, p.bob, 0f)
            assertEquals(0f, p.arms, 0f)
        }
    }
}

class MascotAnimationPolicyTests {
    @Test
    fun theSettingOffStopsAllMascotAnimation() {
        for (playing in listOf(true, false)) for (settled in listOf(true, false)) for (idle in listOf(true, false)) {
            assertEquals(
                "off must mean off (playing=$playing settled=$settled idle=$idle)",
                AnimationRate.STOPPED,
                MascotAnimationPolicy.rate(companionEnabled = false, isPlaying = playing, brainSettled = settled, symbolsIdle = idle),
            )
        }
    }

    @Test
    fun playingAnimatesAtTheFullRate() {
        assertEquals(AnimationRate.ACTIVE, MascotAnimationPolicy.rate(true, isPlaying = true, brainSettled = true, symbolsIdle = true))
        assertEquals(AnimationRate.ACTIVE, MascotAnimationPolicy.rate(true, isPlaying = true, brainSettled = false, symbolsIdle = false))
    }

    @Test
    fun pausedOnlyFinishesItsTransitionThenStops() {
        assertEquals(AnimationRate.RESTING, MascotAnimationPolicy.rate(true, false, brainSettled = false, symbolsIdle = true))
        assertEquals(AnimationRate.RESTING, MascotAnimationPolicy.rate(true, false, brainSettled = true, symbolsIdle = false))
        assertEquals(AnimationRate.STOPPED, MascotAnimationPolicy.rate(true, false, brainSettled = true, symbolsIdle = true))
    }

    @Test
    fun afterAPauseTheWholeSystemReallyReachesStopped() {
        val brain = CompanionBrain()
        val pool = MusicSymbolPool()
        val dt = 0.1f
        var t = 0f
        while (t < 4f) {
            brain.update(dt, true, true, "a", 0.7f); pool.update(dt, brain.state, brain.energy); t += dt
        }
        var stoppedAt = -1f
        t = 0f
        while (t < 15f) {
            brain.update(dt, true, false, "a", 0f); pool.update(dt, brain.state, brain.energy)
            if (MascotAnimationPolicy.rate(true, false, brain.isSettled, pool.isIdle) == AnimationRate.STOPPED) { stoppedAt = t; break }
            t += dt
        }
        assertTrue("animation must stop on its own after a pause (stoppedAt=$stoppedAt)", stoppedAt in 0f..8f)
    }

    @Test
    fun noTrackStopsOnceAsleep() {
        val brain = CompanionBrain()
        val pool = MusicSymbolPool()
        var t = 0f
        while (t < 5f) { brain.update(0.1f, false, false, null, 0f); pool.update(0.1f, brain.state, brain.energy); t += 0.1f }
        assertEquals(AnimationRate.STOPPED, MascotAnimationPolicy.rate(true, false, brain.isSettled, pool.isIdle))
        assertEquals(CompanionState.SLEEPING, brain.state)
    }
}
