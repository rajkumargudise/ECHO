package echo.player.companion

/**
 * Everything the one ECHO mascot drawing needs to know to strike a pose. [CompanionBrain] supplies it while
 * music plays; the empty-state versions supply fixed values. The drawing code never sees audio or playback.
 */
interface MascotPose {
    val state: CompanionState

    /** Vertical bob, roughly -1..1. */
    val bob: Float

    /** Head tilt, -1..1 (left to right). */
    val tilt: Float

    /** How far the arms are raised, 0..1. */
    val arms: Float

    /** 0 = eyes shut, 1 = wide open. */
    val eyeOpen: Float

    /** Display brightness, 0..1. */
    val glow: Float

    /** 0 = standing tall, 1 = slumped. */
    val slump: Float
}

/** The moods ECHO shows where nothing is playing: empty lists and idle screens. */
enum class MascotExpression { SLEEPING, WAITING, NEUTRAL, UNAVAILABLE }

/** A fixed pose for [expression]. Nothing moves, so nothing needs to animate. */
class StaticMascotPose(expression: MascotExpression) : MascotPose {
    override val state: CompanionState
    override val bob = 0f
    override val tilt: Float
    override val arms = 0f
    override val eyeOpen: Float
    override val glow: Float
    override val slump: Float

    init {
        when (expression) {
            MascotExpression.SLEEPING -> {
                state = CompanionState.SLEEPING; tilt = 0f; eyeOpen = 0f; glow = 0.05f; slump = 1f
            }
            MascotExpression.WAITING -> {
                state = CompanionState.DANCING; tilt = 0.25f; eyeOpen = 1f; glow = 0.7f; slump = 0f
            }
            MascotExpression.NEUTRAL -> {
                state = CompanionState.DANCING; tilt = 0f; eyeOpen = 0.9f; glow = 0.6f; slump = 0.1f
            }
            MascotExpression.UNAVAILABLE -> {
                state = CompanionState.PAUSED; tilt = -0.2f; eyeOpen = 0.35f; glow = 0.28f; slump = 0.6f
            }
        }
    }
}
