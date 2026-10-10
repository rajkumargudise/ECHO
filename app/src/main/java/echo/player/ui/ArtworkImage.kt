package echo.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import echo.player.design.EchoColors
import echo.player.platform.android.ArtworkLoader

/**
 * Cover art embedded in the audio file, or a quiet record placeholder when there is none.
 * Decorative: the title next to it already says what is playing.
 */
@Composable
fun ArtworkImage(
    trackId: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
) {
    val context = LocalContext.current
    val artwork by produceState<ImageBitmap?>(
        initialValue = trackId?.let { ArtworkLoader.cached(it)?.asImageBitmap() },
        key1 = trackId,
    ) {
        value = if (trackId == null) null else ArtworkLoader.load(context, trackId)?.asImageBitmap()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.verticalGradient(listOf(EchoColors.Steel, EchoColors.Graphite))),
    ) {
        val image = artwork
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.36f
                for (ring in 1..3) {
                    drawCircle(
                        color = EchoColors.Metal,
                        radius = radius * (0.45f + ring * 0.18f),
                        center = center,
                        style = Stroke(width = size.minDimension * 0.012f),
                    )
                }
                drawCircle(color = EchoColors.Lime, radius = radius * 0.16f, center = center)
                drawCircle(color = EchoColors.Black, radius = radius * 0.05f, center = center)
            }
        }
    }
}
