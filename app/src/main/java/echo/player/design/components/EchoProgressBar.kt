package echo.player.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import echo.player.design.EchoColors

/**
 * Playback position bar. [progress] is a lambda so the position is only read while drawing: the 2 Hz
 * position ticks redraw this bar and nothing else. Pass [onSeek] to make it draggable and tappable;
 * leave it null for a thin read-only line (mini player).
 */
@Composable
fun EchoProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    onSeek: ((Float) -> Unit)? = null,
    valueText: String = "",
    thin: Boolean = false,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val seekable = onSeek != null
    val height = when {
        thin -> 3.dp
        else -> 36.dp
    }

    val interactive = if (seekable) {
        Modifier
            .pointerInput(onSeek) {
                detectTapGestures { offset -> onSeek!!.invoke((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(onSeek) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragFraction?.let { onSeek!!.invoke(it) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                ) { change, _ ->
                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(interactive)
            .semantics(mergeDescendants = true) {
                contentDescription = "Playback position"
                if (valueText.isNotEmpty()) stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(progress().coerceIn(0f, 1f), 0f..1f)
                if (seekable) {
                    setProgress { target ->
                        onSeek!!.invoke(target.coerceIn(0f, 1f))
                        true
                    }
                }
            },
    ) {
        val fraction = (dragFraction ?: progress()).coerceIn(0f, 1f)
        val trackHeight = if (thin) size.height else 6.dp.toPx()
        val top = (size.height - trackHeight) / 2f
        val radius = CornerRadius(trackHeight / 2f)

        // Inset groove, then the lit portion.
        drawRoundRect(color = EchoColors.DisplayDeep, topLeft = Offset(0f, top), size = Size(size.width, trackHeight), cornerRadius = radius)
        if (fraction > 0f) {
            drawRoundRect(
                color = EchoColors.Lime,
                topLeft = Offset(0f, top),
                size = Size((size.width * fraction).coerceAtLeast(trackHeight), trackHeight),
                cornerRadius = radius,
            )
        }
        if (!thin) {
            val thumbRadius = if (dragFraction != null) 11.dp.toPx() else 8.dp.toPx()
            val x = (size.width * fraction).coerceIn(thumbRadius, size.width - thumbRadius)
            drawCircle(color = EchoColors.DisplayDeep, radius = thumbRadius + 2.dp.toPx(), center = Offset(x, size.height / 2f + 1.dp.toPx()))
            drawCircle(color = EchoColors.White, radius = thumbRadius, center = Offset(x, size.height / 2f))
            drawCircle(color = EchoColors.Lime, radius = thumbRadius * 0.38f, center = Offset(x, size.height / 2f))
        }
    }
}
