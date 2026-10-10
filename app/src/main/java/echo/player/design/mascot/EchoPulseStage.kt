package echo.player.design.mascot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import echo.player.companion.CompanionBrain
import echo.player.companion.MusicSymbolPool
import echo.player.companion.SymbolGlyph
import echo.player.design.EchoColors
import echo.player.visualizer.VisualizerEngine
import kotlin.math.min
import kotlin.math.sin

/**
 * ECHO PULSE: ECHO standing inside its own music field. The field is made from the real loudness (ripple rings
 * spread out from ECHO, and waves at its feet swell with the level); ECHO dances to the smoothed audio energy and
 * little music symbols drift up out of its head. All values come from [engine] and [brain], which are computed
 * from the playing audio; this only draws them.
 *
 * Cheap on purpose: one canvas, about seventy draw calls, reused paths, no allocation per frame. When the
 * mascot is switched off ([brain] and [pool] are null) only the quiet field is drawn.
 */
@Composable
fun EchoPulseStage(
    engine: VisualizerEngine,
    tick: () -> Int,
    brain: CompanionBrain?,
    pool: MusicSymbolPool?,
    companionTick: () -> Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            .drawWithCache {
                val layout = StageLayout(size.width, size.height, hasMascot = brain != null) // once per size, not per frame
                val wave = Path()
                val ring = Stroke(width = 2.dp.toPx())
                val waveStroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                val noteStroke = 1.4.dp.toPx()
                onDrawBehind {
                    val frame = tick()
                    companionTick()
                    drawField(engine, frame, layout, ring, wave, waveStroke, drawCore = brain == null)
                    if (brain != null && pool != null) drawEcho(brain, pool, layout, noteStroke)
                }
            },
    ) {}
}

private val RING_AGES = intArrayOf(5, 11, 17, 23)

/** The music field: ripple rings of recent loudness around ECHO, and waves that rise and fall with the level. */
/**
 * Where ECHO stands inside the panel: its size follows the panel, its feet rest just above the waves, and the
 * space above its head is kept free for the rising music symbols (also in short landscape panels).
 */
private class StageLayout(width: Float, height: Float, hasMascot: Boolean) {
    val extent = min(width * 0.6f, height * 0.62f)
    val left = (width - extent) / 2f
    val top = height * 0.84f - extent * 0.99f
    val centre = if (hasMascot) Offset(width / 2f, top + extent * 0.5f) else Offset(width / 2f, height * 0.46f)
    val headY = top + extent * 2.6f / 16f
    val climb = min(extent * 0.55f, (headY - height * 0.12f).coerceAtLeast(extent * 0.1f))
}

private fun DrawScope.drawField(
    engine: VisualizerEngine,
    frame: Int,
    layout: StageLayout,
    ring: Stroke,
    wave: Path,
    waveStroke: Stroke,
    drawCore: Boolean,
) {
    val centre = layout.centre
    val reach = min(size.width, size.height) / 2f
    for (k in RING_AGES.indices) {
        val past = engine.envelopeAt(RING_AGES[k])
        val radius = reach * (0.42f + 0.13f * k + 0.1f * past).coerceAtMost(1f)
        drawCircle(EchoColors.Lime, radius, centre, alpha = (0.45f * past / (k + 1)).coerceIn(0f, 0.45f), style = ring)
    }
    if (drawCore) {
        // With ECHO switched off the middle of the field is a plain glowing core that swells with the level, so the
        // rings stay balanced around something. Two circles; no mascot, no extra animation.
        val coreLevel = (engine.levelLeft + engine.levelRight) * 0.5f
        val core = reach * (0.12f + 0.16f * coreLevel)
        drawCircle(EchoColors.LimeFaint, core * 1.55f, centre)
        drawCircle(EchoColors.Lime, core, centre, alpha = 0.55f + 0.4f * coreLevel)
    }
    // Waves at ECHO's feet. Their height is the real level; their drift is a steady counter that stops with the audio.
    val level = (engine.levelLeft + engine.levelRight) * 0.5f
    val baseY = size.height * 0.88f
    val phase = frame * 0.18f
    for (w in 0 until 3) {
        wave.rewind()
        val amplitude = size.height * (0.012f + 0.05f * level) * (1f - 0.28f * w)
        val steps = 36
        for (i in 0..steps) {
            val x = size.width * i / steps
            val y = baseY + w * size.height * 0.032f + amplitude * sin(i * 0.55f + phase * (1f + 0.3f * w) + w * 1.7f)
            if (i == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
        }
        drawPath(wave, EchoColors.Lime.copy(alpha = 0.55f - 0.16f * w), style = waveStroke)
    }
}

private fun DrawScope.drawEcho(brain: CompanionBrain, pool: MusicSymbolPool, layout: StageLayout, noteStroke: Float) {
    val extent = layout.extent
    translate(left = layout.left, top = layout.top) { drawEchoMascot(brain, extent) }

    // Symbols rise from the top of the head.
    val u = extent / 16f
    val headX = layout.left + 8f * u
    for (i in 0 until pool.capacity) {
        if (!pool.isActive(i)) continue
        val x = headX + pool.x(i) * 11f * u
        val y = layout.headY - pool.rise(i) * layout.climb - u
        drawSymbol(pool.glyph(i), Offset(x, y), u * 1.6f, pool.alpha(i), noteStroke)
    }
}

/** ♪ ♫ • and z, drawn from lines and circles so no font is needed. */
private fun DrawScope.drawSymbol(glyph: SymbolGlyph, at: Offset, s: Float, alpha: Float, stroke: Float) {
    if (alpha <= 0.01f) return
    val c: Color = if (glyph == SymbolGlyph.SLEEP) EchoColors.TextSecondary else EchoColors.Lime
    when (glyph) {
        SymbolGlyph.DOT -> drawCircle(c, s * 0.28f, at, alpha = alpha)
        SymbolGlyph.NOTE -> {
            val head = Offset(at.x, at.y + s * 0.55f)
            drawCircle(c, s * 0.3f, head, alpha = alpha)
            drawLine(c, Offset(head.x + s * 0.27f, head.y), Offset(head.x + s * 0.27f, at.y - s * 0.55f), stroke, StrokeCap.Round, alpha = alpha)
            drawLine(c, Offset(head.x + s * 0.27f, at.y - s * 0.55f), Offset(head.x + s * 0.7f, at.y - s * 0.2f), stroke, StrokeCap.Round, alpha = alpha)
        }
        SymbolGlyph.DOUBLE_NOTE -> {
            val a = Offset(at.x - s * 0.35f, at.y + s * 0.55f)
            val b = Offset(at.x + s * 0.4f, at.y + s * 0.4f)
            drawCircle(c, s * 0.27f, a, alpha = alpha)
            drawCircle(c, s * 0.27f, b, alpha = alpha)
            val topA = Offset(a.x + s * 0.24f, at.y - s * 0.5f)
            val topB = Offset(b.x + s * 0.24f, at.y - s * 0.65f)
            drawLine(c, Offset(a.x + s * 0.24f, a.y), topA, stroke, StrokeCap.Round, alpha = alpha)
            drawLine(c, Offset(b.x + s * 0.24f, b.y), topB, stroke, StrokeCap.Round, alpha = alpha)
            drawLine(c, topA, topB, stroke * 1.6f, StrokeCap.Round, alpha = alpha)
        }
        SymbolGlyph.SLEEP -> {
            val w = s * 0.7f
            drawLine(c, Offset(at.x, at.y), Offset(at.x + w, at.y), stroke, StrokeCap.Round, alpha = alpha)
            drawLine(c, Offset(at.x + w, at.y), Offset(at.x, at.y + w), stroke, StrokeCap.Round, alpha = alpha)
            drawLine(c, Offset(at.x, at.y + w), Offset(at.x + w, at.y + w), stroke, StrokeCap.Round, alpha = alpha)
        }
    }
}
