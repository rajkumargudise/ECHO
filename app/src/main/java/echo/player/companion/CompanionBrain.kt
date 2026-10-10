package echo.player.companion

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/** What the companion is doing right now. Decorative only: it never feeds back into playback. */
enum class CompanionState {
    /** No track at all: asleep, display off. */
    SLEEPING,

    /** A track is loaded but playback is paused: sad and sleepy. */
    PAUSED,

    /** Playback just resumed: short wake-up. */
    WAKING,

    /** Music is playing: gentle dance. */
    DANCING,

    /** Music is playing and loud: stronger dance. */
    ENERGETIC,

    /** The track just changed: brief celebration. */
    CELEBRATING,
}

object CompanionLogic {
    /** The resting state implied by playback alone (no timers). */
    fun baseState(hasTrack: Boolean, isPlaying: Boolean): CompanionState = when {
        !hasTrack -> CompanionState.SLEEPING
        !isPlaying -> CompanionState.PAUSED
        else -> CompanionState.DANCING
    }

    /**
     * Turns the visualizer's meter level (0..1, where music typically sits between 0.4 and 0.9) into a
     * 0..1 "how hard is this playing" value, so ordinary music spans the whole range of the dance.
     */
    fun normalizeEnergy(meterLevel: Float): Float = ((meterLevel - ENERGY_FLOOR) / (ENERGY_CEILING - ENERGY_FLOOR)).coerceIn(0f, 1f)

    const val ENERGY_FLOOR = 0.35f
    const val ENERGY_CEILING = 0.90f
}

/**
 * Presentation state for the companion. It reads one number from the existing audio analysis (the live meter
 * level), never touches audio itself, smooths everything so it cannot jitter, and exposes plain fields for the
 * drawing code. [update] allocates nothing.
 */
class CompanionBrain : MascotPose {
    override var state = CompanionState.SLEEPING
        private set

    /** Vertical bob, roughly -1..1. */
    override var bob = 0f
        private set

    /** Head tilt, -1..1 (left to right). */
    override var tilt = 0f
        private set

    /** How far the arms are raised, 0..1. */
    override var arms = 0f
        private set

    /** 0 = eyes shut, 1 = wide open. */
    override var eyeOpen = 0f
        private set

    /** Display brightness, 0..1. */
    override var glow = 0f
        private set

    /** 0 = standing tall, 1 = slumped. */
    override var slump = 1f
        private set

    /** Smoothed, normalized audio energy, 0..1. */
    var energy = 0f
        private set

    private var fast = 0f
    private var slow = 0f
    private var kick = 0f
    private var phase = 0f
    private var wakeLeft = 0f
    private var celebrateLeft = 0f
    private var lastBase = CompanionState.SLEEPING
    private var lastTrackId: String? = null
    private var energetic = false
    private var stateTime = 0f
    private var lastState = CompanionState.SLEEPING

    /**
     * True once a resting pose (asleep or paused) has fully arrived and stopped changing, so the caller can stop
     * animating altogether and leave the last drawn frame on screen.
     */
    val isSettled: Boolean
        get() = (state == CompanionState.SLEEPING || state == CompanionState.PAUSED) &&
            wakeLeft <= 0f && stateTime >= SETTLE_SECONDS && abs(bob) < SETTLE && abs(tilt) < SETTLE && arms < SETTLE

    /**
     * @param meterLevel the live visualizer level in 0..1 (0 when nothing is playing)
     * @param trackId the current track, used only to notice a change
     */
    fun update(dt: Float, hasTrack: Boolean, isPlaying: Boolean, trackId: String?, meterLevel: Float) {
        val step = dt.coerceIn(0.001f, 0.25f)
        val base = CompanionLogic.baseState(hasTrack, isPlaying)

        // Timed moments.
        if (base == CompanionState.DANCING && lastBase != CompanionState.DANCING) {
            wakeLeft = WAKE_SECONDS
            celebrateLeft = 0f
        }
        if (base == CompanionState.DANCING && lastBase == CompanionState.DANCING &&
            trackId != lastTrackId && lastTrackId != null && trackId != null && wakeLeft <= 0f
        ) {
            celebrateLeft = CELEBRATE_SECONDS
        }
        lastBase = base
        lastTrackId = trackId
        wakeLeft = max(0f, wakeLeft - step)
        celebrateLeft = max(0f, celebrateLeft - step)

        // Energy: smoothed level, plus a fast-minus-slow term that catches beats and loud entries.
        val raw = if (base == CompanionState.DANCING) CompanionLogic.normalizeEnergy(meterLevel) else 0f
        fast = approach(fast, raw, step, if (raw > fast) 0.04f else 0.25f)
        slow = approach(slow, raw, step, 0.6f)
        energy = approach(energy, raw, step, if (raw > energy) 0.15f else 0.5f)
        kick = max(kick * exp(-step / KICK_DECAY), (fast - slow).coerceAtLeast(0f) * KICK_GAIN).coerceAtMost(1f)

        // Loud with hysteresis, so it does not flicker around the threshold.
        energetic = if (energetic) energy > LOUD_EXIT else energy > LOUD_ENTER

        state = when {
            base != CompanionState.DANCING -> base
            wakeLeft > 0f -> CompanionState.WAKING
            celebrateLeft > 0f -> CompanionState.CELEBRATING
            energetic -> CompanionState.ENERGETIC
            else -> CompanionState.DANCING
        }

        if (state != lastState) {
            lastState = state
            stateTime = 0f
        } else {
            stateTime += step
        }

        // Targets for the pose, then eased toward so every change is smooth.
        var targetEye = 1f
        var targetGlow = 0.6f + 0.4f * energy
        var targetSlump = 0f
        var danceAmount = 0f
        var targetArms = 0f
        when (state) {
            CompanionState.SLEEPING -> {
                targetEye = 0f; targetGlow = 0.04f; targetSlump = 1f
            }
            CompanionState.PAUSED -> {
                targetEye = 0.3f; targetGlow = 0.22f; targetSlump = 0.85f
            }
            CompanionState.WAKING -> {
                targetEye = 1f; targetGlow = 0.9f; targetSlump = 0.2f
                danceAmount = 0.2f
            }
            CompanionState.DANCING -> {
                danceAmount = 0.3f + 0.6f * energy
                targetArms = 0.15f + 0.35f * energy
            }
            CompanionState.ENERGETIC -> {
                danceAmount = (0.75f + 0.25f * energy).coerceAtMost(1f)
                targetArms = 0.6f + 0.4f * energy
                targetGlow = 1f
            }
            CompanionState.CELEBRATING -> {
                danceAmount = 1f; targetArms = 1f; targetGlow = 1f
            }
        }

        val tempo = 1.4f + 1.1f * energy // oscillations per second; the amplitude is what follows the music
        phase += step * tempo * TWO_PI
        if (phase > TWO_PI * 100f) phase -= TWO_PI * 100f // a whole number of turns of both phase and phase / 2

        val targetBob = if (danceAmount > 0f) (sin(phase) * 0.6f * danceAmount + kick * 0.5f).coerceIn(-1f, 1f) else 0f
        val targetTilt = if (danceAmount > 0f) sin(phase * 0.5f + 1.3f) * 0.7f * danceAmount else 0f

        eyeOpen = approach(eyeOpen, targetEye, step, 0.12f)
        glow = approach(glow, targetGlow, step, 0.2f)
        slump = approach(slump, targetSlump, step, 0.25f)
        arms = approach(arms, targetArms, step, 0.12f)
        bob = approach(bob, targetBob, step, 0.05f)
        tilt = approach(tilt, targetTilt, step, 0.1f)
    }

    private fun approach(current: Float, target: Float, dt: Float, tau: Float): Float =
        target + (current - target) * exp(-dt / tau)

    private companion object {
        val TWO_PI = 2f * PI.toFloat()
        const val WAKE_SECONDS = 0.7f
        const val CELEBRATE_SECONDS = 0.8f
        const val LOUD_ENTER = 0.7f
        const val LOUD_EXIT = 0.5f
        const val KICK_DECAY = 0.18f
        const val KICK_GAIN = 2.2f
        const val SETTLE = 0.02f
        const val SETTLE_SECONDS = 1.6f // the slump / dim transitions take about this long to arrive
    }
}
