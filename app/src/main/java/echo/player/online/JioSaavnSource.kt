package echo.player.online

import echo.player.domain.model.MusicSourceCapabilities
import echo.player.domain.model.RemoteTrack
import echo.player.domain.model.TrackCapabilities

/**
 * JioSaavn adapter slot, deliberately inactive.
 *
 * JioSaavn publishes no public API or licence that lets a third-party app search its catalogue or stream from it.
 * The community clients reach it through private endpoints and by decrypting protected media addresses; ECHO will not
 * do either. Until JioSaavn offers (or grants ECHO) authorized access, this adapter reports itself unconfigured,
 * makes no network request and returns no tracks, so nothing is faked and no other source is silently used in its
 * place. Authorized access would be implemented here, behind the same [MusicSource] interface, without touching the
 * UI or the player.
 */
class JioSaavnSource : MusicSource {
    override val id: String = ID
    override val displayName: String = "JioSaavn"
    override val capabilities: MusicSourceCapabilities = MusicSourceCapabilities.NONE
    override val isConfigured: Boolean = false
    override val availability: SourceAvailability = SourceAvailability.UNAVAILABLE
    override val availabilityReason: String =
        "UNAVAILABLE: JioSaavn's API policy limits API access to JioSaavn and Authorised Partners with an agreement, and no such agreement exists for ECHO."

    override suspend fun search(query: String, page: Int, pageSize: Int): Result<MusicSearchResult> =
        Result.failure(SourceException(SourceError.NotAuthorized, "No authorized JioSaavn access is available to ECHO"))

    override suspend fun getTrack(sourceTrackId: String): Result<RemoteTrack> =
        Result.failure(SourceException(SourceError.NotAuthorized, "No authorized JioSaavn access is available to ECHO"))

    override fun capabilities(track: RemoteTrack): TrackCapabilities = TrackCapabilities(canStream = false, canDownload = false, requiresAttribution = false)

    companion object {
        const val ID = "jiosaavn"
    }
}
