package echo.player.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.player.design.EchoColors
import echo.player.visualizer.VisualizerEngine
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Two analog needle meters (left and right) reading RMS level, with a peak lamp. */
@Composable
fun EchoVuMeters(
    engine: VisualizerEngine,
    tick: () -> Int,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    // Text is measured once; the draw loop below allocates no text layouts.
    val tickLabels = remember(measurer) {
        MeterMajorDb.map { measurer.measure(it.toString(), TextStyle(fontSize = 9.sp, color = EchoColors.TextMuted)) }
    }
    val leftName = remember(measurer) { measurer.measure("L", TextStyle(fontSize = 13.sp, color = EchoColors.TextSecondary)) }
    val rightName = remember(measurer) { measurer.measure("R", TextStyle(fontSize = 13.sp, color = EchoColors.TextSecondary)) }
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        tick()
        val gap = 12.dp.toPx()
        val meterWidth = (size.width - gap) / 2f
        drawNeedleMeter(tickLabels, leftName, Offset(0f, 0f), Size(meterWidth, size.height), engine.levelLeft, engine.peakLeft)
        drawNeedleMeter(tickLabels, rightName, Offset(meterWidth + gap, 0f), Size(meterWidth, size.height), engine.levelRight, engine.peakRight)
    }
}

private val MeterMajorDb = listOf(-40, -30, -20, -10, -3)
private const val METER_FLOOR_DB = -45f
private const val METER_RANGE_DB = 42f
private const val NEEDLE_SWEEP_DEGREES = 96f
private const val NEEDLE_START_DEGREES = 270f - NEEDLE_SWEEP_DEGREES / 2f

private fun DrawScope.drawNeedleMeter(
    tickLabels: List<TextLayoutResult>,
    name: TextLayoutResult,
    origin: Offset,
    box: Size,
    level: Float,
    peak: Float,
) {
    // The gauge keeps a fixed shape and sits centered in whatever space the display gives it, so it
    // looks the same in portrait and landscape.
    val gaugeHeight = min(box.height, box.width * 0.85f)
    val gaugeOrigin = Offset(origin.x, origin.y + (box.height - gaugeHeight) / 2f)
    val gauge = Size(box.width, gaugeHeight)
    val corner = CornerRadius(10.dp.toPx())
    drawRoundRect(color = EchoColors.Charcoal, topLeft = gaugeOrigin, size = gauge, cornerRadius = corner)

    clipRect(gaugeOrigin.x, gaugeOrigin.y, gaugeOrigin.x + gauge.width, gaugeOrigin.y + gauge.height) {
        val margin = 14.dp.toPx()
        // The needle tip reaches 0.93 of the radius above the pivot, so size the radius to fit the face.
        val radius = min(gauge.width * 0.78f, (gauge.height - margin) / 0.93f)
        val pivot = Offset(gaugeOrigin.x + gauge.width / 2f, gaugeOrigin.y + gauge.height - margin * 0.6f)

        // Scale arc, with the last stretch (above -10 dBFS) marked in the warning color.
        val redStart = (-10f - METER_FLOOR_DB) / METER_RANGE_DB
        fun point(fraction: Float, r: Float): Offset {
            val angle = Math.toRadians((NEEDLE_START_DEGREES + fraction * NEEDLE_SWEEP_DEGREES).toDouble())
            return Offset(pivot.x + r * cos(angle).toFloat(), pivot.y + r * sin(angle).toFloat())
        }
        fun arc(from: Float, to: Float, color: Color) {
            val path = Path()
            val steps = 24
            for (i in 0..steps) {
                val p = point(from + (to - from) * i / steps, radius * 0.86f)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
        arc(0f, redStart, EchoColors.LimeDim)
        arc(redStart, 1f, EchoColors.Danger)

        // Ticks and labels.
        for ((index, db) in MeterMajorDb.withIndex()) {
            val fraction = (db - METER_FLOOR_DB) / METER_RANGE_DB
            drawLine(
                color = if (fraction >= redStart) EchoColors.Danger else EchoColors.TextSecondary,
                start = point(fraction, radius * 0.80f),
                end = point(fraction, radius * 0.90f),
                strokeWidth = 2.dp.toPx(),
            )
            val layout = tickLabels[index]
            val at = point(fraction, radius * 0.70f)
            drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
        }
        for (i in 1..20) {
            val fraction = i / 21f
            drawLine(
                color = EchoColors.Metal,
                start = point(fraction, radius * 0.84f),
                end = point(fraction, radius * 0.88f),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // Needle: a soft shadow first, then the needle itself.
        val tip = point(level.coerceIn(0f, 1f), radius * 0.93f)
        drawLine(EchoColors.Black, Offset(pivot.x + 1.5.dp.toPx(), pivot.y), Offset(tip.x + 1.5.dp.toPx(), tip.y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(EchoColors.Lime, pivot, tip, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }

    // Channel name and peak lamp sit outside the clip so they stay crisp.
    drawText(name, topLeft = Offset(gaugeOrigin.x + 10.dp.toPx(), gaugeOrigin.y + gauge.height - name.size.height - 8.dp.toPx()))
    val lamp = Offset(gaugeOrigin.x + gauge.width - 14.dp.toPx(), gaugeOrigin.y + 14.dp.toPx())
    drawCircle(color = if (peak > 0.92f) EchoColors.Danger else EchoColors.Metal, radius = 4.dp.toPx(), center = lamp)
}
