package echo.player.playback

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import echo.player.MainActivity
import echo.player.data.local.EchoDatabase
import echo.player.data.local.FavoriteSignal
import echo.player.data.local.FavoriteStore
import echo.player.domain.model.RemoteMediaId
import echo.player.data.local.MediaStoreDataSource
import echo.player.domain.MusicRoots
import echo.player.domain.model.Track
import echo.player.platform.android.MusicRootManager
import echo.player.domain.model.EchoLanguage
import echo.player.online.HistoryRepository
import echo.player.online.PlayOrigin
import echo.player.online.PlayRecorder
import echo.player.online.RecentPlay
import echo.player.platform.android.SleepTimers
import echo.player.widget.EchoWidgetUpdater
import echo.player.widget.PlaybackServiceStatus
import echo.player.widget.TrackPresentationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The one owner of the ExoPlayer and the MediaSession. The Activity only talks to it through a
 * MediaController, so playback survives the Activity and there can never be two players.
 * Audio focus, becoming-noisy handling, the media notification and foreground-service promotion are all
 * provided by Media3 from the settings below.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var applyingShuffleOrder = false
    private lateinit var store: PlaybackStore
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private var lastRecordedMediaId: String? = null
    private val history by lazy { HistoryRepository(EchoDatabase.getInstance(this).echoDao()) }

    /**
     * Local, on-device history of what was played (local files and online tracks alike). It runs here, in the one
     * service, so it also records plays started from the notification, a widget or the headset. Only the stable
     * source + track id and display metadata are stored, never a stream address.
     */
    private val historyListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val player = exoPlayer ?: return
            val item = player.currentMediaItem ?: return
            val (sourceId, trackId, origin) = PlayRecorder.identify(item.mediaId)
            if (isPlaying) {
                if (!PlayRecorder.shouldRecord(lastRecordedMediaId, item.mediaId)) return
                lastRecordedMediaId = item.mediaId
                val entry = RecentPlay(
                    sourceId = sourceId,
                    sourceTrackId = trackId,
                    title = item.mediaMetadata.title?.toString().orEmpty(),
                    artist = item.mediaMetadata.artist?.toString().orEmpty(),
                    album = item.mediaMetadata.albumTitle?.toString().orEmpty(),
                    artworkUrl = if (origin == PlayOrigin.ONLINE) item.mediaMetadata.artworkUri?.toString() else null,
                    lastPlayedAt = System.currentTimeMillis(),
                    positionMs = 0L,
                    origin = origin,
                    language = EchoLanguage.fromName(item.mediaMetadata.extras?.getString(EXTRA_LANGUAGE)),
                )
                serviceScope.launch(Dispatchers.IO) { runCatching { history.recordPlay(entry) } }
            } else if (item.mediaId == lastRecordedMediaId) {
                val position = player.currentPosition
                serviceScope.launch(Dispatchers.IO) { runCatching { history.updatePosition(sourceId, trackId, position) } }
            }
        }
    }

    private val progressSaver = object : Runnable {
        override fun run() {
            mediaSession?.player?.let {
                saveProgress(it)
                // The widget bar moves on this same tick (no extra timer); nothing is drawn if no widget exists.
                EchoWidgetUpdater.push(applicationContext, widgetState(it))
            }
            handler.postDelayed(this, PROGRESS_SAVE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = PlaybackStore(this)

        // The only change to the audio path: a pass-through processor that lets the visualizer see the PCM.
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(VisualizerAudioProcessor()))
                    .build()
        }

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        exoPlayer = player
        player.addListener(persistenceListener)
        player.addListener(shuffleListener)
        player.addListener(widgetListener)
        player.addListener(historyListener)
        PlaybackServiceStatus.isAlive = true
        FavoriteSignal.add(favoritesChanged)
        reloadFavorites()

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            // Makes the media notification (and the lock-screen card) open ECHO when its body is tapped.
            .setSessionActivity(openAppIntent())
            .build()

        // The sleep timer lives for the process; when it runs out, playback pauses (queue and position are kept).
        SleepTimers.timer.onExpired = ::onSleepTimerExpired

        restoreLastSession(player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        val keepPlaying = player != null && player.playWhenReady &&
            player.mediaItemCount > 0 && player.playbackState != Player.STATE_ENDED
        if (!keepPlaying) stopSelf()
    }

    /** Opens ECHO: brings the existing task forward if there is one (MainActivity is singleTask), else starts it. */
    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        OPEN_APP_REQUEST,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun onSleepTimerExpired() {
        val session = mediaSession ?: return
        session.player.pause()
        saveProgress(session.player)
    }

    /** Tells the home-screen widgets about meaningful changes only; there is no polling and no timer. */
    private val widgetListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                )
            ) {
                EchoWidgetUpdater.push(applicationContext, widgetState(player))
            }
        }
    }

    /** Favorite ids, kept here so the widget heart is right without a database read on every player event. */
    @Volatile private var favoriteIds: Set<String> = emptySet()
    private val favoritesChanged: () -> Unit = { reloadFavorites() }

    private fun reloadFavorites() {
        serviceScope.launch {
            val ids = withContext(Dispatchers.IO) { runCatching { FavoriteStore.ids(this@PlaybackService) }.getOrDefault(emptySet()) }
            favoriteIds = ids
            exoPlayer?.let { EchoWidgetUpdater.push(applicationContext, widgetState(it)) }
        }
    }

    private fun widgetState(player: Player): TrackPresentationState {
        val item = player.currentMediaItem
        if (item == null) return TrackPresentationState.NOTHING
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: item.mediaMetadata.durationMs ?: 0L
        return TrackPresentationState(
            hasTrack = true,
            title = item.mediaMetadata.title?.toString().orEmpty(),
            artist = item.mediaMetadata.artist?.toString().orEmpty(),
            isPlaying = player.playWhenReady &&
                (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING),
            hasPrevious = player.hasPreviousMediaItem(),
            hasNext = player.hasNextMediaItem(),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            isFavorite = item.mediaId in favoriteIds,
            canFavorite = !RemoteMediaId.isRemote(item.mediaId),
        )
    }

    override fun onDestroy() {
        PlaybackServiceStatus.isAlive = false
        FavoriteSignal.remove(favoritesChanged)
        mediaSession?.player?.let { EchoWidgetUpdater.push(applicationContext, widgetState(it).copy(isPlaying = false)) }
        SleepTimers.timer.onExpired = null
        SleepTimers.timer.cancel()
        handler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        mediaSession?.let { session ->
            saveProgress(session.player)
            session.player.release()
            session.release()
        }
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }

    private fun restoreLastSession(player: ExoPlayer) {
        if (!hasAudioPermission()) return
        val saved = store.load() ?: return
        serviceScope.launch {
            val library = try {
                // Only tracks inside the user's chosen music folders may come back after a restart.
                val roots = withContext(Dispatchers.IO) { MusicRootManager(this@PlaybackService).activeRoots() }
                MediaStoreDataSource(this@PlaybackService).loadTracks()
                    .filter { MusicRoots.accepts(roots, it.volumeId, it.relativePath) }
                    .associateBy(Track::id)
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the library to restore playback", e)
                return@launch
            }
            // The user may already have started something while the library was loading.
            if (player.mediaItemCount > 0) return@launch
            val plan = PlaybackLogic.planRestore(saved, library.keys) ?: return@launch

            player.setMediaItems(plan.ids.mapNotNull { library[it]?.toMediaItem() }, plan.index, plan.positionMs)
            player.repeatMode = saved.repeatMode.toPlayerMode()
            player.shuffleModeEnabled = saved.shuffleEnabled
            // Prepared but not playing: the last track shows up paused and the user presses Play.
            player.playWhenReady = false
            player.prepare()
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun saveProgress(player: Player) {
        if (player.mediaItemCount == 0) return
        store.saveProgress(
            index = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
            repeatMode = EchoRepeatMode.fromPlayerMode(player.repeatMode),
            shuffleEnabled = player.shuffleModeEnabled,
        )
    }

    /**
     * Media3 picks a random shuffle order with the current track anywhere in it, so right after tapping a
     * song the "next" button could already be at the end of the order. Re-deal the order so the current
     * track comes first and every other track follows once, in random order. This only changes playback
     * order; the queue and any stored playlist are untouched.
     */
    private fun dealShuffleOrderFromCurrent() {
        val player = exoPlayer ?: return
        val count = player.mediaItemCount
        if (count == 0 || !player.shuffleModeEnabled || applyingShuffleOrder) return
        val current = player.currentMediaItemIndex
        val order = listOf(current) + (0 until count).filter { it != current }.shuffled()
        // setShuffleOrder reports its own playlist-changed timeline event, delivered after this call returns.
        // That one event is ours; without skipping it the re-deal would trigger itself forever.
        applyingShuffleOrder = true
        player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), System.nanoTime()))
    }

    private val shuffleListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
            if (applyingShuffleOrder) {
                applyingShuffleOrder = false
            } else {
                dealShuffleOrderFromCurrent()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled) dealShuffleOrderFromCurrent()
        }
    }

    private val persistenceListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) && player.mediaItemCount > 0) {
                store.saveQueue((0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId })
            }
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                )
            ) {
                saveProgress(player)
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                handler.removeCallbacks(progressSaver)
                if (player.isPlaying) handler.postDelayed(progressSaver, PROGRESS_SAVE_INTERVAL_MS)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // Technical detail stays in the log; the UI shows a short readable message.
            Log.w(TAG, "Playback error ${error.errorCodeName} (${error.errorCode})", error)
        }
    }

    private class SessionCallback : MediaSession.Callback {
        /** Controllers send items without a playable URI; rebuild it from the request metadata. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
                if (uri != null) item.buildUpon().setUri(uri).build() else item
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

    private companion object {
        const val OPEN_APP_REQUEST = 7001
        const val TAG = "EchoPlayback"
        const val PROGRESS_SAVE_INTERVAL_MS = 10_000L
    }
}
