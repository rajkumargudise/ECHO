package echo.player.widget

/**
 * What a widget needs to know about "the thing playing now", independent of where it came from. A local file
 * today; a future remote track would fill in the same fields. Widgets never see Media3, files or URIs.
 */
data class TrackPresentationState(
    val hasTrack: Boolean = false,
    val title: String = "",
    val artist: String = "",
    /** The user's intent to play (not the momentary player flag), so a track change does not flash "paused". */
    val isPlaying: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    /** Where playback is, and how long the track is; both 0 when unknown. */
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isFavorite: Boolean = false,
    /** False for tracks that cannot be favorites (an online track), so the heart is hidden. */
    val canFavorite: Boolean = false,
) {
    companion object {
        val NOTHING = TrackPresentationState()
    }
}

/**
 * How widgets word the metadata that comes from the playback session (the same title and artist the notification and
 * Now Playing use). Placeholders that mean "no artist" are not shown as if they were a name.
 */
object WidgetLabels {
    private val NO_ARTIST = setOf("", "unknown", "unknown artist", "<unknown>", "null")

    /** The artist to show, or empty when there is none worth showing. */
    fun artist(raw: String?): String {
        val clean = raw?.trim().orEmpty()
        return if (clean.lowercase() in NO_ARTIST) "" else clean
    }

    fun title(raw: String?): String = raw?.trim().orEmpty().ifBlank { "Unknown track" }

    /** "3:05" or "1:02:09"; blank when the time is unknown. */
    fun time(ms: Long): String {
        if (ms <= 0L) return ""
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}

/** Everything a widget layout shows, already worked out (text, labels, which controls are usable). */
data class EchoWidgetState(val track: TrackPresentationState) {
    val hasTrack: Boolean get() = track.hasTrack
    val isPlaying: Boolean get() = track.hasTrack && track.isPlaying

    val titleText: String get() = if (hasTrack) WidgetLabels.title(track.title) else "ECHO"
    val subtitleText: String
        get() = when {
            !hasTrack -> "Nothing playing"
            else -> WidgetLabels.artist(track.artist)
        }

    /** A short state line: PLAYING / PAUSED / IDLE. */
    val stateText: String get() = when { !hasTrack -> "IDLE"; isPlaying -> "PLAYING"; else -> "PAUSED" }

    val playPauseDescription: String get() = if (isPlaying) "Pause" else "Play"
    val previousDescription: String get() = "Previous track"
    val nextDescription: String get() = "Next track"

    val openDescription: String get() = if (hasTrack) "Open ECHO Now Playing" else "Open ECHO"

    /** Whether the whole-widget tap should land on Now Playing (a track exists) or the library. */
    val opensNowPlaying: Boolean get() = hasTrack

    /** Progress through the track, 0-100; the bar is only shown when the length is known. */
    val showProgress: Boolean get() = hasTrack && track.durationMs > 0
    val progressPercent: Int
        get() = if (showProgress) ((track.positionMs.coerceIn(0L, track.durationMs) * 100) / track.durationMs).toInt() else 0
    val elapsedMs: Long get() = if (hasTrack) track.positionMs.coerceAtLeast(0L) else 0L
    val durationText: String get() = if (hasTrack) WidgetLabels.time(track.durationMs) else ""

    val canFavorite: Boolean get() = hasTrack && track.canFavorite
    val isFavorite: Boolean get() = canFavorite && track.isFavorite
    val favoriteDescription: String get() = if (isFavorite) "Remove from favorites" else "Add to favorites"

    val canPrevious: Boolean get() = hasTrack && track.hasPrevious
    val canNext: Boolean get() = hasTrack && track.hasNext

    /** The widget's own summary for screen readers. */
    val summaryDescription: String
        get() = if (hasTrack) "ECHO, ${stateText.lowercase()}: $titleText${if (subtitleText.isNotEmpty()) ", $subtitleText" else ""}" else "ECHO, nothing playing"
}

enum class WidgetAction(val intentAction: String) {
    PLAY("echo.player.widget.PLAY"),
    PAUSE("echo.player.widget.PAUSE"),
    TOGGLE("echo.player.widget.TOGGLE"),
    NEXT("echo.player.widget.NEXT"),
    PREVIOUS("echo.player.widget.PREVIOUS"),

    /** Adds or removes the playing song from favorites. Not a player command: the receiver handles it. */
    FAVORITE("echo.player.widget.FAVORITE"),
    ;

    companion object {
        fun fromIntentAction(action: String?): WidgetAction? = entries.firstOrNull { it.intentAction == action }
    }
}

/** The few playback commands a widget may send. The real implementation talks to the one Media3 session. */
interface WidgetPlayerCommands {
    val isPlaying: Boolean
    fun play()
    fun pause()
    fun next()
    fun previous()
}

object WidgetActions {
    /** Routes a widget button to the player. Next/previous do nothing at the ends of the queue. */
    fun apply(action: WidgetAction, player: WidgetPlayerCommands, canNext: Boolean = true, canPrevious: Boolean = true) {
        when (action) {
            WidgetAction.PLAY -> player.play()
            WidgetAction.PAUSE -> player.pause()
            WidgetAction.TOGGLE -> if (player.isPlaying) player.pause() else player.play()
            WidgetAction.NEXT -> if (canNext) player.next()
            WidgetAction.PREVIOUS -> if (canPrevious) player.previous()
            WidgetAction.FAVORITE -> Unit // handled by the receiver, which talks to the favorites store
        }
    }
}

/**
 * The last known presentation state, kept in two short strings so a widget can be drawn when it is added, restored
 * after a reboot or after the process was killed, without starting the player.
 */
object WidgetSnapshot {
    private const val SEP = '\u001F'

    fun encode(s: TrackPresentationState): String =
        listOf(
            if (s.hasTrack) "1" else "0", if (s.isPlaying) "1" else "0", if (s.hasPrevious) "1" else "0", if (s.hasNext) "1" else "0",
            clean(s.title), clean(s.artist), s.positionMs.toString(), s.durationMs.toString(),
            if (s.isFavorite) "1" else "0", if (s.canFavorite) "1" else "0",
        )
            .joinToString(SEP.toString())

    fun decode(text: String?): TrackPresentationState {
        if (text.isNullOrEmpty()) return TrackPresentationState.NOTHING
        val p = text.split(SEP)
        if (p.size < 6) return TrackPresentationState.NOTHING
        // Snapshots written before progress and favorites existed have only six fields; the rest default.
        return TrackPresentationState(
            p[0] == "1", p[4], p[5], p[1] == "1", p[2] == "1", p[3] == "1",
            positionMs = p.getOrNull(6)?.toLongOrNull() ?: 0L,
            durationMs = p.getOrNull(7)?.toLongOrNull() ?: 0L,
            isFavorite = p.getOrNull(8) == "1",
            canFavorite = p.getOrNull(9) == "1",
        )
            .let { if (it.hasTrack) it else TrackPresentationState.NOTHING }
    }

    /** What a restored widget shows when the player is not running: the track, but never "playing". */
    fun asRestored(s: TrackPresentationState): TrackPresentationState = s.copy(isPlaying = false)

    private fun clean(text: String) = text.replace(SEP, ' ').replace('\n', ' ').take(160)
}
