package echo.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import echo.player.design.EchoColors
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography
import echo.player.design.components.EchoIconButton
import echo.player.design.components.EchoProgressBar
import echo.player.playback.PlaybackControls
import echo.player.playback.PlaybackState

/**
 * The persistent strip above the bottom of the library. Tapping it opens Now Playing; the two buttons act
 * in place. A thin lime line along the top shows progress and is the only part that redraws as time passes.
 */
@Composable
fun EchoMiniPlayer(
    state: PlaybackState,
    controls: PlaybackControls,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(EchoColors.Charcoal)
            .drawBehind {
                drawLine(EchoColors.Metal, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            },
    ) {
        MiniProgress(controls = controls, durationMs = state.durationMs)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Open Now Playing", onClick = onOpen)
                .padding(start = EchoSpacing.M, end = EchoSpacing.Xs, top = EchoSpacing.S, bottom = EchoSpacing.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkImage(trackId = state.currentTrackId, modifier = Modifier.size(44.dp), cornerRadius = 8.dp)
            Column(modifier = Modifier.weight(1f).padding(horizontal = EchoSpacing.M)) {
                Text(
                    text = state.title ?: "Nothing playing",
                    style = EchoTypography.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val error = state.error
                Text(
                    text = error?.userMessage() ?: state.artist.orEmpty(),
                    style = EchoTypography.Caption.copy(color = if (error != null) EchoColors.Danger else EchoColors.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            EchoIconButton(
                icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                onClick = controls::togglePlayPause,
                tint = EchoColors.Lime,
            )
            EchoIconButton(
                icon = Icons.Filled.SkipNext,
                contentDescription = "Next track",
                onClick = controls::next,
            )
        }
    }
}

/** Collects the ticking position in its own small scope so the rest of the strip never recomposes for it. */
@Composable
private fun MiniProgress(controls: PlaybackControls, durationMs: Long) {
    val position by controls.positionMs.collectAsState()
    EchoProgressBar(
        progress = { if (durationMs > 0) position.toFloat() / durationMs else 0f },
        thin = true,
        modifier = Modifier.semantics { contentDescription = "Track progress" },
    )
}
