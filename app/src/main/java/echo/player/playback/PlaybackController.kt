package echo.player.playback

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import echo.player.domain.model.PlayableTrack
import echo.player.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * What the UI uses to control playback. It owns a MediaController connected to [PlaybackService]; it
 * never creates a player. Must be created and used on the main thread (a ViewModel does that).
 */
class PlaybackController(
    private val app: Application,
    private val scope: CoroutineScope,
) : PlaybackControls {
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)

    /** Ticks while playing; collect it only in the composable that draws the position. */
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val pendingActions = ArrayDeque<(MediaController) -> Unit>()
    private var queueItems: List<QueueItem> = emptyList()
    private var ticker: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) queueItems = readQueue(player)
            publish(player)
            updateTicker(player)
        }
    }

    init {
        connect()
    }

    private fun connect() {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    onConnected(future.get())
                } catch (e: Exception) {
                    Log.e(TAG, "Could not connect to the playback service", e)
                }
            },
            ContextCompat.getMainExecutor(app),
        )
    }

    private fun onConnected(connected: MediaController) {
        controller = connected
        connected.addListener(playerListener)
        queueItems = readQueue(connected)
        publish(connected)
        updateTicker(connected)
        while (pendingActions.isNotEmpty()) pendingActions.removeFirst()(connected)
    }

    /** Runs [action] now, or as soon as the controller has connected. */
    private fun withController(action: (MediaController) -> Unit) {
        val connected = controller
        if (connected != null) action(connected) else pendingActions.addLast(action)
    }

    // region commands

    override fun playQueue(tracks: List<Track>, startIndex: Int) =
        playItems(tracks.map { PlayableTrack.Local(it) }, startIndex)

    override fun playItems(items: List<PlayableTrack>, startIndex: Int) {
        // Items that cannot be played (an online track with no authorized stream) are dropped, and the start index
        // keeps pointing at the item the user chose.
        val chosen = items.getOrNull(startIndex)
        val playable = items.filter { it.toMediaItem() != null }
        if (playable.isEmpty()) return
        val mediaItems = playable.mapNotNull { it.toMediaItem() }
        val index = PlaybackLogic.clampStartIndex(mediaItems.size, playable.indexOf(chosen).takeIf { it >= 0 } ?: 0)
        withController { player ->
            player.setMediaItems(mediaItems, index, 0L)
            player.prepare()
            player.play()
        }
    }

    fun play(track: Track) = playQueue(listOf(track), 0)

    fun pause() = withController { it.pause() }

    fun resume() = withController { player ->
        prepareIfNeeded(player)
        player.play()
    }

    override fun togglePlayPause() = withController { player ->
        if (player.isPlaying) {
            player.pause()
        } else {
            prepareIfNeeded(player)
            player.play()
        }
    }

    override fun next() = withController { player ->
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            prepareIfNeeded(player)
            player.play()
        }
    }

    override fun previous() = withController { player ->
        when (PlaybackLogic.previousAction(player.currentPosition, player.hasPreviousMediaItem())) {
            PreviousAction.RESTART_TRACK -> player.seekTo(0L)
            PreviousAction.GO_TO_PREVIOUS -> {
                player.seekToPreviousMediaItem()
                prepareIfNeeded(player)
                player.play()
            }
        }
    }

    override fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0L)) }

    fun setRepeatMode(mode: EchoRepeatMode) = withController { it.repeatMode = mode.toPlayerMode() }

    override fun cycleRepeatMode() = setRepeatMode(_state.value.repeatMode.next())

    fun setShuffleEnabled(enabled: Boolean) = withController { it.shuffleModeEnabled = enabled }

    override fun toggleShuffle() = setShuffleEnabled(!_state.value.shuffleEnabled)

    /** Jumps to an item of the current queue (the Now Playing queue list). */
    override fun skipToQueueItem(index: Int) = withController { player ->
        if (index in 0 until player.mediaItemCount) {
            player.seekToDefaultPosition(index)
            prepareIfNeeded(player)
            player.play()
        }
    }

    fun stop() = withController {
        it.stop()
        it.seekTo(0L)
    }

    /** Disconnects the UI. The service, and any playback, keep running. */
    fun release() {
        ticker?.cancel()
        controller?.removeListener(playerListener)
        controllerFuture?.let(MediaController::releaseFuture)
        controller = null
        controllerFuture = null
    }

    // endregion

    /** After an error or a restore the player is idle; it must be prepared again before it can play. */
    private fun prepareIfNeeded(player: Player) {
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
    }

    private fun readQueue(player: Player): List<QueueItem> =
        (0 until player.mediaItemCount).map { index ->
            val item = player.getMediaItemAt(index)
            QueueItem(
                trackId = item.mediaId,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
            )
        }

    @OptIn(UnstableApi::class)
    private fun publish(player: Player) {
        val current = player.currentMediaItem
        val duration = player.duration.takeIf { it != C.TIME_UNSET }
            ?: current?.mediaMetadata?.durationMs
            ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L)
        _positionMs.value = position
        _state.value = PlaybackState(
            isConnected = true,
            currentTrackId = current?.mediaId,
            title = current?.mediaMetadata?.title?.toString(),
            artist = current?.mediaMetadata?.artist?.toString(),
            album = current?.mediaMetadata?.albumTitle?.toString(),
            isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady,
            status = when (player.playbackState) {
                Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
                Player.STATE_READY -> PlaybackStatus.READY
                Player.STATE_ENDED -> PlaybackStatus.ENDED
                else -> PlaybackStatus.IDLE
            },
            positionMs = position,
            durationMs = duration,
            repeatMode = EchoRepeatMode.fromPlayerMode(player.repeatMode),
            shuffleEnabled = player.shuffleModeEnabled,
            queue = queueItems,
            currentIndex = if (current == null) -1 else player.currentMediaItemIndex,
            error = player.playerError?.let { PlaybackLogic.mapError(it.errorCode) },
        )
    }

    private fun updateTicker(player: Player) {
        if (player.isPlaying) {
            if (ticker?.isActive == true) return
            ticker = scope.launch {
                while (isActive) {
                    controller?.let { _positionMs.value = it.currentPosition.coerceAtLeast(0L) }
                    delay(POSITION_TICK_MS)
                }
            }
        } else {
            ticker?.cancel()
            ticker = null
        }
    }

    private companion object {
        const val TAG = "EchoPlayback"
        const val POSITION_TICK_MS = 500L
    }
}
