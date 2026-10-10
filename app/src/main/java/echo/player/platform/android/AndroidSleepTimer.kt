package echo.player.platform.android

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import echo.player.playback.SleepTimer
import echo.player.playback.TickScheduler
import echo.player.playback.TimeSource

/** The Android clock: elapsed real time, which keeps counting with the screen off and cannot be set by the user. */
object ElapsedRealtimeSource : TimeSource {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}

/** Ticks on the main looper, which belongs to the process and not to any Activity. */
class HandlerTickScheduler(private val handler: Handler = Handler(Looper.getMainLooper())) : TickScheduler {
    private var pending: Runnable? = null

    override fun schedule(delayMs: Long, task: () -> Unit) {
        cancel()
        val run = Runnable {
            pending = null
            task()
        }
        pending = run
        handler.postDelayed(run, delayMs)
    }

    override fun cancel() {
        pending?.let(handler::removeCallbacks)
        pending = null
    }
}

/**
 * The one sleep timer of the app. It lives for the process, not for a screen: the Activity may be rotated,
 * recreated or closed while it counts. [echo.player.playback.PlaybackService] decides what expiry does (it
 * pauses playback) and cancels the timer when the service ends. It is deliberately not saved across a reboot.
 */
object SleepTimers {
    val timer: SleepTimer by lazy { SleepTimer(ElapsedRealtimeSource, HandlerTickScheduler()) }
}
