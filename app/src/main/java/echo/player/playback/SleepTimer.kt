package echo.player.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A clock that never goes backwards and keeps counting while the screen is off ([android.os.SystemClock.elapsedRealtime]). */
fun interface TimeSource {
    fun elapsedRealtimeMs(): Long
}

/** Runs one pending task after a delay. Scheduling again replaces the pending task. */
interface TickScheduler {
    fun schedule(delayMs: Long, task: () -> Unit)
    fun cancel()
}

sealed interface SleepTimerState {
    data object Inactive : SleepTimerState

    /** [remainingMs] is as of the latest tick (once a second); [totalMs] is the length the user asked for. */
    data class Active(val remainingMs: Long, val totalMs: Long) : SleepTimerState
}

object SleepTimerPresets {
    val MINUTES = listOf(15, 30, 45, 60, 90)
    const val MAX_MINUTES = 12 * 60

    fun minutesToMs(minutes: Int): Long = minutes * 60_000L

    /** Whether [minutes] is an acceptable custom length. */
    fun isValidMinutes(minutes: Int): Boolean = minutes in 1..MAX_MINUTES
}

/**
 * The sleep timer. It knows nothing about Compose, Activities or Media3: it keeps one deadline on a
 * monotonic clock, reports the time left about once a second, and calls [onExpired] exactly once when the
 * deadline passes. At most one timer exists; starting another replaces it.
 *
 * Remaining time is always computed from the deadline (`deadline - now`), never by counting ticks, so a late
 * or skipped tick cannot make it drift. The ticks only refresh what is displayed and notice expiry.
 */
class SleepTimer(
    private val time: TimeSource,
    private val scheduler: TickScheduler,
) {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Inactive)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    /** Called once when the timer runs out. The timer is already inactive when this runs. */
    var onExpired: (() -> Unit)? = null

    private var deadlineMs = 0L
    private var totalMs = 0L
    private var active = false

    val isActive: Boolean get() = active

    /** Time left, from the deadline. Zero when no timer is running. */
    fun remainingMs(): Long = if (active) (deadlineMs - time.elapsedRealtimeMs()).coerceAtLeast(0L) else 0L

    /** Starts (or replaces) the timer. Returns false, changing nothing, for a zero, negative or absurd length. */
    fun start(durationMs: Long): Boolean {
        if (durationMs <= 0L || durationMs > SleepTimerPresets.minutesToMs(SleepTimerPresets.MAX_MINUTES)) return false
        scheduler.cancel()
        totalMs = durationMs
        deadlineMs = time.elapsedRealtimeMs() + durationMs
        active = true
        _state.value = SleepTimerState.Active(durationMs, durationMs)
        scheduleNext(durationMs)
        return true
    }

    fun startMinutes(minutes: Int): Boolean = SleepTimerPresets.isValidMinutes(minutes) && start(SleepTimerPresets.minutesToMs(minutes))

    fun cancel() {
        scheduler.cancel()
        active = false
        _state.value = SleepTimerState.Inactive
    }

    private fun tick() {
        if (!active) return
        val remaining = deadlineMs - time.elapsedRealtimeMs()
        if (remaining <= 0L) {
            expire()
        } else {
            _state.value = SleepTimerState.Active(remaining, totalMs)
            scheduleNext(remaining)
        }
    }

    private fun expire() {
        active = false
        scheduler.cancel()
        _state.value = SleepTimerState.Inactive
        onExpired?.invoke()
    }

    /** Wakes when the displayed whole second changes, or at the deadline, whichever is first. */
    private fun scheduleNext(remainingMs: Long) {
        val untilNextSecond = (remainingMs - 1) % 1000 + 1
        scheduler.schedule(untilNextSecond) { tick() }
    }
}

object SleepTimerFormat {
    /** "45:00", "0:59" or "1:02:03". Rounds up, so a timer never shows 0:00 while it is still running. */
    fun format(remainingMs: Long): String {
        val totalSeconds = ((remainingMs.coerceAtLeast(0L) + 999L) / 1000L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }

    /** For screen readers: "23 minutes 41 seconds". */
    fun spoken(remainingMs: Long): String {
        val totalSeconds = ((remainingMs.coerceAtLeast(0L) + 999L) / 1000L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return buildString {
            if (hours > 0) append("$hours ${if (hours == 1L) "hour" else "hours"} ")
            if (minutes > 0) append("$minutes ${if (minutes == 1L) "minute" else "minutes"} ")
            if (seconds > 0 || isEmpty()) append("$seconds ${if (seconds == 1L) "second" else "seconds"}")
        }.trim()
    }
}
