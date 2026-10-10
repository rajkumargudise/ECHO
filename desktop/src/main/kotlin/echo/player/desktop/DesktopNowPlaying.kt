package echo.player.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import echo.player.design.EchoColors
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography
import echo.player.design.components.EchoIconButton
import echo.player.design.components.EchoProgressBar
import echo.player.design.components.EchoToggle
import echo.player.design.components.EchoTransportButton
import echo.player.playback.EchoRepeatMode
import echo.player.playback.NowPlayingFormat
import echo.player.playback.toNowPlayingModel
import echo.player.ui.ArtworkImage
import echo.player.ui.nowplaying.SleepTimerControl
import echo.player.ui.nowplaying.VisualizerDisplay
import echo.player.ui.nowplaying.rememberVisualizerFrames
import echo.player.ui.userMessage
import echo.player.visualizer.AudioSampleTap
import echo.player.visualizer.VisualizerEngine

@Composable
fun DesktopNowPlaying(app: DesktopApp) {
    val state by app.player.state.collectAsState()
    val engine = remember { VisualizerEngine(AudioSampleTap) }
    val model = remember(state, app.favorites) { state.toNowPlayingModel(0L, app.favorites) }
    val mode = app.visualizerMode
    val frames = rememberVisualizerFrames(engine, state.isPlaying, mode, model.trackId != null, model.trackId, app.showCompanion, state.wantsToPlay)
    val track = remember(model.trackId, app.tracks) { app.tracks.firstOrNull { it.id == model.trackId } }

    Column(modifier = Modifier.fillMaxSize().background(EchoColors.Black).padding(horizontal = EchoSpacing.L)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = EchoSpacing.Xs), verticalAlignment = Alignment.CenterVertically) {
            EchoIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", app::back)
            Text("NOW PLAYING", style = EchoTypography.Heading, modifier = Modifier.weight(1f).padding(start = EchoSpacing.S))
            SleepTimerControl(app.sleepTimer)
        }
        Column(modifier = Modifier.weight(1f).fillMaxWidth().widthIn(max = 1100.dp).align(Alignment.CenterHorizontally)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                EchoIconButton(Icons.Filled.ChevronLeft, "Previous display", { app.updateVisualizerMode(mode.previous()) })
                Text(mode.label, style = EchoTypography.Label.copy(color = EchoColors.Lime), modifier = Modifier.widthIn(min = 160.dp))
                EchoIconButton(Icons.Filled.ChevronRight, "Next display", { app.updateVisualizerMode(mode.next()) })
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = 160.dp).clickable { app.updateVisualizerMode(mode.next()) }) {
                VisualizerDisplay(frames = frames, mode = mode, companionEnabled = app.showCompanion, modifier = Modifier.fillMaxSize())
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = EchoSpacing.M),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EchoSpacing.M),
            ) {
                ArtworkImage(trackId = model.trackId, modifier = Modifier.size(72.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.title, style = EchoTypography.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(model.artist, model.album).filter { it.isNotBlank() }.joinToString(" · "),
                        style = EchoTypography.Body.copy(color = EchoColors.TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    model.error?.let { Text(it.desktopMessage(track), style = EchoTypography.Caption.copy(color = EchoColors.Danger)) }
                }
                EchoToggle(
                    icon = if (model.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    label = "Favorite",
                    stateText = if (model.isFavorite) "In your favorites" else "Not in your favorites",
                    active = model.isFavorite,
                    onClick = { track?.let(app::toggleFavorite) },
                )
                EchoToggle(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    label = "Playlist",
                    stateText = "Add this song to a playlist",
                    active = false,
                    onClick = { app.playlistTarget = track },
                )
            }
            ProgressRow(app, state.durationMs, model.canSeek)
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = EchoSpacing.L),
                horizontalArrangement = Arrangement.spacedBy(EchoSpacing.L, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EchoToggle(
                    icon = Icons.Filled.Shuffle,
                    label = "Shuffle",
                    stateText = if (model.shuffleEnabled) "On" else "Off",
                    active = model.shuffleEnabled,
                    onClick = app.player::toggleShuffle,
                )
                EchoTransportButton(Icons.Filled.SkipPrevious, "Previous track", app.player::previous, size = 56.dp)
                EchoTransportButton(
                    icon = if (model.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (model.isPlaying) "Pause" else "Play",
                    onClick = app.player::togglePlayPause,
                    primary = true,
                    size = 72.dp,
                )
                EchoTransportButton(Icons.Filled.SkipNext, "Next track", app.player::next, size = 56.dp)
                EchoToggle(
                    icon = if (model.repeatMode == EchoRepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    label = "Repeat",
                    stateText = when (model.repeatMode) {
                        EchoRepeatMode.OFF -> "Off"
                        EchoRepeatMode.ALL -> "Repeat all"
                        EchoRepeatMode.ONE -> "Repeat one track"
                    },
                    active = model.repeatMode != EchoRepeatMode.OFF,
                    onClick = app.player::cycleRepeatMode,
                )
            }
        }
    }
}

/** The ticking position lives in its own small composable so only it redraws as time passes. */
@Composable
private fun ProgressRow(app: DesktopApp, durationMs: Long, canSeek: Boolean) {
    val position by app.player.positionMs.collectAsState()
    val elapsed = NowPlayingFormat.formatTime(position)
    val total = if (durationMs > 0) NowPlayingFormat.formatTime(durationMs) else "--:--"
    Column(modifier = Modifier.fillMaxWidth().padding(top = EchoSpacing.S)) {
        EchoProgressBar(
            progress = { if (durationMs > 0) (position.toFloat() / durationMs).coerceIn(0f, 1f) else 0f },
            onSeek = if (canSeek) { fraction -> app.player.seekTo((fraction * durationMs).toLong()) } else null,
            valueText = "$elapsed of $total",
            modifier = Modifier.semantics { contentDescription = "Track progress" },
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(elapsed, style = EchoTypography.Readout.copy(color = EchoColors.Lime), modifier = Modifier.weight(1f))
            Text(total, style = EchoTypography.Readout.copy(color = EchoColors.TextMuted))
        }
    }
}

private fun echo.player.playback.PlaybackError.desktopMessage(track: echo.player.domain.model.Track?): String {
    val ext = track?.mimeType
    return if (this == echo.player.playback.PlaybackError.UNSUPPORTED_OR_CORRUPT && ext != null && ext == "audio/mp4") {
        "This format (M4A/AAC) is not supported on desktop yet."
    } else {
        userMessage()
    }
}
