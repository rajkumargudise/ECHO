package echo.player.online

import echo.player.domain.model.EchoLanguage
import echo.player.domain.model.MusicSourceCapabilities
import echo.player.domain.model.RemoteTrack
import echo.player.domain.model.TrackCapabilities
import kotlinx.coroutines.CancellationException
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder

/**
 * Jamendo (https://developer.jamendo.com), a catalogue of independently released, Creative Commons licensed music
 * that Jamendo itself offers for streaming through its API. Needs a registered API client id, supplied only through
 * local build configuration (see app/build.gradle.kts); without one the source reports [SourceError.NotConfigured].
 * It streams only: this build contains no download path.
 */
class JamendoSource(
    private val clientId: String,
    private val transport: HttpTransport = UrlConnectionTransport(),
    private val baseUrl: String = "https://api.jamendo.com/v3.0",
) : MusicSource {
    override val id: String = ID
    override val displayName: String = "Jamendo"
    override val isConfigured: Boolean get() = clientId.isNotBlank()
    override val capabilities = MusicSourceCapabilities(
        search = true,
        stream = true,
        backgroundPlayback = true,
        attributionRequired = true,
    )

    override suspend fun search(query: String, page: Int, pageSize: Int): Result<MusicSearchResult> {
        val q = query.trim().replace(Regex("\\s+"), " ")
        if (q.isEmpty()) return Result.success(MusicSearchResult(q, page, emptyList(), false))
        val safePage = page.coerceAtLeast(1)
        val size = pageSize.coerceIn(1, 50)
        val url = buildUrl("tracks", "search" to q, "limit" to size.toString(), "offset" to ((safePage - 1) * size).toString())
        return fetch(url) { JamendoParser.parseSearch(it, q, safePage, size) }
    }

    override suspend fun getTrack(sourceTrackId: String): Result<RemoteTrack> {
        if (sourceTrackId.isBlank()) return Result.failure(SourceException(SourceError.TrackUnavailable))
        val url = buildUrl("tracks", "id" to sourceTrackId, "limit" to "1")
        return fetch(url) { body ->
            JamendoParser.parseTracks(body).firstOrNull { it.playable } ?: throw SourceException(SourceError.TrackUnavailable)
        }
    }

    override fun capabilities(track: RemoteTrack): TrackCapabilities = TrackCapabilities(
        canStream = capabilities.stream && track.playable,
        canDownload = false,
        requiresAttribution = capabilities.attributionRequired,
    )

    private fun buildUrl(path: String, vararg params: Pair<String, String>): String {
        val fixed = listOf("client_id" to clientId, "format" to "json", "include" to "musicinfo", "audioformat" to "mp32", "imagesize" to "300")
        return "$baseUrl/$path/?" + (fixed + params).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
    }

    private suspend fun <T> fetch(url: String, parse: (String) -> T): Result<T> {
        if (!isConfigured) return Result.failure(SourceException(SourceError.NotConfigured))
        return try {
            val response = transport.get(url)
            when {
                response.code == 401 || response.code == 403 -> Result.failure(SourceException(SourceError.NotAuthorized))
                response.code !in 200..299 -> Result.failure(SourceException(SourceError.Http(response.code)))
                else -> Result.success(parse(response.body))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(SourceErrors.wrap(e))
        }
    }

    companion object {
        const val ID = "jamendo"
    }
}

/** Turns a thrown failure into a [SourceException] the UI can describe truthfully. */
object SourceErrors {
    fun wrap(e: Throwable): SourceException = when (e) {
        is SourceException -> e
        is SocketTimeoutException -> SourceException(SourceError.Timeout)
        is JSONException -> SourceException(SourceError.Malformed)
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.NoRouteToHostException -> SourceException(SourceError.Offline)
        is javax.net.ssl.SSLException -> SourceException(SourceError.Unknown)
        is IOException -> SourceException(SourceError.Offline)
        else -> SourceException(SourceError.Unknown)
    }
}

/** Jamendo response -> ECHO domain model. Pure, so it is unit tested against sample responses. */
object JamendoParser {
    fun parseSearch(body: String, query: String, page: Int, pageSize: Int): MusicSearchResult {
        val tracks = parseTracks(body)
        return MusicSearchResult(query, page, tracks, hasMore = tracks.size >= pageSize)
    }

    fun parseTracks(body: String): List<RemoteTrack> {
        val root = JSONObject(body)
        val headers = root.optJSONObject("headers") ?: throw SourceException(SourceError.Malformed)
        val status = headers.optString("status")
        if (status != "success") {
            // Code 5 is Jamendo's "invalid client id / credential not authorized".
            throw SourceException(if (headers.optInt("code") == 5) SourceError.NotAuthorized else SourceError.Unknown)
        }
        val results = root.optJSONArray("results") ?: throw SourceException(SourceError.Malformed)
        val seen = HashSet<String>()
        val out = ArrayList<RemoteTrack>()
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val track = map(item) ?: continue
            if (seen.add(track.sourceTrackId)) out += track
        }
        return out
    }

    private fun map(item: JSONObject): RemoteTrack? {
        val id = item.opt("id")?.toString()?.trim().orEmpty()
        val title = item.optString("name").trim()
        if (id.isEmpty() || title.isEmpty()) return null
        val artist = item.optString("artist_name").trim().ifEmpty { "Unknown artist" }
        val audio = item.optString("audio").trim()
        val streamable = item.optBoolean("audio_allowed", true) && audio.startsWith("https://")
        val license = item.optString("license_ccurl").trim().takeIf { it.startsWith("https://") }
        val musicInfo = item.optJSONObject("musicinfo")
        val tags = musicInfo?.optJSONObject("tags")
        val genre = tags?.optJSONArray("genres")?.optString(0)?.trim()?.takeIf { it.isNotEmpty() }
        val language = EchoLanguage.fromProvider(musicInfo?.optString("lang"))
        val album = item.optString("album_name").trim()
        return RemoteTrack(
            sourceId = JamendoSource.ID,
            sourceTrackId = id,
            title = title,
            artist = artist,
            album = album,
            albumArtist = artist,
            durationMs = item.optLong("duration", 0L).coerceAtLeast(0L) * 1000L,
            artworkUrl = (item.optString("image").ifEmpty { item.optString("album_image") }).trim().takeIf { it.startsWith("https://") },
            language = language,
            genre = genre,
            year = item.optString("releasedate").take(4).toIntOrNull(),
            playable = streamable,
            // Jamendo may allow downloads, but this build has no download path and so claims none.
            downloadable = false,
            attributionRequired = true,
            attributionText = buildString {
                append("\"").append(title).append("\" by ").append(artist).append(" via Jamendo")
                if (license != null) append(" · ").append(license)
            },
            license = license,
            sourceDisplayName = "Jamendo",
            streamUrl = audio.takeIf { streamable },
        )
    }
}
