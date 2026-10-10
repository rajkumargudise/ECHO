package echo.player.ui.nowplaying

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.player.design.EchoColors
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography
import echo.player.design.components.EchoDial
import echo.player.design.components.EchoIconButton
import echo.player.design.components.EchoProgressBar
import echo.player.design.components.EchoToggle
import echo.player.design.mascot.EchoMascotView
import echo.player.design.components.EchoTransportButton
import echo.player.playback.EchoRepeatMode
import echo.player.playback.NowPlayingFormat
import echo.player.playback.NowPlayingModel
import echo.player.playback.PlaybackControls
import echo.player.playback.PlaybackState
import echo.player.playback.SleepTimer
import echo.player.playback.toNowPlayingModel
import echo.player.ui.ArtworkImage
import echo.player.ui.onHorizontalSwipe
import echo.player.ui.userMessage
import echo.player.visualizer.VisualizerEngine
import echo.player.visualizer.VisualizerMode

/**
 * ECHO's main screen: a hi-fi deck. The display (audio visualizer) leads, then the track, then the
 * transport keys. Portrait stacks them like a pocket player; landscape lays them out like a deck.
 * Everything reads the one shared playback state and sends commands through [PlaybackControls].
 */
@Composable
fun NowPlayingScreen(
    controls: PlaybackControls,
    favoriteIds: Set<String>,
    engine: VisualizerEngine,
    mode: VisualizerMode,
    showQueue: Boolean,
    companionEnabled: Boolean,
    sleepTimer: SleepTimer,
    onModeChange: (VisualizerMode) -> Unit,
    onToggleQueue: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAddToPlaylist: () -> Unit,
    onClose: () -> Unit,
) {
    val state by controls.state.collectAsState()
    // Position is deliberately 0 here: the ticking time lives in NowPlayingProgress so this screen does not
    // recompose twice a second.
    val model = remember(state, favoriteIds) { state.toNowPlayingModel(0L, favoriteIds) }
    val frames = rememberVisualizerFrames(engine, state.isPlaying, mode, model.trackId != null, model.trackId, companionEnabled, state.wantsToPlay)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EchoColors.Black)
            .pointerInput(Unit) { detectTapGestures { } } // the library underneath must not receive touches
            .padding(horizontal = EchoSpacing.L),
    ) {
        NowPlayingHeader(model = model, showQueue = showQueue, onToggleQueue = onToggleQueue, onClose = onClose)
        if (landscape) {
            LandscapeBody(model, state, frames, controls, mode, showQueue, companionEnabled, sleepTimer, onModeChange, onToggleFavorite, onAddToPlaylist, Modifier.weight(1f))
        } else {
            PortraitBody(model, state, frames, controls, mode, showQueue, companionEnabled, sleepTimer, onModeChange, onToggleFavorite, onAddToPlaylist, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PortraitBody(
    model: NowPlayingModel,
    state: PlaybackState,
    frames: VisualizerFrameState,
    controls: PlaybackControls,
    mode: VisualizerMode,
    showQueue: Boolean,
    companionEnabled: Boolean,
    sleepTimer: SleepTimer,
    onModeChange: (VisualizerMode) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EchoSpacing.M)) {
        if (showQueue) {
            QueueList(state = state, onSelect = controls::skipToQueueItem, modifier = Modifier.weight(1f))
        } else {
            VisualizerDisplay(
                frames = frames, mode = mode, companionEnabled = companionEnabled,
                modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = 160.dp)
                    .onHorizontalSwipe(onLeft = { onModeChange(mode.next()) }, onRight = { onModeChange(mode.previous()) }),
            )
            TrackInfo(
                model = model, artworkSize = 72.dp, frames = frames, companionEnabled = companionEnabled && mode != VisualizerMode.ECHO_PULSE,
                onSwipeNext = controls::next, onSwipePrevious = controls::previous,
            )
        }
        NowPlayingProgress(controls = controls, durationMs = state.durationMs, canSeek = model.canSeek)
        TransportRow(model = model, controls = controls, keySize = 64.dp, playSize = 76.dp, modifier = Modifier.fillMaxWidth())
        SecondaryRow(model, controls, mode, sleepTimer, onModeChange, onToggleFavorite, onAddToPlaylist)
        Spacer(Modifier.size(EchoSpacing.S))
    }
}

@Composable
private fun LandscapeBody(
    model: NowPlayingModel,
    state: PlaybackState,
    frames: VisualizerFrameState,
    controls: PlaybackControls,
    mode: VisualizerMode,
    showQueue: Boolean,
    companionEnabled: Boolean,
    sleepTimer: SleepTimer,
    onModeChange: (VisualizerMode) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EchoSpacing.S)) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EchoSpacing.L)) {
            Column(modifier = Modifier.width(210.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(EchoSpacing.S)) {
                ArtworkImage(
                    trackId = model.trackId,
                    modifier = Modifier.weight(1f, fill = false).aspectRatio(1f, matchHeightConstraintsFirst = true).fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.onHorizontalSwipe(onLeft = controls::previous, onRight = controls::next),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrackText(model, modifier = Modifier.weight(1f))
                    if (companionEnabled && mode != VisualizerMode.ECHO_PULSE) CompanionSlot(frames, size = 52.dp)
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (showQueue) {
                    QueueList(state = state, onSelect = controls::skipToQueueItem, modifier = Modifier.fillMaxSize())
                } else {
                    VisualizerDisplay(
                        frames = frames, mode = mode, companionEnabled = companionEnabled,
                        modifier = Modifier.fillMaxSize()
                            .onHorizontalSwipe(onLeft = { onModeChange(mode.next()) }, onRight = { onModeChange(mode.previous()) }),
                    )
                }
            }
        }
        NowPlayingProgress(controls = controls, durationMs = state.durationMs, canSeek = model.canSeek)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShuffleToggle(model, controls)
            RepeatToggle(model, controls)
            SleepTimerControl(sleepTimer)
            TransportRow(model = model, controls = controls, keySize = 52.dp, playSize = 60.dp, spacing = 14.dp)
            FavoriteToggle(model, onToggleFavorite)
            AddToPlaylistToggle(model, onAddToPlaylist)
            ModeDial(mode, onModeChange, dialSize = 40.dp)
        }
        Spacer(Modifier.size(EchoSpacing.Xs))
    }
}

@Composable
private fun NowPlayingHeader(
    model: NowPlayingModel,
    showQueue: Boolean,
    onToggleQueue: () -> Unit,
    onClose: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        EchoIconButton(Icons.Filled.KeyboardArrowDown, "Close Now Playing", onClose)
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ECHO", style = EchoTypography.Label.copy(color = EchoColors.Lime, letterSpacing = 5.sp))
            Text(
                text = if (showQueue) "QUEUE ${model.queuePositionText}".trim() else "NOW PLAYING",
                style = EchoTypography.Readout.copy(color = EchoColors.TextMuted, fontSize = 11.sp),
            )
        }
        EchoToggle(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            label = "Queue",
            stateText = if (showQueue) "Showing the queue" else "Queue hidden",
            active = showQueue,
            onClick = onToggleQueue,
        )
    }
}

@Composable
private fun TrackInfo(
    model: NowPlayingModel,
    artworkSize: androidx.compose.ui.unit.Dp,
    frames: VisualizerFrameState,
    companionEnabled: Boolean,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
) {
    // Swipe the song name: right for the next song, left for the previous one.
    Row(
        modifier = Modifier.fillMaxWidth().onHorizontalSwipe(onLeft = onSwipePrevious, onRight = onSwipeNext),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EchoSpacing.M),
    ) {
        ArtworkImage(trackId = model.trackId, modifier = Modifier.size(artworkSize))
        TrackText(model, modifier = Modifier.weight(1f))
        if (companionEnabled) CompanionSlot(frames, size = 64.dp)
    }
}

/** ECHO the companion, beside the track text so it never covers the display or any control. */
@Composable
private fun CompanionSlot(frames: VisualizerFrameState, size: androidx.compose.ui.unit.Dp) {
    EchoMascotView(pose = frames.companion, tick = { frames.companionTick.intValue }, size = size)
}

@Composable
private fun TrackText(model: NowPlayingModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = model.title,
            style = EchoTypography.Title.copy(fontSize = 20.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        val error = model.error
        if (error != null) {
            Text(error.userMessage(), style = EchoTypography.Body.copy(color = EchoColors.Danger))
        } else {
            if (model.artist.isNotEmpty()) {
                Text(model.artist, style = EchoTypography.Body.copy(color = EchoColors.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (model.album.isNotEmpty()) {
                Text(model.album, style = EchoTypography.Caption.copy(color = EchoColors.TextMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The only part of the screen that follows the ticking position. */
@Composable
private fun NowPlayingProgress(controls: PlaybackControls, durationMs: Long, canSeek: Boolean) {
    val position by controls.positionMs.collectAsState()
    val shown = position.coerceIn(0L, durationMs.coerceAtLeast(0L).takeIf { it > 0 } ?: Long.MAX_VALUE)
    val elapsed = NowPlayingFormat.formatTime(shown)
    val total = NowPlayingFormat.formatTime(durationMs)
    Column {
        EchoProgressBar(
            progress = { if (durationMs > 0) (position.toFloat() / durationMs).coerceIn(0f, 1f) else 0f },
            onSeek = if (canSeek) { fraction -> controls.seekTo((fraction * durationMs).toLong()) } else null,
            valueText = "$elapsed of $total",
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(elapsed, style = EchoTypography.Readout.copy(color = EchoColors.Lime))
            Spacer(Modifier.weight(1f))
            Text(total, style = EchoTypography.Readout)
        }
    }
}

@Composable
private fun TransportRow(
    model: NowPlayingModel,
    controls: PlaybackControls,
    keySize: androidx.compose.ui.unit.Dp,
    playSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = 22.dp,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EchoTransportButton(Icons.Filled.SkipPrevious, "Previous track", controls::previous, size = keySize)
        EchoTransportButton(
            icon = if (model.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (model.isPlaying) "Pause" else "Play",
            onClick = controls::togglePlayPause,
            primary = true,
            size = playSize,
        )
        EchoTransportButton(Icons.Filled.SkipNext, "Next track", controls::next, size = keySize)
    }
}

@Composable
private fun SecondaryRow(
    model: NowPlayingModel,
    controls: PlaybackControls,
    mode: VisualizerMode,
    sleepTimer: SleepTimer,
    onModeChange: (VisualizerMode) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShuffleToggle(model, controls)
        RepeatToggle(model, controls)
        SleepTimerControl(sleepTimer)
        FavoriteToggle(model, onToggleFavorite)
        AddToPlaylistToggle(model, onAddToPlaylist)
        ModeDial(mode, onModeChange)
    }
}

@Composable
private fun ShuffleToggle(model: NowPlayingModel, controls: PlaybackControls) {
    EchoToggle(
        icon = Icons.Filled.Shuffle,
        label = "Shuffle",
        stateText = if (model.shuffleEnabled) "On" else "Off",
        active = model.shuffleEnabled,
        onClick = controls::toggleShuffle,
    )
}

@Composable
private fun RepeatToggle(model: NowPlayingModel, controls: PlaybackControls) {
    EchoToggle(
        icon = if (model.repeatMode == EchoRepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
        label = "Repeat",
        stateText = when (model.repeatMode) {
            EchoRepeatMode.OFF -> "Off"
            EchoRepeatMode.ALL -> "Repeat all"
            EchoRepeatMode.ONE -> "Repeat one track"
        },
        active = model.repeatMode != EchoRepeatMode.OFF,
        onClick = controls::cycleRepeatMode,
    )
}

@Composable
private fun FavoriteToggle(model: NowPlayingModel, onToggleFavorite: (String) -> Unit) {
    EchoToggle(
        icon = if (model.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        label = "Favorite",
        stateText = if (model.isFavorite) "In your favorites" else "Not in your favorites",
        active = model.isFavorite,
        onClick = { model.trackId?.let(onToggleFavorite) },
    )
}

/** The "+" key: add the playing song to a playlist. */
@Composable
private fun AddToPlaylistToggle(model: NowPlayingModel, onAddToPlaylist: () -> Unit) {
    EchoToggle(
        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
        label = "Playlist",
        stateText = "Add this song to a playlist",
        active = false,
        onClick = { if (model.trackId != null) onAddToPlaylist() },
    )
}

/** One tap goes straight to the next visualization (see [VisualizerMode.next]); there is no list to choose from. */
@Composable
private fun ModeDial(mode: VisualizerMode, onModeChange: (VisualizerMode) -> Unit, dialSize: androidx.compose.ui.unit.Dp = 48.dp) {
    val modes = VisualizerMode.entries
    EchoDial(
        label = "Display",
        valueText = mode.label,
        positions = modes.size,
        selected = modes.indexOf(mode),
        onSelect = { onModeChange(mode.next()) },
        size = dialSize,
        spokenLabel = "Visualization",
        hint = "Double tap to switch to the next visualization.",
    )
}

/** The current queue. The playing item is lime and marked; tapping any item jumps to it. */
@Composable
private fun QueueList(state: PlaybackState, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        listState.scrollToItem((state.currentIndex - 2).coerceAtLeast(0))
    }
    LazyColumn(state = listState, modifier = modifier.fillMaxWidth()) {
        itemsIndexed(state.queue, key = { index, item -> "$index-${item.trackId}" }) { index, item ->
            val current = index == state.currentIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(if (current) EchoColors.LimeFaint else EchoColors.Black)
                    .drawBehind {
                        val stroke = 1.dp.toPx()
                        drawLine(EchoColors.Separator, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
                        if (current) drawRect(EchoColors.Lime, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
                    }
                    .clickable(role = Role.Button, onClickLabel = "Play this track") { onSelect(index) }
                    .padding(horizontal = EchoSpacing.M),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}",
                    style = EchoTypography.Readout.copy(color = if (current) EchoColors.Lime else EchoColors.TextMuted),
                    modifier = Modifier.width(40.dp),
                )
                Column(modifier = Modifier.weight(1f).padding(vertical = EchoSpacing.S)) {
                    Text(
                        text = item.title,
                        style = EchoTypography.Body.copy(color = if (current) EchoColors.Lime else EchoColors.TextPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (current) "Now playing · ${item.artist}" else item.artist,
                        style = EchoTypography.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
