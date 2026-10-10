package echo.player.ui.nowplaying

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.sp
import echo.player.design.EchoShapes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import echo.player.companion.AnimationRate
import echo.player.companion.CompanionBrain
import echo.player.companion.MascotAnimationPolicy
import echo.player.companion.MusicSymbolPool
import echo.player.design.EchoColors
import echo.player.design.EchoMotion
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography
import echo.player.design.components.EchoClassicBars
import echo.player.design.components.EchoDigitalAnalyzer
import echo.player.design.components.EchoDisplayPanel
import echo.player.design.components.EchoMirrorSpectrum
import echo.player.design.components.EchoNeonLine
import echo.player.design.components.EchoOscilloscope
import echo.player.design.components.EchoPulse
import echo.player.design.components.EchoSpectrum
import echo.player.design.components.EchoVuMeters
import echo.player.design.components.EchoWaveformHistory
import echo.player.design.mascot.EchoPulseStage
import echo.player.visualizer.VisualizerEngine
import echo.player.visualizer.VisualizerMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class VisualizerStatus { LIVE, STANDBY, UNAVAILABLE }

/**
 * Drives the visualizer and the companion for as long as the screen is visible and the app is started.
 * [tick] and [companionTick] change on every update but are only read inside canvas draw blocks, so the screen
 * never recomposes for them; [status] changes rarely and is the only value that causes recomposition.
 */
class VisualizerFrameState(val engine: VisualizerEngine) {
    val tick = mutableIntStateOf(0)
    val companionTick = mutableIntStateOf(0)
    val companion = CompanionBrain()
    val symbols = MusicSymbolPool()
    var status by mutableStateOf(VisualizerStatus.STANDBY)
}

/**
 * One loop for everything animated on this screen, paced by a timer instead of the display's frame callback.
 * Waiting on every display frame (90 Hz or more on many phones) kept the UI thread busy even when nothing
 * had changed; at a fixed ~30 updates a second the picture looks the same and costs a fraction of the CPU.
 */
@Composable
fun rememberVisualizerFrames(
    engine: VisualizerEngine,
    isPlaying: Boolean,
    mode: VisualizerMode,
    hasTrack: Boolean,
    trackId: String?,
    companionEnabled: Boolean,
    mascotPlaying: Boolean,
): VisualizerFrameState {
    val frames = remember(engine) { VisualizerFrameState(engine) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val playing by rememberUpdatedState(isPlaying)
    val currentMode by rememberUpdatedState(mode)
    val track by rememberUpdatedState(hasTrack)
    val currentTrackId by rememberUpdatedState(trackId)
    val companionOn by rememberUpdatedState(companionEnabled)
    // ECHO follows the user's intent to play, not the player's momentary isPlaying, so a track change is not a pause.
    val wantsToPlay by rememberUpdatedState(mascotPlaying)

    LaunchedEffect(engine, lifecycle) {
        // Stops completely when the app is not on screen, and restarts when it comes back.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = 0L
            while (isActive) {
                val now = System.nanoTime()
                val dt = if (last == 0L) UPDATE_SECONDS else (now - last) / 1_000_000_000f
                last = now

                if (engine.isIdle && !playing) {
                    // The audio has decayed to rest. ECHO may still be finishing its slump or a fading symbol;
                    // once that is done (or ECHO is off) nothing animates at all and this only polls for playback.
                    if (frames.status != VisualizerStatus.STANDBY) frames.status = VisualizerStatus.STANDBY
                    val rate = MascotAnimationPolicy.rate(companionOn, wantsToPlay, frames.companion.isSettled, frames.symbols.isIdle)
                    if (rate != AnimationRate.STOPPED) {
                        // RESTING: finishing a pause. ACTIVE: the user wants music but none is flowing yet (a track is loading).
                        frames.companion.update(dt, track, wantsToPlay, currentTrackId, 0f)
                        frames.symbols.update(dt, frames.companion.state, frames.companion.energy)
                        frames.companionTick.intValue++
                        delay(if (rate == AnimationRate.ACTIVE) UPDATE_MS else COMPANION_REST_MS)
                    } else {
                        if (!companionOn) frames.symbols.clear()
                        delay(IDLE_POLL_MS)
                    }
                    continue
                }

                engine.update(now, dt, playing, currentMode.needs)
                frames.tick.intValue++
                if (companionOn) {
                    frames.companion.update(dt, track, wantsToPlay, currentTrackId, engine.instantLevel)
                    frames.symbols.update(dt, frames.companion.state, frames.companion.energy)
                    frames.companionTick.intValue++
                } else if (!frames.symbols.isIdle) {
                    frames.symbols.clear()
                }
                val status = when {
                    engine.unavailable -> VisualizerStatus.UNAVAILABLE
                    engine.isLive -> VisualizerStatus.LIVE
                    else -> VisualizerStatus.STANDBY
                }
                if (status != frames.status) frames.status = status

                val spentMs = (System.nanoTime() - now) / 1_000_000L
                delay((UPDATE_MS - spentMs).coerceAtLeast(MIN_DELAY_MS))
            }
        }
    }
    return frames
}

private const val UPDATE_MS = 33L // about 30 updates per second
private const val UPDATE_SECONDS = 0.033f
private const val MIN_DELAY_MS = 4L
private const val COMPANION_REST_MS = 100L
private const val IDLE_POLL_MS = 250L

/** The inset display with the selected visualizer mode drawn from real audio. */
@Composable
fun VisualizerDisplay(
    frames: VisualizerFrameState,
    mode: VisualizerMode,
    companionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val status = frames.status
    val statusText = when (status) {
        VisualizerStatus.LIVE -> "LIVE"
        VisualizerStatus.STANDBY -> "STANDBY"
        VisualizerStatus.UNAVAILABLE -> "NO SIGNAL"
    }
    EchoDisplayPanel(
        modifier = modifier.semantics {
            contentDescription = "Audio visualizer, ${mode.label.lowercase()}"
            stateDescription = statusText.lowercase()
        },
        caption = mode.label,
        readout = statusText,
    ) {
        Crossfade(
            targetState = mode,
            animationSpec = tween(EchoMotion.Medium),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = EchoSpacing.M, end = EchoSpacing.M, top = 30.dp, bottom = EchoSpacing.M),
            label = "visualizerMode",
        ) { current ->
            val tick: () -> Int = { frames.tick.intValue }
            val engine = frames.engine
            val fill = Modifier.fillMaxSize()
            when (current) {
                VisualizerMode.ECHO_PULSE -> EchoPulseStage(
                    engine = engine,
                    tick = tick,
                    brain = if (companionEnabled) frames.companion else null,
                    pool = if (companionEnabled) frames.symbols else null,
                    companionTick = { frames.companionTick.intValue },
                )
                VisualizerMode.SPECTRUM -> EchoSpectrum(engine, tick, fill)
                VisualizerMode.CLASSIC_BARS -> EchoClassicBars(engine, tick, fill)
                VisualizerMode.MIRROR -> EchoMirrorSpectrum(engine, tick, fill)
                VisualizerMode.DIGITAL -> EchoDigitalAnalyzer(engine, tick, fill)
                VisualizerMode.NEON_LINE -> EchoNeonLine(engine, tick, fill)
                VisualizerMode.VU -> EchoVuMeters(engine, tick, fill)
                VisualizerMode.SCOPE -> EchoOscilloscope(engine, tick, fill)
                VisualizerMode.WAVEFORM -> EchoWaveformHistory(engine, tick, fill)
                VisualizerMode.PULSE -> EchoPulse(engine, tick, fill)
            }
        }
        ModeToast(mode)
        if (status == VisualizerStatus.UNAVAILABLE) {
            Text(
                text = "VISUALIZER UNAVAILABLE\nPlayback is not affected.",
                style = EchoTypography.Label.copy(color = EchoColors.Warning, textAlign = TextAlign.Center),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/**
 * A short label that names the visualization right after it changes, then fades. It is skipped for the mode the
 * screen opens with, never blocks touches, and is announced to screen readers as a polite live region.
 */
@Composable
private fun BoxScope.ModeToast(mode: VisualizerMode) {
    val alpha = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(mode) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        alpha.snapTo(1f)
        delay(TOAST_HOLD_MS)
        alpha.animateTo(0f, tween(EchoMotion.Medium))
    }
    if (alpha.value > 0.01f) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { this.alpha = alpha.value }
                .background(EchoColors.Black.copy(alpha = 0.82f), EchoShapes.Medium)
                .border(1.dp, EchoColors.Lime.copy(alpha = 0.55f), EchoShapes.Medium)
                .padding(horizontal = EchoSpacing.L, vertical = EchoSpacing.M)
                .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Visualization: ${mode.label}" },
        ) {
            Text(text = mode.label, style = EchoTypography.Heading.copy(color = EchoColors.Lime, letterSpacing = 2.sp))
        }
    }
}

private const val TOAST_HOLD_MS = 700L
