package echo.player.companion

import kotlin.math.max

enum class SymbolGlyph { NOTE, DOUBLE_NOTE, DOT, SLEEP }

/**
 * The few little symbols (♪ ♫ • and a single sleepy z) that drift up out of ECHO's head. A fixed set of slots is
 * reused forever: nothing is allocated while playing, and at most [maxActive] are alive at once.
 *
 * Nothing here is random. When a symbol appears comes from the audio energy and a counter; where it drifts
 * comes from the same counter. The same inputs always give the same symbols.
 */
class MusicSymbolPool(val capacity: Int = 5) {
    private val active = BooleanArray(capacity)
    private val age = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val startX = FloatArray(capacity)
    private val driftX = FloatArray(capacity)
    private val kind = Array(capacity) { SymbolGlyph.NOTE }

    /** How many symbols are alive right now. */
    var activeCount = 0
        private set

    /** True when nothing is alive and nothing is waiting to appear, so the caller can stop updating. */
    val isIdle: Boolean get() = activeCount == 0 && burstLeft == 0

    private var spawnCount = 0
    private var spawnProgress = 0f
    private var burstLeft = 0
    private var burstTimer = 0f
    private var lastState = CompanionState.SLEEPING

    fun isActive(slot: Int): Boolean = active[slot]
    fun glyph(slot: Int): SymbolGlyph = kind[slot]

    /** 0 when it appears, 1 when it is gone. */
    fun progress(slot: Int): Float = (age[slot] / life[slot]).coerceIn(0f, 1f)

    /** Horizontal offset from the head centre, in head-widths: where it started plus how far it has drifted. */
    fun x(slot: Int): Float = startX[slot] + driftX[slot] * age[slot]

    /** How far it has risen, 0..1 of the available height (easing out, so it slows as it climbs). */
    fun rise(slot: Int): Float {
        val p = progress(slot)
        return 1f - (1f - p) * (1f - p)
    }

    /** Opacity: a quick fade in, a hold, then a fade out. */
    fun alpha(slot: Int): Float {
        val p = progress(slot)
        return when {
            p < FADE_IN -> p / FADE_IN
            p > 1f - FADE_OUT -> (1f - p) / FADE_OUT
            else -> 1f
        }.coerceIn(0f, 1f)
    }

    /**
     * @param energy ECHO's smoothed audio energy, 0..1
     */
    fun update(dt: Float, state: CompanionState, energy: Float) {
        val step = dt.coerceIn(0.001f, 0.25f)
        val resting = state == CompanionState.PAUSED || state == CompanionState.SLEEPING

        // One-off moments, on entering a state.
        if (state != lastState) {
            when (state) {
                CompanionState.WAKING -> spawn(SymbolGlyph.NOTE)
                CompanionState.CELEBRATING -> {
                    spawn(SymbolGlyph.DOUBLE_NOTE)
                    burstLeft = 1
                    burstTimer = BURST_GAP
                }
                CompanionState.PAUSED -> if (lastState.isMusical()) spawn(SymbolGlyph.SLEEP)
                else -> Unit
            }
            lastState = state
        }
        if (burstLeft > 0) {
            burstTimer -= step
            if (burstTimer <= 0f) {
                spawn(SymbolGlyph.NOTE)
                burstLeft--
            }
        }

        // A steady trickle while music plays: faster and a little busier the harder it plays.
        if (state == CompanionState.DANCING || state == CompanionState.ENERGETIC) {
            val e = energy.coerceIn(0f, 1f)
            spawnProgress += step * (RATE_LOW + (RATE_HIGH - RATE_LOW) * e)
            if (spawnProgress >= 1f) {
                val limit = if (e > BUSY_ENERGY) maxActive + 1 else maxActive
                if (activeCount < limit) {
                    spawnProgress -= 1f
                    spawn(STEADY_CYCLE[spawnCount % STEADY_CYCLE.size])
                } else {
                    spawnProgress = 1f // wait for a free slot instead of queueing a burst
                }
            }
        } else {
            spawnProgress = 0f
        }

        // Age everything. When ECHO falls asleep the music symbols hurry away; the sleepy z keeps its own pace.
        for (i in 0 until capacity) {
            if (!active[i]) continue
            age[i] += step * if (resting && kind[i] != SymbolGlyph.SLEEP) HURRY else 1f
            if (age[i] >= life[i]) {
                active[i] = false
                activeCount--
            }
        }
    }

    /** Frees every slot at once, for example when the mascot is switched off. */
    fun clear() {
        for (i in 0 until capacity) active[i] = false
        activeCount = 0
        burstLeft = 0
        spawnProgress = 0f
    }

    private val maxActive: Int get() = max(1, capacity - 2)

    private fun spawn(glyph: SymbolGlyph) {
        val slot = (0 until capacity).firstOrNull { !active[it] } ?: return
        spawnCount++
        active[slot] = true
        activeCount++
        age[slot] = 0f
        kind[slot] = glyph
        life[slot] = if (glyph == SymbolGlyph.SLEEP) SLEEP_LIFE else NOTE_LIFE
        // Where it starts and which way it drifts follow the counter, so the pattern varies but never randomly.
        val lane = (spawnCount * 7) % 5 - 2 // -2..2, a repeating but uneven order
        startX[slot] = lane * 0.12f
        driftX[slot] = (if (spawnCount % 2 == 0) 1f else -1f) * (0.05f + 0.03f * (spawnCount % 3))
    }

    private fun CompanionState.isMusical() =
        this == CompanionState.DANCING || this == CompanionState.ENERGETIC ||
            this == CompanionState.CELEBRATING || this == CompanionState.WAKING

    companion object {
        const val RATE_LOW = 0.5f // symbols per second at the quietest
        const val RATE_HIGH = 2.2f // symbols per second at the loudest
        const val NOTE_LIFE = 1.7f
        const val SLEEP_LIFE = 2.2f
        const val BUSY_ENERGY = 0.6f
        const val BURST_GAP = 0.18f
        const val HURRY = 3f
        private const val FADE_IN = 0.15f
        private const val FADE_OUT = 0.4f
        private val STEADY_CYCLE = arrayOf(SymbolGlyph.NOTE, SymbolGlyph.DOT, SymbolGlyph.DOUBLE_NOTE, SymbolGlyph.NOTE)
    }
}
