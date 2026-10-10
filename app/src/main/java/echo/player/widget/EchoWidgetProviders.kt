package echo.player.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import echo.player.data.local.FavoriteStore
import echo.player.domain.model.RemoteMediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import echo.player.playback.PlaybackService

/** Base for the three widgets. They only redraw from the remembered state; the layouts differ, the data does not. */
abstract class EchoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        EchoWidgetUpdater.refresh(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        EchoWidgetUpdater.refresh(context)
    }
}

/** 2 x 1: identity, title and play/pause. */
class EchoCompactWidget : EchoWidgetProvider()

/** 4 x 1: title, artist, previous / play-pause / next. */
class EchoPlayerWidget : EchoWidgetProvider()

/** 4 x 2: the ECHO Pulse widget. */
class EchoPulseWidget : EchoWidgetProvider()

/**
 * Receives widget button taps and forwards them to the one existing Media3 session through a short-lived
 * MediaController (connect, send, release). It owns no player and no service of its own. Works with the app UI
 * closed: connecting starts the playback service if needed.
 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = WidgetAction.fromIntentAction(intent.action) ?: return
        val pending = goAsync()
        val app = context.applicationContext
        val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        val main = Handler(Looper.getMainLooper())
        var finished = false
        fun finish(controller: MediaController?) {
            if (finished) return
            finished = true
            controller?.release()
            pending.finish()
        }
        future.addListener({
            val controller = try { future.get() } catch (e: Exception) { finish(null); return@addListener }
            if (action == WidgetAction.FAVORITE) {
                // Favorites are not a player command: read what is playing, flip it in the database, and let the
                // signal refresh the widgets and the app. The controller is released as soon as that is done.
                val id = controller.currentMediaItem?.mediaId
                if (id == null || RemoteMediaId.isRemote(id)) {
                    finish(controller)
                } else {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            FavoriteStore.toggle(app, id)
                        } finally {
                            withContext(Dispatchers.Main) { finish(controller) }
                        }
                    }
                }
                return@addListener
            }
            val commands = ControllerCommands(controller)
            if (controller.mediaItemCount == 0) {
                // The service has just started and is still restoring the last queue: wait briefly for it.
                // The session restore sets the queue, then prepares it paused. A play sent before it is prepared would be
                // overridden, so wait until the restored queue is READY.
                val waiter = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (controller.mediaItemCount > 0 && playbackState == Player.STATE_READY) {
                            controller.removeListener(this)
                            apply(action, controller, commands)
                            finish(controller)
                        }
                    }
                }
                controller.addListener(waiter)
                main.postDelayed({ if (!finished) { controller.removeListener(waiter); finish(controller) } }, 6000)
            } else {
                apply(action, controller, commands)
                finish(controller)
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun apply(action: WidgetAction, controller: MediaController, commands: ControllerCommands) {
        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
        WidgetActions.apply(action, commands, controller.hasNextMediaItem(), controller.hasPreviousMediaItem())
    }

    private class ControllerCommands(private val c: MediaController) : WidgetPlayerCommands {
        override val isPlaying: Boolean get() = c.playWhenReady && c.playbackState != Player.STATE_ENDED
        override fun play() = c.play()
        override fun pause() = c.pause()
        override fun next() = c.seekToNextMediaItem().also { c.play() }
        override fun previous() = c.seekToPreviousMediaItem().also { c.play() }
    }
}
