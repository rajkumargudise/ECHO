package echo.player.visualizer

import kotlin.math.exp
import kotlin.math.max

/**
 * Every mode draws from the same real-PCM analysis. [usesSpectrum] / [usesWaveform] tell the engine which
 * expensive parts a mode actually needs, so the FFT is not run for a mode that only draws levels.
 */
enum class VisualizerMode(val label: String, val usesSpectrum: Boolean = false, val usesWaveform: Boolean = false) {
    // The order below is the one and only cycle order: one tap on the display control goes to the next entry
    // and wraps from the last back to the first.

    /** ECHO's signature display: the mascot inside its own music field. Needs only the loudness, never the FFT. */
    ECHO_PULSE("ECHO PULSE"),
    SPECTRUM("SPECTRUM", usesSpectrum = true),
    VU("VU METER"),
    SCOPE("SCOPE", usesWaveform = true),
    CLASSIC_BARS("CLASSIC BARS", usesSpectrum = true),
    MIRROR("MIRROR SPECTRUM", usesSpectrum = true),
    DIGITAL("DIGITAL ANALYZER", usesSpectrum = true),
    NEON_LINE("NEON LINE", usesSpectrum = true),
    WAVEFORM("WAVEFORM"),
    PULSE("RIPPLES"),
    ;

    /** The next display in the cycle; after the last one it wraps to the first. */
    fun next(): VisualizerMode = entries[(ordinal + 1) % entries.size]

    /** The display before this one; before the first it wraps to the last. */
    fun previous(): VisualizerMode = entries[(ordinal - 1 + entries.size) % entries.size]

    val needs: Int get() = (if (usesSpectrum) VisualizerEngine.NEED_SPECTRUM else 0) or (if (usesWaveform) VisualizerEngine.NEED_WAVEFORM else 0)

    companion object {
        fun fromName(name: String?): VisualizerMode = entries.firstOrNull { it.name == name } ?: ECHO_PULSE
    }
}

/**
 * Per-frame visualizer data computed from real PCM. Everything is preallocated; [update] does no allocation,
 * so it can run every frame on the UI thread. The UI draws straight from these arrays.
 *
 * When no audio is flowing (paused, stopped, between tracks) every value decays to zero from what was
 * really shown; nothing is invented. [unavailable] is set if playback is running but the player never
 * delivered any PCM to the tap (for example an unsupported output format).
 */
class VisualizerEngine(
    private val source: SampleSource,
    val bandCount: Int = 32,
    val waveformPoints: Int = 512,
    private val analysisSize: Int = 2048,
    val envelopeSize: Int = 96,
) {
    val bars = FloatArray(bandCount)
    val barPeaks = FloatArray(bandCount)
    val waveform = FloatArray(waveformPoints)

    /**
     * A short history of the real loudness, one value per update, oldest first once read through
     * [envelopeAt]. It is what the scrolling WAVEFORM mode draws.
     */
    private val envelope = FloatArray(envelopeSize)
    private var envelopeHead = 0

    var levelLeft = 0f
        private set
    var levelRight = 0f
        private set
    var peakLeft = 0f
        private set
    var peakRight = 0f
        private set

    /** The loudness of the latest audio, before any smoothing, 0..1. The companion reacts to this. */
    var instantLevel = 0f
        private set

    var isLive = false
        private set
    var unavailable = false
        private set

    /** True when everything has decayed to rest, so the caller can stop redrawing every frame. */
    val isIdle: Boolean
        get() = !isLive && levelLeft < REST && levelRight < REST && peakLeft < REST && peakRight < REST &&
            bars.all { it < REST } && barPeaks.all { it < REST } && envelope.all { it < REST }

    private val analyzer = SpectrumAnalyzer(fftSize = analysisSize, bandCount = bandCount)
    private val left = FloatArray(analysisSize)
    private val right = FloatArray(analysisSize)
    private val mono = FloatArray(analysisSize)
    private val targetBars = FloatArray(bandCount)
    private val targetWave = FloatArray(waveformPoints)
    private var playingSinceNanos = 0L

    /** The envelope value [age] updates ago, 0 being the newest. */
    fun envelopeAt(age: Int): Float = envelope[(envelopeHead - 1 - age + envelopeSize * 2) % envelopeSize]

    fun update(nowNanos: Long, dtSeconds: Float, isPlaying: Boolean, needs: Int = NEED_ALL) {
        val dt = dtSeconds.coerceIn(0.001f, 0.25f)
        val live = source.isLive(nowNanos)
        isLive = live

        var targetLeft = 0f
        var targetRight = 0f
        var targetPeakLeft = 0f
        var targetPeakRight = 0f
        var frameLoudness = 0f

        targetBars.fill(0f)
        targetWave.fill(0f)
        if (live) {
            source.copyLatest(0, left)
            source.copyLatest(1, right)
            if (needs and (NEED_SPECTRUM or NEED_WAVEFORM) != 0) {
                for (i in mono.indices) mono[i] = (left[i] + right[i]) * 0.5f
                if (needs and NEED_SPECTRUM != 0) analyzer.analyze(mono, source.sampleRate, targetBars)
                if (needs and NEED_WAVEFORM != 0) Waveform.fill(mono, targetWave)
            }
            // Meters read the last ~46 ms, about one scope sweep.
            targetLeft = LevelMath.toMeter(LevelMath.rms(left, METER_WINDOW))
            targetRight = LevelMath.toMeter(LevelMath.rms(right, METER_WINDOW))
            targetPeakLeft = LevelMath.toMeter(LevelMath.peak(left, METER_WINDOW))
            targetPeakRight = LevelMath.toMeter(LevelMath.peak(right, METER_WINDOW))
            // The scrolling waveform and the companion use the loudness of just the newest ~23 ms.
            frameLoudness = LevelMath.toMeter((LevelMath.rms(left, ENVELOPE_WINDOW) + LevelMath.rms(right, ENVELOPE_WINDOW)) * 0.5f)
        }
        instantLevel = (targetLeft + targetRight) * 0.5f

        Ballistics.step(bars, targetBars, dt, attackTau = 0.04f, releaseTau = 0.28f)
        Ballistics.step(waveform, targetWave, dt, attackTau = 0.02f, releaseTau = 0.12f)
        levelLeft = Ballistics.step(levelLeft, targetLeft, dt, attackTau = 0.12f, releaseTau = 0.30f)
        levelRight = Ballistics.step(levelRight, targetRight, dt, attackTau = 0.12f, releaseTau = 0.30f)

        // Loudness history: a little attack/release smoothing against the previous entry, so it scrolls smoothly.
        val previous = envelope[(envelopeHead - 1 + envelopeSize) % envelopeSize]
        envelope[envelopeHead] = Ballistics.step(previous, frameLoudness, dt, attackTau = 0.03f, releaseTau = 0.12f)
        envelopeHead = (envelopeHead + 1) % envelopeSize

        // Peak hold: jump up instantly, then fall slowly.
        val fall = exp(-dt / PEAK_FALL_TAU)
        for (i in barPeaks.indices) barPeaks[i] = max(bars[i], barPeaks[i] * fall)
        peakLeft = max(targetPeakLeft, peakLeft * fall)
        peakRight = max(targetPeakRight, peakRight * fall)

        if (isPlaying) {
            if (playingSinceNanos == 0L) playingSinceNanos = nowNanos
            unavailable = !source.hasReceivedAudio && nowNanos - playingSinceNanos > UNAVAILABLE_AFTER_NANOS
        } else {
            playingSinceNanos = 0L
            unavailable = false
        }
    }

    companion object {
        const val NEED_SPECTRUM = 1
        const val NEED_WAVEFORM = 2
        const val NEED_ALL = NEED_SPECTRUM or NEED_WAVEFORM

        private const val REST = 0.004f
        private const val METER_WINDOW = 2048
        private const val ENVELOPE_WINDOW = 1024
        private const val PEAK_FALL_TAU = 0.6f
        private const val UNAVAILABLE_AFTER_NANOS = 2_500_000_000L
    }
}
