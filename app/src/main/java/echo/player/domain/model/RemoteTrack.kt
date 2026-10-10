package echo.player.domain.model

/**
 * A track that lives with an online source. Provider-independent: the UI and the player only ever see this, never a
 * provider's own response shape. The identity is [sourceId] + [sourceTrackId]; [streamUrl] is a short-lived playback
 * address resolved at play time and is never stored as identity or written to history.
 */
data class RemoteTrack(
    val sourceId: String,
    val sourceTrackId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val albumArtist: String = "",
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val language: EchoLanguage = EchoLanguage.UNKNOWN,
    val genre: String? = null,
    val year: Int? = null,
    val playable: Boolean = false,
    val downloadable: Boolean = false,
    val requiresAuthentication: Boolean = false,
    val requiresSubscription: Boolean = false,
    val attributionRequired: Boolean = false,
    val attributionText: String? = null,
    val license: String? = null,
    val sourceDisplayName: String = "",
    /** Resolved, https-only, transient. Null when the source does not authorize streaming this track. */
    val streamUrl: String? = null,
) {
    val key: String get() = "$sourceId:$sourceTrackId"
}

/** What one source can do in general. The UI shows an action only when the source says it supports it. */
data class MusicSourceCapabilities(
    val search: Boolean = false,
    val browse: Boolean = false,
    val stream: Boolean = false,
    val download: Boolean = false,
    val backgroundPlayback: Boolean = false,
    val offline: Boolean = false,
    val authenticationRequired: Boolean = false,
    val subscriptionRequired: Boolean = false,
    val attributionRequired: Boolean = false,
) {
    companion object {
        val NONE = MusicSourceCapabilities()
    }
}

/** What can be done with one particular track. */
data class TrackCapabilities(
    val canStream: Boolean,
    val canDownload: Boolean,
    val requiresAttribution: Boolean,
)

/** A local file or an online track, so the one player can be given either. */
sealed interface PlayableTrack {
    val mediaId: String

    data class Local(val track: Track) : PlayableTrack {
        override val mediaId: String get() = track.id
    }

    data class Remote(val track: RemoteTrack) : PlayableTrack {
        override val mediaId: String get() = RemoteMediaId.encode(track.sourceId, track.sourceTrackId)
    }
}

/** The Media3 mediaId of an online track: stable identity, no URL. Local tracks keep their content URI string. */
object RemoteMediaId {
    private const val PREFIX = "echo-remote:"

    fun encode(sourceId: String, sourceTrackId: String): String = "$PREFIX$sourceId:$sourceTrackId"

    fun isRemote(mediaId: String?): Boolean = mediaId?.startsWith(PREFIX) == true

    /** (sourceId, sourceTrackId), or null when [mediaId] is not an online track. */
    fun parse(mediaId: String?): Pair<String, String>? {
        if (mediaId == null || !isRemote(mediaId)) return null
        val rest = mediaId.removePrefix(PREFIX)
        val split = rest.indexOf(':')
        if (split <= 0 || split == rest.lastIndex) return null
        return rest.substring(0, split) to rest.substring(split + 1)
    }
}
