package echo.player.online

import echo.player.domain.model.RemoteTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Search-box text rules shared by the search and the recent-searches list. */
object SearchText {
    private val whitespace = Regex("\\s+")

    /** Trimmed, inner whitespace collapsed to single spaces. Blank in, blank out. */
    fun normalize(raw: String): String = raw.trim().replace(whitespace, " ")

    /** Searches that differ only in case or spacing are the same recent search. */
    fun key(raw: String): String = normalize(raw).lowercase()
}

sealed interface DiscoverState {
    /** No online source is configured in this build; nothing can be searched. */
    data object NoSource : DiscoverState
    data object Idle : DiscoverState
    data class Loading(val query: String) : DiscoverState
    data class Success(val query: String, val sourceName: String, val tracks: List<RemoteTrack>) : DiscoverState
    data class Empty(val query: String, val sourceName: String) : DiscoverState
    data class Offline(val query: String) : DiscoverState
    data class Failed(val query: String, val error: SourceError) : DiscoverState
}

/**
 * Runs explicit searches against the one configured source. A new search cancels the running one, and a result is
 * only published if it is still the latest, so a slow old search can never overwrite a newer one. Blank queries make
 * no request. It never falls back to another source.
 */
class OnlineSearchController(
    private val sources: () -> List<MusicSource>,
    private val scope: CoroutineScope,
    private val onSearchStarted: (String) -> Unit = {},
) {
    private val _state = MutableStateFlow<DiscoverState>(if (sources().isEmpty()) DiscoverState.NoSource else DiscoverState.Idle)
    val state: StateFlow<DiscoverState> = _state.asStateFlow()

    private var job: Job? = null
    private var generation = 0

    fun submit(raw: String) {
        val query = SearchText.normalize(raw)
        val source = sources().firstOrNull()
        job?.cancel()
        generation++
        if (source == null) {
            _state.value = DiscoverState.NoSource
            return
        }
        if (query.isEmpty()) {
            _state.value = DiscoverState.Idle
            return
        }
        val mine = generation
        _state.value = DiscoverState.Loading(query)
        onSearchStarted(query)
        job = scope.launch {
            val result = try {
                source.search(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(SourceErrors.wrap(e))
            }
            if (mine != generation) return@launch // a newer search owns the screen now
            _state.value = result.fold(
                onSuccess = { found ->
                    val tracks = found.tracks.distinctBy { it.key }
                    if (tracks.isEmpty()) DiscoverState.Empty(query, source.displayName) else DiscoverState.Success(query, source.displayName, tracks)
                },
                onFailure = {
                    when (val error = result.sourceError() ?: SourceError.Unknown) {
                        SourceError.Offline -> DiscoverState.Offline(query)
                        else -> DiscoverState.Failed(query, error)
                    }
                },
            )
        }
    }

    /** Back to the idle screen (for example the search box was cleared). */
    fun reset() {
        job?.cancel()
        generation++
        _state.value = if (sources().isEmpty()) DiscoverState.NoSource else DiscoverState.Idle
    }
}

/** Finds a fresh, playable copy of a track by its stable id. Stream addresses are never remembered. */
class RemoteTrackResolver(private val registry: SourceRegistry) {
    suspend fun resolve(sourceId: String, sourceTrackId: String): Result<RemoteTrack> {
        val source = registry.byId(sourceId)?.takeIf { it.isConfigured }
            ?: return Result.failure(SourceException(SourceError.NotConfigured))
        return source.getTrack(sourceTrackId).mapCatching { track ->
            if (track.playable && !track.streamUrl.isNullOrBlank()) track else throw SourceException(SourceError.TrackUnavailable)
        }
    }
}
