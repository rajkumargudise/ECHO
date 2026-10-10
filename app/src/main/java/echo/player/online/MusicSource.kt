package echo.player.online

import echo.player.domain.model.MusicSourceCapabilities
import echo.player.domain.model.RemoteTrack
import echo.player.domain.model.TrackCapabilities

data class MusicSearchResult(
    val query: String,
    val page: Int,
    val tracks: List<RemoteTrack>,
    val hasMore: Boolean,
)

/** Why a source could not answer. Mapped to honest wording in the UI; never carries a URL or a credential. */
sealed interface SourceError {
    /** No credential or configuration for this source in this build. */
    data object NotConfigured : SourceError
    data object Offline : SourceError
    data object Timeout : SourceError
    data class Http(val code: Int) : SourceError
    data object Malformed : SourceError

    /** The provider refused this application's access. */
    data object NotAuthorized : SourceError

    /** The track is no longer offered, or has no authorized stream. */
    data object TrackUnavailable : SourceError
    data object Unknown : SourceError
}

class SourceException(val error: SourceError, message: String? = null) : Exception(message ?: error.toString())

fun <T> Result<T>.sourceError(): SourceError? =
    exceptionOrNull()?.let { (it as? SourceException)?.error ?: SourceError.Unknown }

/**
 * One online catalogue. Implementations own all provider networking and response shapes and expose only ECHO
 * domain models. The UI and the player depend on this interface alone, so a provider can be added or replaced
 * without touching either.
 */
interface MusicSource {
    val id: String
    val displayName: String
    val capabilities: MusicSourceCapabilities

    /** Whether ECHO may use this source at all, and why not. Shown truthfully; never worked around. */
    val availability: SourceAvailability
        get() = if (isConfigured) SourceAvailability.AVAILABLE else SourceAvailability.NOT_CONFIGURED

    /** One line on why [availability] is not AVAILABLE; empty when it is. */
    val availabilityReason: String get() = ""

    /** False when this build has no way to talk to the provider (for example no API credential). */
    val isConfigured: Boolean

    suspend fun search(query: String, page: Int = 1, pageSize: Int = 20): Result<MusicSearchResult>

    /** Resolves a fresh, playable copy of one track by its stable id. */
    suspend fun getTrack(sourceTrackId: String): Result<RemoteTrack>

    fun capabilities(track: RemoteTrack): TrackCapabilities
}

enum class SourceAvailability {
    AVAILABLE,

    /** Legitimate, but this build has no credential/configuration for it. */
    NOT_CONFIGURED,

    /** No authorized access exists for ECHO. The adapter stays inactive. */
    UNAVAILABLE,
}

/** The sources compiled into ECHO. Only configured ones are offered to the user; nothing is silently swapped. */
class SourceRegistry(private val all: List<MusicSource>) {
    fun configured(): List<MusicSource> = all.filter { it.isConfigured }
    fun byId(id: String): MusicSource? = all.firstOrNull { it.id == id }
}

/** What the UI may offer for a track, worked out from the capabilities alone. */
data class TrackActions(val showPlay: Boolean, val showDownload: Boolean, val showAttribution: Boolean) {
    companion object {
        fun of(track: RemoteTrack, caps: TrackCapabilities): TrackActions = TrackActions(
            showPlay = caps.canStream && track.playable && !track.streamUrl.isNullOrBlank(),
            // Only when the source explicitly exposes a legitimate downloadable asset. Nothing downloads in this build.
            showDownload = caps.canDownload && track.downloadable,
            showAttribution = caps.requiresAttribution && !track.attributionText.isNullOrBlank(),
        )
    }
}
