package echo.player.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import echo.player.design.EchoColors
import echo.player.visualizer.VisualizerEngine
import kotlin.math.min

/*
 * Visualizer modes beyond the original spectrum/VU/scope trio. Every mode draws from the engine's arrays,
 * which are computed from the playing audio; nothing here generates values of its own. They are written to be
 * cheap: static artwork lives in a layer that never redraws, the moving layer reads [tick] only while drawing,
 * and each frame issues tens of draw calls, not hundreds.
 */

private val LitTop = Color(0xFFF4FFA6)

/** Stacked draw layers: [background] never redraws; [foreground] redraws on every tick. */
@Composable
internal fun TwoLayer(
    modifier: Modifier,
    background: (DrawScope.() -> Unit)?,
    foreground: Modifier,
) {
    Box(modifier = modifier.clearAndSetSemantics { }) {
        if (background != null) Canvas(Modifier.fillMaxSize()) { background() }
        Box(foreground.fillMaxSize())
    }
}

/** One lit column made of equal segments, drawn as a single dashed line instead of one rect per segment. */
private fun DrawScope.segmentColumn(centerX: Float, bottom: Float, height: Float, width: Float, color: Color, effect: PathEffect) {
    if (height <= 0f) return
    drawLine(color, Offset(centerX, bottom), Offset(centerX, bottom - height), strokeWidth = width, cap = StrokeCap.Butt, pathEffect = effect)
}

/**
 * Segmented LED spectrum with peak-hold markers: the original look, drawn with about a hundred calls a frame.
 */
@Composable
fun EchoSpectrum(
    engine: VisualizerEngine,
    tick: () -> Int,
    modifier: Modifier = Modifier,
    segments: Int = 18,
) {
    val bands = engine.bandCount
    val layout = { size: Size, density: Float ->
        val gap = 3 * density
        val segmentGap = 2 * density
        val barWidth = (size.width - gap * (bands - 1)) / bands
        val segmentHeight = (size.height - segmentGap * (segments - 1)) / segments
        floatArrayOf(gap, segmentGap, barWidth, segmentHeight)
    }
    TwoLayer(
        modifier = modifier,
        background = {
            val (gap, segmentGap, barWidth, segmentHeight) = layout(size, density).toList()
            val effect = PathEffect.dashPathEffect(floatArrayOf(segmentHeight, segmentGap), 0f)
            val full = segments * (segmentHeight + segmentGap) - segmentGap
            for (band in 0 until bands) segmentColumn(band * (barWidth + gap) + barWidth / 2, size.height, full, barWidth, EchoColors.LimeOff, effect)
        },
        foreground = Modifier.drawWithCache {
            val (gap, segmentGap, barWidth, segmentHeight) = layout(size, density).toList()
            val pitch = segmentHeight + segmentGap
            val effect = PathEffect.dashPathEffect(floatArrayOf(segmentHeight, segmentGap), 0f)
            val peakSize = Size(barWidth, segmentHeight)
            onDrawBehind {
                tick()
                for (band in 0 until bands) {
                    val x = band * (barWidth + gap)
                    val lit = (engine.bars[band] * segments + 0.2f).toInt().coerceIn(0, segments)
                    segmentColumn(x + barWidth / 2, size.height, lit * pitch - segmentGap, barWidth, EchoColors.Lime, effect)
                    if (lit > segments - 3) {
                        val from = (segments - 3) * pitch
                        segmentColumn(x + barWidth / 2, size.height - from, (lit - (segments - 3)) * pitch - segmentGap, barWidth, LitTop, effect)
                    }
                    val peakValue = engine.barPeaks[band]
                    if (peakValue > 0.03f) {
                        val peakSegment = (peakValue * segments).toInt().coerceIn(0, segments - 1)
                        drawRect(EchoColors.White, Offset(x, size.height - (peakSegment + 1) * pitch + segmentGap), peakSize)
                    }
                }
            }
        },
    )
}

/** Solid thin bars with peak caps and a faint reflection, in the manner of a classic digital equalizer. */
@Composable
fun EchoClassicBars(engine: VisualizerEngine, tick: () -> Int, modifier: Modifier = Modifier) {
    val bands = engine.bandCount
    TwoLayer(
        modifier = modifier,
        background = {
            drawLine(EchoColors.LimeFaint, Offset(0f, size.height * BASELINE), Offset(size.width, size.height * BASELINE), 1.dp.toPx())
        },
        foreground = Modifier.drawWithCache {
            val gap = 4.dp.toPx()
            val barWidth = (size.width - gap * (bands - 1)) / bands
            val baseline = size.height * BASELINE
            val cap = 3.dp.toPx()
            val reflection = size.height - baseline
            val capSize = Size(barWidth, cap)
            onDrawBehind {
                tick()
                for (band in 0 until bands) {
                    val x = band * (barWidth + gap)
                    val h = engine.bars[band] * (baseline - cap)
                    if (h > 0.5f) {
                        drawRect(EchoColors.Lime, Offset(x, baseline - h), Size(barWidth, h))
                        drawRect(EchoColors.Lime, Offset(x, baseline + 2.dp.toPx()), Size(barWidth, min(h * 0.35f, reflection - 2.dp.toPx())), alpha = 0.16f)
                    }
                    val peak = engine.barPeaks[band]
                    if (peak > 0.03f) drawRect(EchoColors.White, Offset(x, baseline - peak * (baseline - cap) - cap - 1.dp.toPx()), capSize)
                }
            }
        },
    )
}

private const val BASELINE = 0.82f

/** Bass at the centre, treble at both edges, every bar growing up and down from the middle line. */
@Composable
fun EchoMirrorSpectrum(engine: VisualizerEngine, tick: () -> Int, modifier: Modifier = Modifier) {
    val bands = engine.bandCount
    TwoLayer(
        modifier = modifier,
        background = {
            drawLine(EchoColors.LimeFaint, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.dp.toPx())
        },
        foreground = Modifier.drawWithCache {
            val gap = 3.dp.toPx()
            val columns = bands * 2
            val barWidth = (size.width - gap * (columns - 1)) / columns
            val mid = size.height / 2f
            val half = mid - 2.dp.toPx()
            onDrawBehind {
                tick()
                for (band in 0 until bands) {
                    val h = engine.bars[band] * half
                    if (h < 0.5f) continue
                    val xRight = size.width / 2f + band * (barWidth + gap) + gap / 2f
                    val xLeft = size.width / 2f - (band + 1) * (barWidth + gap) + gap / 2f
                    drawRect(EchoColors.Lime, Offset(xRight, mid - h), Size(barWidth, h * 2f))
                    drawRect(EchoColors.Lime, Offset(xLeft, mid - h), Size(barWidth, h * 2f))
                    val peak = engine.barPeaks[band] * half
                    if (engine.barPeaks[band] > 0.03f) {
                        drawRect(EchoColors.White, Offset(xRight, mid - peak - 2.dp.toPx()), Size(barWidth, 2.dp.toPx()))
                        drawRect(EchoColors.White, Offset(xLeft, mid - peak - 2.dp.toPx()), Size(barWidth, 2.dp.toPx()))
                    }
                }
            }
        },
    )
}

/** Dense, small-segment analyzer: twice the columns of the bands, as on a rack-mounted analyzer. */
@Composable
fun EchoDigitalAnalyzer(engine: VisualizerEngine, tick: () -> Int, modifier: Modifier = Modifier, rows: Int = 28) {
    val bands = engine.bandCount
    val columns = bands * 2
    TwoLayer(
        modifier = modifier,
        background = {
            val gap = 1.5.dp.toPx()
            val rowGap = 1.5.dp.toPx()
            val width = (size.width - gap * (columns - 1)) / columns
            val rowHeight = (size.height - rowGap * (rows - 1)) / rows
            val effect = PathEffect.dashPathEffect(floatArrayOf(rowHeight, rowGap), 0f)
            val full = rows * (rowHeight + rowGap) - rowGap
            for (c in 0 until columns) segmentColumn(c * (width + gap) + width / 2, size.height, full, width, EchoColors.LimeOff, effect)
        },
        foreground = Modifier.drawWithCache {
            val gap = 1.5.dp.toPx()
            val rowGap = 1.5.dp.toPx()
            val width = (size.width - gap * (columns - 1)) / columns
            val rowHeight = (size.height - rowGap * (rows - 1)) / rows
            val pitch = rowHeight + rowGap
            val effect = PathEffect.dashPathEffect(floatArrayOf(rowHeight, rowGap), 0f)
            val peakSize = Size(width, rowHeight)
            onDrawBehind {
                tick()
                for (c in 0 until columns) {
                    // Each band feeds two neighbouring columns, blended toward the next band for a denser trace.
                    val position = c / 2f
                    val lower = position.toInt().coerceAtMost(bands - 1)
                    val upper = (lower + 1).coerceAtMost(bands - 1)
                    val mix = position - lower
                    val value = engine.bars[lower] * (1f - mix) + engine.bars[upper] * mix
                    val peakValue = engine.barPeaks[lower] * (1f - mix) + engine.barPeaks[upper] * mix
                    val x = c * (width + gap)
                    val lit = (value * rows + 0.2f).toInt().coerceIn(0, rows)
                    segmentColumn(x + width / 2, size.height, lit * pitch - rowGap, width, EchoColors.Lime, effect)
                    if (peakValue > 0.03f) {
                        val peakRow = (peakValue * rows).toInt().coerceIn(0, rows - 1)
                        drawRect(EchoColors.White, Offset(x, size.height - (peakRow + 1) * pitch + rowGap), peakSize)
                    }
                }
            }
        },
    )
}

/** A thin glowing line through the band levels: the frequency response of what is playing. */
@Composable
fun EchoNeonLine(engine: VisualizerEngine, tick: () -> Int, modifier: Modifier = Modifier) {
    val bands = engine.bandCount
    TwoLayer(
        modifier = modifier,
        background = {
            for (i in 1 until 4) drawLine(EchoColors.LimeOff, Offset(0f, size.height * i / 4f), Offset(size.width, size.height * i / 4f), 1.dp.toPx())
        },
        foreground = Modifier.drawWithCache {
            val path = Path()
            val inset = 4.dp.toPx()
            val usable = size.height - inset * 2f
            val glow = Stroke(width = 5.dp.toPx()) // butt caps and mitre joins: far cheaper to stroke than round ones
            val line = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind {
                tick()
                path.rewind()
                val step = size.width / (bands - 1)
                var previousX = 0f
                var previousY = size.height - inset - engine.bars[0] * usable
                path.moveTo(previousX, previousY)
                for (i in 1 until bands) {
                    val x = i * step
                    val y = size.height - inset - engine.bars[i] * usable
                    // Quadratic through midpoints keeps the line smooth without overshooting the data.
                    path.quadraticTo(previousX, previousY, (previousX + x) / 2f, (previousY + y) / 2f)
                    previousX = x
                    previousY = y
                }
                path.lineTo(previousX, previousY)
                drawPath(path, EchoColors.LimeFaint, style = glow)
                drawPath(path, EchoColors.Lime, style = line)
            }
        },
    )
}

/** A scrolling record of the real loudness over the last few seconds, newest at the right. */
@Composable
fun EchoWaveformHistory(engine: VisualizerEngine, tick: () -> Int, modifier: Modifier = Modifier) {
    val n = engine.envelopeSize
    TwoLayer(
        modifier = modifier,
        background = {
            drawLine(EchoColors.LimeFaint, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.dp.toPx())
        },
        foreground = Modifier.drawWithCache {
            val path = Path()
            val mid = size.height / 2f
            val half = mid - 3.dp.toPx()
            val edge = Stroke(width = 2.dp.toPx())
            onDrawBehind {
                tick()
                path.rewind()
                val step = size.width / (n - 1)
                // Every second history entry is plenty for a path this wide, and halves the stroking work.
                for (i in 0 until n step 2) {
                    val amp = engine.envelopeAt(n - 1 - i) * half
                    if (i == 0) path.moveTo(0f, mid - amp) else path.lineTo(i * step, mid - amp)
                }
                for (i in n - 1 downTo 0 step 2) path.lineTo(i * step, mid + engine.envelopeAt(n - 1 - i) * half)
                path.close()
                drawPath(path, EchoColors.LimeFaint, style = Fill)
                drawPath(path, EchoColors.Lime, style = edge)
            }
        },
    )
}

/** A centre pulse: its size follows the real level, and rings are the level from moments ago spreading outward. */
@Composable
fun EchoPulse(engine: VisualizerEngine, tick: () -> Int, modifier: Modifier = Modifier) {
    TwoLayer(
        modifier = modifier,
        background = null,
        foreground = Modifier.drawWithCache {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val reach = min(size.width, size.height) / 2f - 4.dp.toPx()
            val ring = Stroke(width = 2.dp.toPx())
            onDrawBehind {
                tick()
                val level = (engine.levelLeft + engine.levelRight) * 0.5f
                // Rings: the loudness from `age` updates ago sets how far out a ring has travelled.
                for (k in 4 downTo 1) {
                    val past = engine.envelopeAt(k * RING_AGE)
                    val radius = reach * (0.28f + 0.14f * k + 0.1f * past).coerceAtMost(1f)
                    drawCircle(EchoColors.Lime, radius, centre, alpha = (0.5f * past / k).coerceIn(0f, 0.5f), style = ring)
                }
                val core = reach * (0.14f + 0.2f * level)
                drawCircle(EchoColors.LimeFaint, core * 1.5f, centre)
                drawCircle(EchoColors.Lime, core, centre)
            }
        },
    )
}

private const val RING_AGE = 6

/**
 * Oscilloscope trace of the real waveform (512 points of the playing audio) over a faint graticule that is
 * drawn once. The soft glow is a thinned copy of the trace, so only the sharp line carries every point.
 */
@Composable
fun EchoOscilloscope(engine: VisualizerEngine, tick: () -> Int, modifier: Modifier = Modifier) {
    TwoLayer(
        modifier = modifier,
        background = {
            for (i in 1 until 8) {
                val x = size.width * i / 8f
                drawLine(EchoColors.LimeOff, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
            for (i in 1 until 4) {
                val y = size.height * i / 4f
                drawLine(if (i == 2) EchoColors.LimeFaint else EchoColors.LimeOff, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
        },
        foreground = Modifier.drawWithCache {
            val line = Path()
            val glowPath = Path()
            val lineStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val glowStroke = Stroke(width = 5.dp.toPx())
            val points = engine.waveform.size
            val midY = size.height / 2f
            val minY = 2.dp.toPx()
            val maxY = size.height - 2.dp.toPx()
            val gain = 1.8f
            onDrawBehind {
                tick()
                line.rewind()
                glowPath.rewind()
                for (i in 0 until points) {
                    val x = size.width * i / (points - 1)
                    val y = (midY - engine.waveform[i] * gain * midY).coerceIn(minY, maxY)
                    if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
                    if (i % GLOW_STRIDE == 0) {
                        if (i == 0) glowPath.moveTo(x, y) else glowPath.lineTo(x, y)
                    }
                }
                drawPath(glowPath, EchoColors.LimeFaint, style = glowStroke)
                drawPath(line, EchoColors.Lime, style = lineStroke)
            }
        },
    )
}

private const val GLOW_STRIDE = 4
