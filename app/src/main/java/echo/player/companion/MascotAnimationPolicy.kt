package echo.player.companion

/** How often the mascot needs to be redrawn right now. */
enum class AnimationRate {
    /** Nothing moves: no updates, no redraws, no timer wake-ups. */
    STOPPED,

    /** A short transition is finishing (the slump after a pause, a symbol fading): a few updates a second. */
    RESTING,

    /** Music is playing: the normal ~30 updates a second. */
    ACTIVE,
}

/**
 * The one place that decides whether the mascot animates. Playback is never involved: this only governs drawing.
 */
object MascotAnimationPolicy {
    fun rate(
        companionEnabled: Boolean,
        isPlaying: Boolean,
        brainSettled: Boolean,
        symbolsIdle: Boolean,
    ): AnimationRate = when {
        !companionEnabled -> AnimationRate.STOPPED
        isPlaying -> AnimationRate.ACTIVE
        !brainSettled || !symbolsIdle -> AnimationRate.RESTING
        else -> AnimationRate.STOPPED
    }
}
