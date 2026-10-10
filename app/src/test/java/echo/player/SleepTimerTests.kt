package echo.player

import echo.player.playback.SleepTimer
import echo.player.playback.SleepTimerFormat
import echo.player.playback.SleepTimerPresets
import echo.player.playback.SleepTimerState
import echo.player.playback.TickScheduler
import echo.player.playback.TimeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Virtual time: nothing here ever waits. [advance] moves the clock and runs the scheduled tick when it is due. */
private class FakeClock : TimeSource, TickScheduler {
    var now = 1_000_000L
    private var dueAt: Long? = null
    private var task: (() -> Unit)? = null
    var scheduleCount = 0

    override fun elapsedRealtimeMs() = now

    override fun schedule(delayMs: Long, task: () -> Unit) {
        scheduleCount++
        dueAt = now + delayMs
        this.task = task
    }

    override fun cancel() {
        dueAt = null
        task = null
    }

    val hasPendingTick: Boolean get() = task != null

    fun advance(ms: Long) {
        val target = now + ms
        while (true) {
            val due = dueAt
            if (due == null || due > target) break
            now = due
            val run = task
            dueAt = null
            task = null
            run?.invoke()
        }
        now = target
    }

    /** Jumps the clock without running anything, like a device that was suspended (ticks arrive late). */
    fun jump(ms: Long) { now += ms }

    fun fireDueLate() {
        val run = task
        dueAt = null
        task = null
        run?.invoke()
    }
}

class SleepTimerTests {
    private fun timer(clock: FakeClock = FakeClock()) = SleepTimer(clock, clock) to clock

    private fun SleepTimer.remaining() = (state.value as? SleepTimerState.Active)?.remainingMs

    @Test
    fun theFivePresetsStartTimersOfExactlyThatLength() {
        assertEquals(listOf(15, 30, 45, 60, 90), SleepTimerPresets.MINUTES)
        for (minutes in SleepTimerPresets.MINUTES) {
            val (t, _) = timer()
            assertTrue(t.startMinutes(minutes))
            assertEquals(minutes * 60_000L, t.remainingMs())
            assertEquals(minutes * 60_000L, (t.state.value as SleepTimerState.Active).totalMs)
        }
    }

    @Test
    fun aCustomLengthWorks() {
        val (t, _) = timer()
        assertTrue(t.startMinutes(7))
        assertEquals(7 * 60_000L, t.remainingMs())
        assertTrue(t.startMinutes(1))
        assertEquals(60_000L, t.remainingMs())
        assertTrue(t.startMinutes(SleepTimerPresets.MAX_MINUTES))
    }

    @Test
    fun zeroNegativeAndAbsurdLengthsAreRejectedWithoutChangingAnything() {
        val (t, _) = timer()
        assertFalse(t.start(0))
        assertFalse(t.start(-5_000))
        assertFalse(t.startMinutes(0))
        assertFalse(t.startMinutes(-3))
        assertFalse(t.startMinutes(SleepTimerPresets.MAX_MINUTES + 1))
        assertFalse(t.isActive)
        assertEquals(SleepTimerState.Inactive, t.state.value)

        // A rejected request must not disturb a timer that is already running.
        assertTrue(t.startMinutes(30))
        assertFalse(t.start(0))
        assertTrue(t.isActive)
        assertEquals(30 * 60_000L, t.remainingMs())
    }

    @Test
    fun remainingTimeComesFromTheDeadlineNotFromCountingTicks() {
        val (t, clock) = timer()
        t.startMinutes(15)
        // The clock moves by 7 minutes 13.4 seconds in one go: no tick ran, yet the answer is exact.
        clock.jump(433_400)
        assertEquals(15 * 60_000L - 433_400L, t.remainingMs())
    }

    @Test
    fun theDisplayedTimeFollowsTheClockOncePerSecond() {
        val (t, clock) = timer()
        t.start(10_000)
        clock.advance(1000)
        assertEquals(9_000L, t.remaining())
        clock.advance(3000)
        assertEquals(6_000L, t.remaining())
        clock.advance(500)
        assertEquals("a half second later the next tick has not happened yet", 6_000L, t.remaining())
        clock.advance(500)
        assertEquals(5_000L, t.remaining())
    }

    @Test
    fun aLateTickCannotMakeTheTimeDrift() {
        val (t, clock) = timer()
        t.start(60_000)
        clock.jump(20_000) // the device was suspended; the tick is 19 seconds late
        clock.fireDueLate()
        assertEquals(40_000L, t.remaining())
        clock.advance(40_000)
        assertFalse(t.isActive)
    }

    @Test
    fun reachingZeroRequestsPlaybackToStopExactlyOnce() {
        val (t, clock) = timer()
        var stops = 0
        t.onExpired = { stops++ }
        t.startMinutes(15)
        clock.advance(15 * 60_000L - 1)
        assertEquals(0, stops)
        assertTrue(t.isActive)
        clock.advance(1)
        assertEquals(1, stops)
        assertFalse(t.isActive)
        assertEquals(SleepTimerState.Inactive, t.state.value)
        clock.advance(10 * 60_000L)
        assertEquals("it never fires again", 1, stops)
        assertFalse(clock.hasPendingTick)
    }

    @Test
    fun theTimerIsAlreadyInactiveWhenTheStopRuns() {
        val (t, clock) = timer()
        var activeDuringStop: Boolean? = null
        t.onExpired = { activeDuringStop = t.isActive }
        t.start(2_000)
        clock.advance(2_000)
        assertEquals(false, activeDuringStop)
    }

    @Test
    fun cancellingMeansNoStopEver() {
        val (t, clock) = timer()
        var stops = 0
        t.onExpired = { stops++ }
        t.startMinutes(30)
        clock.advance(10 * 60_000L)
        t.cancel()
        assertFalse(t.isActive)
        assertEquals(SleepTimerState.Inactive, t.state.value)
        assertEquals(0L, t.remainingMs())
        assertFalse("its pending tick is gone", clock.hasPendingTick)
        clock.advance(2 * 60 * 60_000L)
        assertEquals(0, stops)
    }

    @Test
    fun cancellingJustBeforeExpiryStillMeansNoStop() {
        val (t, clock) = timer()
        var stops = 0
        t.onExpired = { stops++ }
        t.start(5_000)
        clock.advance(4_999)
        t.cancel()
        clock.advance(10_000)
        assertEquals(0, stops)
    }

    @Test
    fun replacingATimerLeavesOnlyTheNewOne() {
        val (t, clock) = timer()
        var stops = 0
        t.onExpired = { stops++ }
        t.startMinutes(45)
        clock.advance(60_000)
        t.startMinutes(15)
        assertEquals(15 * 60_000L, t.remainingMs())
        assertEquals(15 * 60_000L, (t.state.value as SleepTimerState.Active).totalMs)

        clock.advance(15 * 60_000L - 1)
        assertEquals(0, stops)
        clock.advance(1)
        assertEquals("only the 15-minute timer fired", 1, stops)

        // And the old 45-minute deadline does nothing later.
        clock.advance(60 * 60_000L)
        assertEquals(1, stops)
    }

    @Test
    fun replacingAFreshTimerWithALongerOneAlsoLeavesOne() {
        val (t, clock) = timer()
        var stops = 0
        t.onExpired = { stops++ }
        t.startMinutes(1)
        t.startMinutes(30)
        clock.advance(2 * 60_000L)
        assertEquals(0, stops)
        assertEquals(28 * 60_000L, t.remainingMs())
    }

    @Test
    fun aVeryShortTimerWorks() {
        val (t, clock) = timer()
        var stops = 0
        t.onExpired = { stops++ }
        t.start(1)
        clock.advance(1)
        assertEquals(1, stops)
    }

    @Test
    fun startingAfterExpiryWorksAgain() {
        val (t, clock) = timer()
        var stops = 0
        t.onExpired = { stops++ }
        t.start(1_000)
        clock.advance(1_000)
        t.start(1_000)
        clock.advance(1_000)
        assertEquals(2, stops)
    }

    @Test
    fun cancelWithNothingRunningIsHarmless() {
        val (t, _) = timer()
        t.cancel()
        assertFalse(t.isActive)
        assertEquals(0L, t.remainingMs())
    }

    @Test
    fun theTimerNeedsNothingFromComposeOrAnActivity() {
        // It is constructed from nothing but a clock and a scheduler and runs to completion on its own: no Activity,
        // lifecycle or composition exists in this test, which is exactly the situation of a backgrounded app.
        val (t, clock) = timer()
        var stopped = false
        t.onExpired = { stopped = true }
        t.startMinutes(90)
        var seconds = 0
        while (!stopped && seconds < 90 * 60 + 5) {
            clock.advance(1000)
            seconds++
        }
        assertTrue(stopped)
        assertEquals(90 * 60, seconds)
    }

    @Test
    fun theWorkIsOneTickPerSecondAtMost() {
        val (t, clock) = timer()
        t.startMinutes(1)
        clock.advance(60_000)
        assertTrue("ticks=${clock.scheduleCount}", clock.scheduleCount <= 62)
    }

    @Test
    fun countdownTextIsReadable() {
        assertEquals("45:00", SleepTimerFormat.format(45 * 60_000L))
        assertEquals("23:41", SleepTimerFormat.format(23 * 60_000L + 41_000L))
        assertEquals("0:59", SleepTimerFormat.format(59_000))
        assertEquals("1:30:00", SleepTimerFormat.format(90 * 60_000L))
        assertEquals("1:02:03", SleepTimerFormat.format(3_723_000L))
        assertEquals("rounds up so a running timer never reads 0:00", "0:01", SleepTimerFormat.format(1))
        assertEquals("0:00", SleepTimerFormat.format(0))
        assertEquals("0:00", SleepTimerFormat.format(-100))
    }

    @Test
    fun spokenCountdownForScreenReaders() {
        assertEquals("23 minutes 41 seconds", SleepTimerFormat.spoken(23 * 60_000L + 41_000L))
        assertEquals("1 hour 30 minutes", SleepTimerFormat.spoken(90 * 60_000L))
        assertEquals("1 minute", SleepTimerFormat.spoken(60_000))
        assertEquals("1 second", SleepTimerFormat.spoken(1_000))
    }

    @Test
    fun customMinutesValidation() {
        assertFalse(SleepTimerPresets.isValidMinutes(0))
        assertTrue(SleepTimerPresets.isValidMinutes(1))
        assertTrue(SleepTimerPresets.isValidMinutes(720))
        assertFalse(SleepTimerPresets.isValidMinutes(721))
        assertNull((SleepTimerState.Inactive as? SleepTimerState.Active))
    }
}
