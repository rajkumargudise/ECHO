package echo.player

import androidx.media3.common.PlaybackException
import echo.player.data.local.EchoMigrations
import echo.player.data.local.HistoryDao
import echo.player.data.local.RecentPlayEntity
import echo.player.data.local.RecentSearchEntity
import echo.player.domain.model.MusicSourceCapabilities
import echo.player.domain.model.PlayableTrack
import echo.player.domain.model.RemoteMediaId
import echo.player.domain.model.RemoteTrack
import echo.player.domain.model.TrackCapabilities
import echo.player.online.DiscoverState
import echo.player.online.HistoryRepository
import echo.player.online.HttpResponse
import echo.player.online.HttpTransport
import echo.player.online.JamendoParser
import echo.player.online.JamendoSource
import echo.player.online.JioSaavnSource
import echo.player.online.MusicSearchResult
import echo.player.online.MusicSource
import echo.player.online.OnlineSearchController
import echo.player.online.PlayOrigin
import echo.player.online.PlayRecorder
import echo.player.online.RecentPlay
import echo.player.online.RemoteTrackResolver
import echo.player.online.SearchText
import echo.player.online.SourceError
import echo.player.online.SourceException
import echo.player.online.SourceRegistry
import echo.player.online.TrackActions
import echo.player.online.sourceError
import echo.player.playback.PlaybackError
import echo.player.playback.PlaybackLogic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val SAMPLE = """
{"headers":{"status":"success","code":0,"error_message":"","results_count":3},
 "results":[
  {"id":"1001","name":"Maanu Maakunu","duration":201,"artist_name":"DRGM","album_name":"Folk","releasedate":"2021-05-04",
   "image":"https://img.example/1.jpg","audio":"https://stream.example/1.mp3","audio_allowed":true,
   "license_ccurl":"https://creativecommons.org/licenses/by/3.0/",
   "musicinfo":{"lang":"te","tags":{"genres":["folk"]}}},
  {"id":"1001","name":"Duplicate of the first","duration":10,"artist_name":"X","audio":"https://stream.example/dup.mp3"},
  {"id":"1002","name":"No stream","duration":90,"artist_name":"Y","audio":"","audio_allowed":false},
  {"id":"1003","name":"Plain http stream","duration":90,"artist_name":"Z","audio":"http://stream.example/3.mp3"},
  {"name":"No id","artist_name":"Q"},
  {"id":"1004","name":"","artist_name":"Blank title"}
 ]}
"""

private class FakeTransport(private val reply: () -> HttpResponse) : HttpTransport {
    var calls = 0
    val urls = mutableListOf<String>()
    override suspend fun get(url: String): HttpResponse { calls++; urls += url; return reply() }
}

private fun ok(body: String) = HttpResponse(200, body)

private fun remote(id: String, title: String = "T$id", playable: Boolean = true, source: String = "fake") =
    RemoteTrack(
        sourceId = source, sourceTrackId = id, title = title, artist = "A", playable = playable,
        streamUrl = if (playable) "https://s.example/$id.mp3" else null, sourceDisplayName = "Fake",
        attributionRequired = true, attributionText = "credit",
    )

private class FakeSource(
    override val id: String = "fake",
    override val isConfigured: Boolean = true,
    val searcher: suspend (String) -> Result<MusicSearchResult> = { q -> Result.success(MusicSearchResult(q, 1, listOf(remote("1")), false)) },
    val getter: suspend (String) -> Result<RemoteTrack> = { Result.success(remote(it)) },
) : MusicSource {
    override val displayName = "Fake"
    override val capabilities = MusicSourceCapabilities(search = true, stream = true, attributionRequired = true)
    val queries = mutableListOf<String>()
    override suspend fun search(query: String, page: Int, pageSize: Int): Result<MusicSearchResult> { queries += query; return searcher(query) }
    override suspend fun getTrack(sourceTrackId: String) = getter(sourceTrackId)
    override fun capabilities(track: RemoteTrack) = TrackCapabilities(track.playable, false, true)
}

class JamendoMappingTests {
    @Test
    fun mapsAResponseToEchoModelsAndDropsWhatCannotBeShown() {
        val tracks = JamendoParser.parseTracks(SAMPLE)
        assertEquals(listOf("1001", "1002", "1003"), tracks.map { it.sourceTrackId })
        val first = tracks[0]
        assertEquals("jamendo", first.sourceId)
        assertEquals("Maanu Maakunu", first.title)
        assertEquals("DRGM", first.artist)
        assertEquals("Folk", first.album)
        assertEquals(201_000L, first.durationMs)
        assertEquals(2021, first.year)
        assertEquals(echo.player.domain.model.EchoLanguage.TELUGU, first.language)
        assertEquals("folk", first.genre)
        assertEquals("https://img.example/1.jpg", first.artworkUrl)
        assertEquals("https://stream.example/1.mp3", first.streamUrl)
        assertTrue(first.playable)
        assertEquals("Jamendo", first.sourceDisplayName)
    }

    @Test
    fun attributionAndLicenseComeFromTheProviderData() {
        val first = JamendoParser.parseTracks(SAMPLE)[0]
        assertTrue(first.attributionRequired)
        assertEquals("https://creativecommons.org/licenses/by/3.0/", first.license)
        assertTrue(first.attributionText!!.contains("Maanu Maakunu") && first.attributionText!!.contains("DRGM") && first.attributionText!!.contains("Jamendo"))
    }

    @Test
    fun noDownloadIsClaimedAndNoStreamMeansNotPlayable() {
        val tracks = JamendoParser.parseTracks(SAMPLE)
        assertTrue(tracks.none { it.downloadable })
        assertFalse(tracks[1].playable)
        assertNull(tracks[1].streamUrl)
    }

    @Test
    fun aPlainHttpStreamIsNeverUsed() {
        val http = JamendoParser.parseTracks(SAMPLE).first { it.sourceTrackId == "1003" }
        assertFalse(http.playable)
        assertNull(http.streamUrl)
    }

    @Test
    fun duplicateIdsCollapseToTheFirst() {
        assertEquals(1, JamendoParser.parseTracks(SAMPLE).count { it.sourceTrackId == "1001" })
    }

    @Test
    fun anEmptyResultListIsNotAnError() {
        assertTrue(JamendoParser.parseTracks("""{"headers":{"status":"success","code":0},"results":[]}""").isEmpty())
    }

    @Test
    fun malformedResponsesAreReportedAsMalformed() {
        for (bad in listOf("not json", "{}", """{"headers":{"status":"success"}}""", "[]")) {
            val e = runCatching { JamendoParser.parseTracks(bad) }.exceptionOrNull()
            assertNotNull(bad, e)
            assertEquals(SourceError.Malformed, echo.player.online.SourceErrors.wrap(e!!).error)
        }
    }

    @Test
    fun aRefusedCredentialIsReportedAsNotAuthorized() {
        val body = """{"headers":{"status":"failed","code":5,"error_message":"Invalid Client Id"},"results":[]}"""
        val e = runCatching { JamendoParser.parseTracks(body) }.exceptionOrNull() as SourceException
        assertEquals(SourceError.NotAuthorized, e.error)
    }

    @Test
    fun hasMoreFollowsThePageSize() {
        assertFalse(JamendoParser.parseSearch(SAMPLE, "q", 1, 20).hasMore)
        assertTrue(JamendoParser.parseSearch(SAMPLE, "q", 1, 3).hasMore)
    }
}

class JamendoSourceTests {
    private fun source(clientId: String = "abc123", t: HttpTransport) = JamendoSource(clientId, t, "https://api.example/v3.0")

    @Test
    fun withoutACredentialNothingIsRequested() = runBlocking {
        val t = FakeTransport { ok(SAMPLE) }
        val s = source("", t)
        assertFalse(s.isConfigured)
        assertEquals(SourceError.NotConfigured, s.search("x").sourceError())
        assertEquals(0, t.calls)
    }

    @Test
    fun aBlankQueryMakesNoRequest() = runBlocking {
        val t = FakeTransport { ok(SAMPLE) }
        assertTrue(source(t = t).search("   ").getOrThrow().tracks.isEmpty())
        assertEquals(0, t.calls)
    }

    @Test
    fun searchBuildsAnHttpsRequestWithTheQueryEncodedAndPaged() = runBlocking {
        val t = FakeTransport { ok(SAMPLE) }
        source(t = t).search("  Samajavaragamana   ", page = 2, pageSize = 10)
        val url = t.urls.single()
        assertTrue(url.startsWith("https://"))
        assertTrue(url.contains("search=Samajavaragamana&") || url.endsWith("search=Samajavaragamana"))
        assertTrue(url.contains("offset=10"))
        assertTrue(url.contains("limit=10"))
    }

    @Test
    fun failuresMapToTruthfulErrors() = runBlocking {
        assertEquals(SourceError.Timeout, source(t = FakeTransport { throw SocketTimeoutException() }).search("x").sourceError())
        assertEquals(SourceError.Offline, source(t = FakeTransport { throw UnknownHostException() }).search("x").sourceError())
        assertEquals(SourceError.Offline, source(t = FakeTransport { throw IOException("down") }).search("x").sourceError())
        assertEquals(SourceError.Http(503), source(t = FakeTransport { HttpResponse(503, "") }).search("x").sourceError())
        assertEquals(SourceError.NotAuthorized, source(t = FakeTransport { HttpResponse(403, "") }).search("x").sourceError())
        assertEquals(SourceError.Malformed, source(t = FakeTransport { ok("<html>") }).search("x").sourceError())
    }

    @Test
    fun getTrackResolvesAFreshPlayableCopyOrSaysUnavailable() = runBlocking {
        val found = source(t = FakeTransport { ok(SAMPLE) }).getTrack("1001").getOrThrow()
        assertEquals("1001", found.sourceTrackId)
        assertNotNull(found.streamUrl)
        val none = source(t = FakeTransport { ok("""{"headers":{"status":"success","code":0},"results":[]}""") }).getTrack("9")
        assertEquals(SourceError.TrackUnavailable, none.sourceError())
    }

    @Test
    fun capabilitiesStreamButNeverDownload() {
        val s = source(t = FakeTransport { ok(SAMPLE) })
        assertTrue(s.capabilities.search && s.capabilities.stream && s.capabilities.attributionRequired)
        assertFalse(s.capabilities.download)
        val caps = s.capabilities(JamendoParser.parseTracks(SAMPLE)[0])
        assertTrue(caps.canStream && !caps.canDownload && caps.requiresAttribution)
    }
}

class JioSaavnAdapterTests {
    @Test
    fun theAdapterIsInactiveMakesNoRequestAndFakesNothing() = runBlocking {
        val s = JioSaavnSource()
        assertFalse(s.isConfigured)
        assertEquals(MusicSourceCapabilities.NONE, s.capabilities)
        assertEquals(SourceError.NotAuthorized, s.search("Samajavaragamana").sourceError())
        assertEquals(SourceError.NotAuthorized, s.getTrack("1").sourceError())
    }

    @Test
    fun anInactiveSourceIsNeverOffered() {
        val registry = SourceRegistry(listOf(JioSaavnSource(), JamendoSource("")))
        assertTrue(registry.configured().isEmpty())
        assertEquals("jiosaavn", registry.byId("jiosaavn")?.id)
    }
}

class CapabilityUiTests {
    private val streamOnly = TrackCapabilities(canStream = true, canDownload = false, requiresAttribution = true)

    @Test
    fun playShowsOnlyForAStreamableTrack() {
        assertTrue(TrackActions.of(remote("1"), streamOnly).showPlay)
        assertFalse(TrackActions.of(remote("1", playable = false), streamOnly).showPlay)
        assertFalse(TrackActions.of(remote("1"), streamOnly.copy(canStream = false)).showPlay)
    }

    @Test
    fun downloadNeedsBothTheSourceCapabilityAndAnExplicitDownloadableAsset() {
        assertFalse(TrackActions.of(remote("1"), streamOnly).showDownload)
        assertFalse(TrackActions.of(remote("1").copy(downloadable = true), streamOnly).showDownload)
        assertFalse(TrackActions.of(remote("1"), streamOnly.copy(canDownload = true)).showDownload)
        assertTrue(TrackActions.of(remote("1").copy(downloadable = true), streamOnly.copy(canDownload = true)).showDownload)
    }

    @Test
    fun attributionShowsOnlyWhenRequiredAndPresent() {
        assertTrue(TrackActions.of(remote("1"), streamOnly).showAttribution)
        assertFalse(TrackActions.of(remote("1").copy(attributionText = null), streamOnly).showAttribution)
        assertFalse(TrackActions.of(remote("1"), streamOnly.copy(requiresAttribution = false)).showAttribution)
    }
}

class OnlineSearchControllerTests {
    private fun controller(source: MusicSource?, started: MutableList<String> = mutableListOf()) =
        OnlineSearchController({ listOfNotNull(source).filter { it.isConfigured } }, kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)) { started += it }

    @Test
    fun noConfiguredSourceSaysSoAndSearchesNothing() {
        val c = controller(FakeSource(isConfigured = false))
        assertEquals(DiscoverState.NoSource, c.state.value)
        c.submit("anything")
        assertEquals(DiscoverState.NoSource, c.state.value)
    }

    @Test
    fun blankOrWhitespaceQueriesMakeNoRequestAndRecordNothing() {
        val s = FakeSource()
        val started = mutableListOf<String>()
        val c = controller(s, started)
        c.submit("")
        c.submit("   \t ")
        assertEquals(DiscoverState.Idle, c.state.value)
        assertTrue(s.queries.isEmpty())
        assertTrue(started.isEmpty())
    }

    @Test
    fun aSearchPublishesRealResultsWithTheNormalizedQuery() {
        val s = FakeSource()
        val started = mutableListOf<String>()
        val c = controller(s, started)
        c.submit("  Hello    World ")
        val state = c.state.value as DiscoverState.Success
        assertEquals("Hello World", state.query)
        assertEquals("Fake", state.sourceName)
        assertEquals(listOf("Hello World"), s.queries)
        assertEquals(listOf("Hello World"), started)
    }

    @Test
    fun duplicateResultsAreShownOnce() {
        val s = FakeSource(searcher = { q -> Result.success(MusicSearchResult(q, 1, listOf(remote("1"), remote("1"), remote("2")), false)) })
        val c = controller(s)
        c.submit("x")
        assertEquals(2, (c.state.value as DiscoverState.Success).tracks.size)
    }

    @Test
    fun emptyOfflineAndFailureStatesAreDistinct() {
        val c1 = controller(FakeSource(searcher = { q -> Result.success(MusicSearchResult(q, 1, emptyList(), false)) }))
        c1.submit("none")
        assertTrue(c1.state.value is DiscoverState.Empty)
        val c2 = controller(FakeSource(searcher = { Result.failure(SourceException(SourceError.Offline)) }))
        c2.submit("x")
        assertTrue(c2.state.value is DiscoverState.Offline)
        val c3 = controller(FakeSource(searcher = { Result.failure(SourceException(SourceError.Timeout)) }))
        c3.submit("x")
        assertEquals(DiscoverState.Failed("x", SourceError.Timeout), c3.state.value)
        val c4 = controller(FakeSource(searcher = { throw IllegalStateException("boom") }))
        c4.submit("x")
        assertTrue(c4.state.value is DiscoverState.Failed)
    }

    @Test
    fun aSlowOldSearchNeverOverwritesANewerOne() {
        val slow = CompletableDeferred<Result<MusicSearchResult>>()
        val s = FakeSource(searcher = { q -> if (q == "old") slow.await() else Result.success(MusicSearchResult(q, 1, listOf(remote("new")), false)) })
        val c = controller(s)
        c.submit("old")
        assertTrue(c.state.value is DiscoverState.Loading)
        c.submit("new")
        assertEquals("new", (c.state.value as DiscoverState.Success).query)
        slow.complete(Result.success(MusicSearchResult("old", 1, listOf(remote("old")), false)))
        assertEquals("new", (c.state.value as DiscoverState.Success).query)
    }

    @Test
    fun clearingTheSearchAfterAResultReturnsToIdle() {
        val c = controller(FakeSource())
        c.submit("x")
        c.submit("")
        assertEquals(DiscoverState.Idle, c.state.value)
    }

    @Test
    fun thereIsNoSilentFallbackToAnotherSource() {
        val primary = FakeSource(id = "a", searcher = { Result.failure(SourceException(SourceError.Http(500))) })
        val other = FakeSource(id = "b")
        val c = OnlineSearchController({ listOf(primary, other) }, kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined))
        c.submit("x")
        assertTrue(c.state.value is DiscoverState.Failed)
        assertTrue(other.queries.isEmpty())
    }
}

class SearchTextTests {
    @Test
    fun normalizesWhitespaceAndCase() {
        assertEquals("a b c", SearchText.normalize("  a \t b\n c  "))
        assertEquals("", SearchText.normalize("   "))
        assertEquals(SearchText.key("Hello  World"), SearchText.key(" hello world "))
        assertEquals("సమజవరగమన", SearchText.normalize(" సమజవరగమన "))
    }
}

private class FakeHistoryDao : HistoryDao {
    val searches = LinkedHashMap<String, RecentSearchEntity>()
    val plays = LinkedHashMap<String, RecentPlayEntity>()
    private fun k(e: RecentPlayEntity) = "${e.sourceId}:${e.sourceTrackId}"
    override suspend fun upsertRecentSearch(entry: RecentSearchEntity) { searches[entry.queryKey] = entry }
    override suspend fun recentSearches(limit: Int) = searches.values.sortedByDescending { it.lastUsedAt }.take(limit)
    override suspend fun trimRecentSearches(limit: Int) {
        val keep = searches.values.sortedByDescending { it.lastUsedAt }.take(limit).map { it.queryKey }.toSet()
        searches.keys.retainAll(keep)
    }
    override suspend fun removeRecentSearch(queryKey: String) { searches.remove(queryKey) }
    override suspend fun clearRecentSearches() = searches.clear()
    override suspend fun upsertRecentPlay(entry: RecentPlayEntity) { plays[k(entry)] = entry }
    override suspend fun recentPlays(limit: Int) = plays.values.sortedByDescending { it.lastPlayedAt }.take(limit)
    override suspend fun updateRecentPlayPosition(sourceId: String, sourceTrackId: String, positionMs: Long) {
        plays["$sourceId:$sourceTrackId"]?.let { plays["$sourceId:$sourceTrackId"] = it.copy(positionMs = positionMs) }
    }
    override suspend fun removeRecentPlay(sourceId: String, sourceTrackId: String) { plays.remove("$sourceId:$sourceTrackId") }
    override suspend fun trimRecentPlays(limit: Int) {
        val keep = plays.values.sortedByDescending { it.lastPlayedAt }.take(limit).map { k(it) }.toSet()
        plays.keys.retainAll(keep)
    }
    override suspend fun clearRecentPlays() = plays.clear()
}

class HistoryTests {
    private var now = 1_000L
    private val dao = FakeHistoryDao()
    private val repo = HistoryRepository(dao) { now++ }

    @Test
    fun recentSearchesAreNewestFirstNormalizedAndDeduplicated() = runBlocking {
        repo.recordSearch("anirudh")
        repo.recordSearch("  AR   Rahman ")
        repo.recordSearch("Anirudh")
        repo.recordSearch("Anirudh")
        assertEquals(listOf("Anirudh", "AR Rahman"), repo.recentSearches())
    }

    @Test
    fun blankSearchesAreNeverStored() = runBlocking {
        assertFalse(repo.recordSearch("   "))
        assertTrue(repo.recentSearches().isEmpty())
    }

    @Test
    fun theListIsCappedAtTwenty() = runBlocking {
        for (i in 1..30) repo.recordSearch("query $i")
        val recent = repo.recentSearches()
        assertEquals(20, recent.size)
        assertEquals("query 30", recent.first())
        assertEquals("query 11", recent.last())
    }

    @Test
    fun aSearchCanBeRemovedOrAllCleared() = runBlocking {
        repo.recordSearch("a"); repo.recordSearch("b")
        repo.removeSearch(" A ")
        assertEquals(listOf("b"), repo.recentSearches())
        repo.clearSearches()
        assertTrue(repo.recentSearches().isEmpty())
    }

    private fun play(id: String, source: String = "jamendo") = RecentPlay(source, id, "T$id", "A", "Al", "https://img/$id", 0L, 0L, PlayOrigin.ONLINE)

    @Test
    fun recentPlaysKeepIdentityNotStreamAddresses() = runBlocking {
        repo.recordPlay(RecentPlay.of(remote("7", source = "jamendo"), 5L))
        val saved = repo.recentPlays().single()
        assertEquals("jamendo", saved.sourceId)
        assertEquals("7", saved.sourceTrackId)
        assertEquals(PlayOrigin.ONLINE, saved.origin)
        // The stored entity has no field that could hold a stream address.
        assertTrue(RecentPlayEntity::class.java.declaredFields.none { it.name.contains("stream", true) || it.name.equals("url", true) })
    }

    @Test
    fun replayingATrackMovesItToTheTopWithoutDuplicating() = runBlocking {
        repo.recordPlay(play("1")); repo.recordPlay(play("2")); repo.recordPlay(play("1"))
        assertEquals(listOf("1", "2"), repo.recentPlays().map { it.sourceTrackId })
    }

    @Test
    fun localAndOnlinePlaysShareOneHistory() = runBlocking {
        repo.recordPlay(RecentPlay("local", "content://media/1", "Song", "Me", "", null, 0L, 0L, PlayOrigin.LOCAL))
        repo.recordPlay(play("9"))
        val all = repo.recentPlays()
        assertEquals(listOf(PlayOrigin.ONLINE, PlayOrigin.LOCAL), all.map { it.origin })
        assertTrue(all.last().isLocal)
    }

    @Test
    fun positionIsRememberedAndCannotGoNegative() = runBlocking {
        repo.recordPlay(play("1"))
        repo.updatePosition("jamendo", "1", 42_000L)
        assertEquals(42_000L, repo.recentPlays().single().positionMs)
        repo.updatePosition("jamendo", "1", -5L)
        assertEquals(0L, repo.recentPlays().single().positionMs)
    }

    @Test
    fun playsAreCappedAndCanBeRemovedOrCleared() = runBlocking {
        for (i in 1..60) repo.recordPlay(play("$i"))
        assertEquals(HistoryRepository.MAX_PLAYS, repo.recentPlays().size)
        repo.removePlay("jamendo", "60")
        assertTrue(repo.recentPlays().none { it.sourceTrackId == "60" })
        repo.clearPlays()
        assertTrue(repo.recentPlays().isEmpty())
    }
}

class PlayRecorderTests {
    @Test
    fun onlyANewItemIsRecorded() {
        assertTrue(PlayRecorder.shouldRecord(null, "a"))
        assertFalse(PlayRecorder.shouldRecord("a", "a"))
        assertTrue(PlayRecorder.shouldRecord("a", "b"))
        assertFalse(PlayRecorder.shouldRecord("a", null))
        assertFalse(PlayRecorder.shouldRecord("a", ""))
    }

    @Test
    fun mediaIdsAreFiledBySourceAndOrigin() {
        assertEquals(Triple("jamendo", "123", PlayOrigin.ONLINE), PlayRecorder.identify(RemoteMediaId.encode("jamendo", "123")))
        assertEquals(Triple("local", "content://media/external/audio/media/5", PlayOrigin.LOCAL), PlayRecorder.identify("content://media/external/audio/media/5"))
    }
}

class RemoteIdentityTests {
    @Test
    fun theMediaIdRoundTripsAndCarriesNoUrl() {
        val id = PlayableTrack.Remote(remote("77", source = "jamendo")).mediaId
        assertEquals("jamendo" to "77", RemoteMediaId.parse(id))
        assertFalse(id.contains("http"))
        assertTrue(RemoteMediaId.isRemote(id))
    }

    @Test
    fun localIdsAndGarbageAreNotRemote() {
        assertNull(RemoteMediaId.parse("content://media/external/audio/media/5"))
        assertNull(RemoteMediaId.parse(null))
        assertNull(RemoteMediaId.parse("echo-remote:"))
        assertNull(RemoteMediaId.parse("echo-remote:onlysource"))
        assertFalse(RemoteMediaId.isRemote("content://x"))
    }

    @Test
    fun aTrackIdMayContainColons() {
        assertEquals("s" to "a:b:c", RemoteMediaId.parse(RemoteMediaId.encode("s", "a:b:c")))
    }
}

class ReplayResolverTests {
    @Test
    fun replayResolvesAFreshStreamByStableId() = runBlocking {
        val resolver = RemoteTrackResolver(SourceRegistry(listOf(FakeSource())))
        val track = resolver.resolve("fake", "42").getOrThrow()
        assertEquals("42", track.sourceTrackId)
        assertNotNull(track.streamUrl)
    }

    @Test
    fun anUnknownOrUnconfiguredSourceIsReportedNotSubstituted() = runBlocking {
        val resolver = RemoteTrackResolver(SourceRegistry(listOf(FakeSource(id = "other"), FakeSource(id = "off", isConfigured = false))))
        assertEquals(SourceError.NotConfigured, resolver.resolve("missing", "1").sourceError())
        assertEquals(SourceError.NotConfigured, resolver.resolve("off", "1").sourceError())
    }

    @Test
    fun aTrackThatHasNoStreamAnymoreIsUnavailable() = runBlocking {
        val resolver = RemoteTrackResolver(SourceRegistry(listOf(FakeSource(getter = { Result.success(remote(it, playable = false)) }))))
        assertEquals(SourceError.TrackUnavailable, resolver.resolve("fake", "1").sourceError())
        val gone = RemoteTrackResolver(SourceRegistry(listOf(FakeSource(getter = { Result.failure(SourceException(SourceError.TrackUnavailable)) }))))
        assertEquals(SourceError.TrackUnavailable, gone.resolve("fake", "1").sourceError())
    }
}

class OnlinePlaybackErrorTests {
    @Test
    fun networkAndExpiredStreamErrorsAreTold() {
        assertEquals(PlaybackError.NETWORK, PlaybackLogic.mapError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertEquals(PlaybackError.NETWORK, PlaybackLogic.mapError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertEquals(PlaybackError.STREAM_UNAVAILABLE, PlaybackLogic.mapError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        // The earlier mappings are unchanged.
        assertEquals(PlaybackError.FILE_UNAVAILABLE, PlaybackLogic.mapError(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
    }
}

class MigrationAndPolicyTests {
    private fun main(vararg parts: String) = File("src/main/${parts.joinToString("/")}")
    private fun sources(): List<File> = main("java").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun migrationOnlyAddsTheTwoHistoryTablesAndTouchesNothingElse() {
        val sql = EchoMigrations.MIGRATION_1_2_SQL
        assertEquals(2, sql.size)
        assertTrue(sql.all { it.startsWith("CREATE TABLE IF NOT EXISTS") })
        assertTrue(sql[0].contains("`recent_searches`") && sql[1].contains("`recent_plays`"))
        assertTrue(sql.none { it.contains("favorite_tracks") || it.contains("playlist") || it.contains("DROP", true) })
        assertEquals(1, EchoMigrations.MIGRATION_1_2.startVersion)
        assertEquals(2, EchoMigrations.MIGRATION_1_2.endVersion)
    }

    @Test
    fun thereIsOnePlayerAndOneMediaSessionAndTheyLiveInTheService() {
        val builders = sources().filter { f -> f.readText().let { "ExoPlayer.Builder(" in it || "MediaSession.Builder(" in it } }
        assertEquals(listOf("PlaybackService.kt"), builders.map { it.name })
    }

    @Test
    fun onlySourcesTalkToTheNetworkAndNeverOverClearText() {
        val net = sources().filter { f -> f.readText().let { "HttpURLConnection" in it || "openConnection" in it } }
        assertEquals(listOf("HttpTransport.kt"), net.map { it.name })
        for (f in sources()) {
            val code = f.readLines().filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") || it.trim().startsWith("/*") }.joinToString("\n")
            assertFalse("${f.name} has a clear-text http URL", Regex("\"http://[a-z]").containsMatchIn(code.replace("\"http://stream.example", "")))
        }
    }

    private fun codeOf(f: File) = f.readLines().filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") || it.trim().startsWith("/*") }.joinToString("\n")

    @Test
    fun noCredentialIsInSourceAndNoDownloadPathExists() {
        for (f in sources()) {
            val code = codeOf(f)
            assertFalse("${f.name} must not hold a credential", Regex("client_id\\s*=\\s*[\"'][A-Za-z0-9]{6,}").containsMatchIn(code))
            assertFalse("${f.name} must not contain decryption", Regex("Cipher|DESede|SecretKeySpec|decrypt", RegexOption.IGNORE_CASE).containsMatchIn(code))
            assertFalse("${f.name} must not download", Regex("DownloadManager|download_url|openFileOutput", RegexOption.IGNORE_CASE).containsMatchIn(code))
        }
    }

    @Test
    fun theManifestAddsOnlyTheInternetPermission() {
        val manifest = main("AndroidManifest.xml").readText()
        val perms = Regex("uses-permission android:name=\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf(
                "android.permission.INTERNET", "android.permission.READ_MEDIA_AUDIO", "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.POST_NOTIFICATIONS", "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK", "android.permission.WAKE_LOCK",
            ),
            perms,
        )
        assertTrue(manifest.contains("usesCleartextTraffic=\"false\""))
    }

    @Test
    fun theWidgetsStayProviderIndependent() {
        for (f in main("java", "echo", "player", "widget").walkTopDown().filter { it.extension == "kt" }) {
            assertFalse("${f.name} must not know about a provider", Regex("jamendo|jiosaavn|echo\\.player\\.online", RegexOption.IGNORE_CASE).containsMatchIn(f.readText()))
        }
    }
}
