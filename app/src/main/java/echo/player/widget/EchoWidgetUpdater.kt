package echo.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import echo.player.MainActivity
import echo.player.R

/** [showsControls]: previous/next; [showsTime]: elapsed and total time; [showsFavorite]: the heart. All show the bar. */
enum class WidgetKind(val providerClass: Class<*>, val layout: Int, val showsControls: Boolean, val showsTime: Boolean, val showsFavorite: Boolean) {
    COMPACT(EchoCompactWidget::class.java, R.layout.widget_compact, false, false, false),
    PLAYER(EchoPlayerWidget::class.java, R.layout.widget_player, true, true, true),
    PULSE(EchoPulseWidget::class.java, R.layout.widget_pulse, true, true, true),
}

/**
 * Draws every ECHO widget from one [TrackPresentationState]. It is called only when something meaningful changes
 * (track, play/pause, queue edges, stop) or when a widget is added or restored; there is no timer, loop or polling,
 * so an idle widget costs nothing. The state comes from the single playback service, which pushes it here.
 */
object EchoWidgetUpdater {
    private const val PREFS = "echo_widget"
    private const val KEY_SNAPSHOT = "snapshot"
    const val EXTRA_OPEN_NOW_PLAYING = "echo.player.extra.OPEN_NOW_PLAYING"

    @Volatile private var lastDrawn: String? = null

    /** Called by the playback service on a real state change. Always remembers it; redraws only if widgets exist and it changed. */
    fun push(context: Context, state: TrackPresentationState) {
        val app = context.applicationContext
        val encoded = WidgetSnapshot.encode(state)
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SNAPSHOT, encoded).apply()
        if (encoded == lastDrawn) return
        if (updateAll(app, state)) lastDrawn = encoded
    }

    /** Called when a widget is added, resized, restored or the process restarted: draw from the remembered state. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        val saved = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SNAPSHOT, null)
        // If the playback service is not alive the remembered "playing" would be a lie, so it shows as paused.
        val state = if (PlaybackServiceStatus.isAlive) WidgetSnapshot.decode(saved) else WidgetSnapshot.asRestored(WidgetSnapshot.decode(saved))
        updateAll(app, state)
        lastDrawn = null
    }

    private fun updateAll(context: Context, state: TrackPresentationState): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val shown = EchoWidgetState(state)
        var any = false
        for (kind in WidgetKind.entries) {
            val ids = manager.getAppWidgetIds(ComponentName(context, kind.providerClass))
            if (ids.isEmpty()) continue
            any = true
            val views = build(context, kind, shown)
            manager.updateAppWidget(ids, views)
        }
        return any
    }

    fun build(context: Context, kind: WidgetKind, state: EchoWidgetState): RemoteViews {
        val views = RemoteViews(context.packageName, kind.layout)
        views.setTextViewText(R.id.w_title, state.titleText)
        views.setTextViewText(R.id.w_artist, state.subtitleText)
        views.setViewVisibility(R.id.w_artist, if (state.subtitleText.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE)
        views.setContentDescription(R.id.w_root, state.summaryDescription)

        // The mascot: bright while music plays, dimmed when paused or idle (the same face as the launcher icon).
        views.setInt(R.id.w_mascot, "setImageAlpha", if (state.isPlaying) 255 else 120)
        views.setContentDescription(R.id.w_mascot, null)

        views.setImageViewResource(R.id.w_play, if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
        views.setContentDescription(R.id.w_play, state.playPauseDescription)
        views.setOnClickPendingIntent(R.id.w_play, actionIntent(context, WidgetAction.TOGGLE))

        if (kind.showsControls) {
            views.setContentDescription(R.id.w_prev, state.previousDescription)
            views.setContentDescription(R.id.w_next, state.nextDescription)
            views.setOnClickPendingIntent(R.id.w_prev, actionIntent(context, WidgetAction.PREVIOUS))
            views.setOnClickPendingIntent(R.id.w_next, actionIntent(context, WidgetAction.NEXT))
            views.setInt(R.id.w_prev, "setImageAlpha", if (state.canPrevious) 255 else 90)
            views.setInt(R.id.w_next, "setImageAlpha", if (state.canNext) 255 else 90)
        }
        // Progress: the bar is redrawn on real changes and about every 10 s while playing (the service's existing save
        // tick); the elapsed time is a Chronometer, which the launcher advances by itself, so it costs nothing.
        views.setViewVisibility(R.id.w_progress, if (state.showProgress) android.view.View.VISIBLE else android.view.View.INVISIBLE)
        views.setProgressBar(R.id.w_progress, 100, state.progressPercent, false)
        if (kind.showsTime) {
            views.setChronometer(R.id.w_elapsed, SystemClock.elapsedRealtime() - state.elapsedMs, null, state.isPlaying && state.showProgress)
            views.setViewVisibility(R.id.w_elapsed, if (state.showProgress) android.view.View.VISIBLE else android.view.View.INVISIBLE)
            views.setTextViewText(R.id.w_duration, state.durationText)
        }
        if (kind.showsFavorite) {
            views.setImageViewResource(R.id.w_fav, if (state.isFavorite) R.drawable.ic_widget_heart_filled else R.drawable.ic_widget_heart)
            views.setContentDescription(R.id.w_fav, state.favoriteDescription)
            views.setViewVisibility(R.id.w_fav, if (state.canFavorite) android.view.View.VISIBLE else android.view.View.INVISIBLE)
            views.setOnClickPendingIntent(R.id.w_fav, actionIntent(context, WidgetAction.FAVORITE))
        }
        views.setTextViewText(R.id.w_state, state.stateText)
        views.setTextColor(R.id.w_state, if (state.isPlaying) 0xFFE8FF47.toInt() else 0xFF8C8C8C.toInt())

        // Tapping the body opens ECHO: Now Playing when a track exists, the library otherwise. The activity is
        // singleTask, so this brings the one existing task forward rather than starting a second.
        views.setOnClickPendingIntent(R.id.w_root, openIntent(context, state.opensNowPlaying))
        views.setContentDescription(R.id.w_open, state.openDescription)
        return views
    }

    fun openIntent(context: Context, nowPlaying: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_NOW_PLAYING, nowPlaying)
        return PendingIntent.getActivity(
            context,
            if (nowPlaying) 7101 else 7102,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun actionIntent(context: Context, action: WidgetAction): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).setAction(action.intentAction)
        return PendingIntent.getBroadcast(context, 7200 + action.ordinal, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/** Whether the playback service currently exists in this process. */
object PlaybackServiceStatus {
    @Volatile var isAlive: Boolean = false
}
