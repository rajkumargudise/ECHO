package echo.player

import echo.player.data.local.EchoMigrations
import echo.player.data.local.HistoryDao
import echo.player.data.local.RecentPlayEntity
import echo.player.data.local.RecentSearchEntity
import echo.player.domain.model.EchoLanguage
import echo.player.domain.model.LanguageFacets
import echo.player.domain.model.MusicSourceCapabilities
import echo.player.domain.model.PlayableTrack
import echo.player.domain.model.RemoteTrack
import echo.player.domain.model.TrackCapabilities
import echo.player.online.DiscoverState
import echo.player.online.HistoryRepository
import echo.player.online.JamendoParser
import echo.player.online.JamendoSource
import echo.player.online.JioSaavnSource
import echo.player.online.MusicSearchResult
import echo.player.online.MusicSource
import echo.player.online.OnlineSearchController
import echo.player.online.RecentPlay
import echo.player.online.RemoteTrackResolver
import echo.player.online.SourceAvailability
import echo.player.online.SourceError
import echo.player.online.SourceException
import echo.player.online.SourceRegistry
import echo.player.online.sourceError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A stand-in for a future legitimate Indian provider: it implements only [MusicSource]. */
private class IndianFakeSource(
    private val catalogue: List<RemoteTrack>,
    private val failure: SourceError? = null,
) : MusicSource {
    override val id = "indian-fake"
    override val displayName = "Indian Fake"
    override val isConfigured = true
    override val capabilities = MusicSourceCapabilities(search = true, stream = true, backgroundPlayback = true)
    override suspend fun search(query: String, page: Int, pageSize: Int): Result<MusicSearchResult> =
        if (failure != null) Result.failure(SourceException(failure))
        else Result.success(MusicSearchResult(query, page, catalogue.filter { it.title.contains(query, true) || it.language.label.equals(query, true) }, false))
    override suspend fun getTrack(sourceTrackId: String): Result<RemoteTrack> =
        catalogue.firstOrNull { it.sourceTrackId == sourceTrackId }?.let { Result.success(it) } ?: Result.failure(SourceException(SourceError.TrackUnavailable))
    override fun capabilities(track: RemoteTrack) = TrackCapabilities(track.playable, false, false)
}

private fun song(id: String, title: String, lang: EchoLanguage) = RemoteTrack(
    sourceId = "indian-fake", sourceTrackId = id, title = title, artist = "A", language = lang, playable = true,
    streamUrl = "https://s.example/$id.mp3", sourceDisplayName = "Indian Fake",
)

class LanguageNormalizationTests {
    @Test
    fun namesAndIsoCodesMapToTheThirteenLanguages() {
        val expected = mapOf(
            "Telugu" to EchoLanguage.TELUGU, "te" to EchoLanguage.TELUGU, "tel" to EchoLanguage.TELUGU,
            "Hindi" to EchoLanguage.HINDI, "hi" to EchoLanguage.HINDI,
            "TAMIL" to EchoLanguage.TAMIL, "ta" to EchoLanguage.TAMIL,
            "Malayalam" to EchoLanguage.MALAYALAM, "ml" to EchoLanguage.MALAYALAM,
            "Kannada" to EchoLanguage.KANNADA, "kn" to EchoLanguage.KANNADA,
            "Bengali" to EchoLanguage.BENGALI, "Bangla" to EchoLanguage.BENGALI, "bn" to EchoLanguage.BENGALI,
            "Marathi" to EchoLanguage.MARATHI, "mr" to EchoLanguage.MARATHI,
            "Punjabi" to EchoLanguage.PUNJABI, "pa" to EchoLanguage.PUNJABI,
            "Gujarati" to EchoLanguage.GUJARATI, "gu" to EchoLanguage.GUJARATI,
            "Odia" to EchoLanguage.ODIA, "Oriya" to EchoLanguage.ODIA, "or" to EchoLanguage.ODIA,
            "Assamese" to EchoLanguage.ASSAMESE, "as" to EchoLanguage.ASSAMESE,
            "Urdu" to EchoLanguage.URDU, "ur" to EchoLanguage.URDU,
            "English" to EchoLanguage.ENGLISH, "en" to EchoLanguage.ENGLISH,
        )
        for ((raw, language) in expected) assertEquals(raw, language, EchoLanguage.fromProvider(raw))
    }

    @Test
    fun regionAndCaseVariantsAreAccepted() {
        assertEquals(EchoLanguage.HINDI, EchoLanguage.fromProvider("hi-IN"))
        assertEquals(EchoLanguage.TELUGU, EchoLanguage.fromProvider("te_IN"))
        assertEquals(EchoLanguage.TAMIL, EchoLanguage.fromProvider("  Tamil  "))
    }

    @Test
    fun otherIndianLanguagesAreGroupedNotMisfiled() {
        for (raw in listOf("Sanskrit", "Bhojpuri", "Konkani", "Maithili", "sa")) assertEquals(raw, EchoLanguage.OTHER_INDIAN, EchoLanguage.fromProvider(raw))
        assertTrue(EchoLanguage.OTHER_INDIAN.isIndian)
    }

    @Test
    fun unstatedOrUnrecognizedIsUnknownNeverAGuess() {
        for (raw in listOf(null, "", "   ", "xx", "klingon", "french", "null", "instrumental")) assertEquals(raw, EchoLanguage.UNKNOWN, EchoLanguage.fromProvider(raw))
        assertFalse(EchoLanguage.UNKNOWN.isIndian)
    }

    @Test
    fun theThirteenTargetLanguagesAreAllPresentAndStableNames() {
        val names = EchoLanguage.entries.map { it.name }
        for (n in listOf("TELUGU", "HINDI", "TAMIL", "MALAYALAM", "KANNADA", "BENGALI", "MARATHI", "PUNJABI", "GUJARATI", "ODIA", "ASSAMESE", "URDU", "ENGLISH", "OTHER_INDIAN", "UNKNOWN")) assertTrue(n, n in names)
        assertEquals(EchoLanguage.TELUGU, EchoLanguage.fromName("TELUGU"))
        assertEquals(EchoLanguage.UNKNOWN, EchoLanguage.fromName("nonsense"))
        assertEquals(EchoLanguage.UNKNOWN, EchoLanguage.fromName(null))
    }

    @Test
    fun theProviderLanguageFlowsIntoTheRemoteTrack() {
        val body = """{"headers":{"status":"success","code":0},"results":[
            {"id":"1","name":"A","artist_name":"X","audio":"https://s/1.mp3","musicinfo":{"lang":"ta"}},
            {"id":"2","name":"B","artist_name":"X","audio":"https://s/2.mp3","musicinfo":{"lang":""}},
            {"id":"3","name":"C","artist_name":"X","audio":"https://s/3.mp3"}]}"""
        assertEquals(listOf(EchoLanguage.TAMIL, EchoLanguage.UNKNOWN, EchoLanguage.UNKNOWN), JamendoParser.parseTracks(body).map { it.language })
    }
}

class LanguageFacetTests {
    private val results = listOf(
        song("1", "a", EchoLanguage.TELUGU), song("2", "b", EchoLanguage.HINDI), song("3", "c", EchoLanguage.TELUGU), song("4", "d", EchoLanguage.UNKNOWN),
    )

    @Test
    fun onlyLanguagesPresentInTheResultsAreOffered() {
        assertEquals(listOf(EchoLanguage.TELUGU, EchoLanguage.HINDI), LanguageFacets.of(results))
        assertTrue(LanguageFacets.of(listOf(song("9", "z", EchoLanguage.UNKNOWN))).isEmpty())
        assertTrue(LanguageFacets.of(emptyList()).isEmpty())
    }

    @Test
    fun filteringKeepsOnlyThatLanguageAndNullMeansAll() {
        assertEquals(listOf("1", "3"), LanguageFacets.filter(results, EchoLanguage.TELUGU).map { it.sourceTrackId })
        assertEquals(4, LanguageFacets.filter(results, null).size)
        assertTrue(LanguageFacets.filter(results, EchoLanguage.TAMIL).isEmpty())
    }
}

class IndianProviderPluggabilityTests {
    private fun controller(vararg sources: MusicSource) =
        OnlineSearchController({ SourceRegistry(sources.toList()).configured() }, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun aNewProviderWorksThroughTheSameSearchHistoryAndPlayablePipeline() = runBlocking {
        val catalogue = listOf(song("t1", "Telugu Song One", EchoLanguage.TELUGU), song("h1", "Hindi Song", EchoLanguage.HINDI))
        val source = IndianFakeSource(catalogue)
        val c = controller(source)
        c.submit("Song")
        val shown = (c.state.value as DiscoverState.Success).tracks
        assertEquals(2, shown.size)
        // Search result -> PlayableTrack: identity is source + track id, never a URL.
        val playable = PlayableTrack.Remote(shown[0])
        assertFalse(playable.mediaId.contains("http"))
        // History keeps the language and replays by id through the resolver.
        val repo = HistoryRepository(MemoryDao()) { 1L }
        repo.recordPlay(RecentPlay.of(shown[0], 5L))
        val saved = repo.recentPlays().single()
        assertEquals(EchoLanguage.TELUGU, saved.language)
        val again = RemoteTrackResolver(SourceRegistry(listOf(source))).resolve(saved.sourceId, saved.sourceTrackId).getOrThrow()
        assertEquals("t1", again.sourceTrackId)
    }

    @Test
    fun anEmptyIndianLanguageSearchIsAnHonestEmptyState() {
        val c = controller(IndianFakeSource(listOf(song("t1", "x", EchoLanguage.TELUGU))))
        c.submit("Malayalam")
        assertTrue(c.state.value is DiscoverState.Empty)
    }

    @Test
    fun providerFailureTimeoutAndOfflineAreReportedTruthfully() {
        val timeout = controller(IndianFakeSource(emptyList(), SourceError.Timeout)).also { it.submit("x") }
        assertEquals(DiscoverState.Failed("x", SourceError.Timeout), timeout.state.value)
        val offline = controller(IndianFakeSource(emptyList(), SourceError.Offline)).also { it.submit("x") }
        assertTrue(offline.state.value is DiscoverState.Offline)
        val broken = controller(IndianFakeSource(emptyList(), SourceError.Malformed)).also { it.submit("x") }
        assertEquals(DiscoverState.Failed("x", SourceError.Malformed), broken.state.value)
    }

    @Test
    fun withNoUsableProviderTheScreenSaysSoAndSearchesNothing() {
        val c = controller(JioSaavnSource(), JamendoSource(""))
        assertEquals(DiscoverState.NoSource, c.state.value)
        c.submit("Samajavaragamana")
        assertEquals(DiscoverState.NoSource, c.state.value)
    }

    @Test
    fun duplicateRemoteResultsCollapse() {
        val dup = song("t1", "Same", EchoLanguage.TELUGU)
        val source = object : MusicSource by IndianFakeSource(emptyList()) {
            override suspend fun search(query: String, page: Int, pageSize: Int) = Result.success(MusicSearchResult(query, 1, listOf(dup, dup, dup), false))
        }
        val c = controller(source)
        c.submit("Same")
        assertEquals(1, (c.state.value as DiscoverState.Success).tracks.size)
    }
}

class JioSaavnStatusTests {
    @Test
    fun jioSaavnIsMarkedUnavailableWithAReasonAndDoesNothing() = runBlocking {
        val s = JioSaavnSource()
        assertEquals(SourceAvailability.UNAVAILABLE, s.availability)
        assertTrue(s.availabilityReason.startsWith("UNAVAILABLE"))
        assertFalse(s.isConfigured)
        assertEquals(MusicSourceCapabilities.NONE, s.capabilities)
        assertEquals(SourceError.NotAuthorized, s.search("Arijit Singh").sourceError())
        assertEquals(SourceError.NotAuthorized, s.getTrack("1").sourceError())
    }

    @Test
    fun otherSourcesReportTheirTruthfulAvailability() {
        assertEquals(SourceAvailability.NOT_CONFIGURED, JamendoSource("").availability)
        assertEquals(SourceAvailability.AVAILABLE, JamendoSource("abc").availability)
    }

    @Test
    fun noDecryptionOrUnofficialEndpointCodeExistsForJioSaavn() {
        val code = java.io.File("src/main/java/echo/player/online/JioSaavnSource.kt").readLines()
            .filterNot { it.trim().startsWith("*") || it.trim().startsWith("/*") || it.trim().startsWith("//") }.joinToString("\n")
        assertFalse(Regex("http|Cipher|DES|decrypt|openConnection|HttpTransport", RegexOption.IGNORE_CASE).containsMatchIn(code))
    }
}

class LanguageHistoryMigrationTests {
    @Test
    fun migrationTwoToThreeOnlyAddsADefaultedLanguageColumn() {
        val sql = EchoMigrations.MIGRATION_2_3_SQL.single()
        assertTrue(sql.startsWith("ALTER TABLE `recent_plays` ADD COLUMN `language`"))
        assertTrue(sql.contains("NOT NULL DEFAULT 'UNKNOWN'"))
        assertFalse(sql.contains("DROP", true))
        assertEquals(2, EchoMigrations.MIGRATION_2_3.startVersion)
        assertEquals(3, EchoMigrations.MIGRATION_2_3.endVersion)
    }

    @Test
    fun onlineRecentPlaysKeepTheLanguageAndUnknownStaysUnknown() = runBlocking {
        val repo = HistoryRepository(MemoryDao()) { 1L }
        repo.recordPlay(RecentPlay.of(song("a", "A", EchoLanguage.MALAYALAM), 1L))
        repo.recordPlay(RecentPlay.of(song("b", "B", EchoLanguage.UNKNOWN), 2L))
        assertEquals(listOf(EchoLanguage.UNKNOWN, EchoLanguage.MALAYALAM), repo.recentPlays().map { it.language })
    }
}

private class MemoryDao : HistoryDao {
    val plays = LinkedHashMap<String, RecentPlayEntity>()
    override suspend fun upsertRecentSearch(entry: RecentSearchEntity) {}
    override suspend fun recentSearches(limit: Int) = emptyList<RecentSearchEntity>()
    override suspend fun trimRecentSearches(limit: Int) {}
    override suspend fun removeRecentSearch(queryKey: String) {}
    override suspend fun clearRecentSearches() {}
    override suspend fun upsertRecentPlay(entry: RecentPlayEntity) { plays["${entry.sourceId}:${entry.sourceTrackId}"] = entry }
    override suspend fun recentPlays(limit: Int) = plays.values.sortedByDescending { it.lastPlayedAt }.take(limit)
    override suspend fun updateRecentPlayPosition(sourceId: String, sourceTrackId: String, positionMs: Long) {}
    override suspend fun removeRecentPlay(sourceId: String, sourceTrackId: String) {}
    override suspend fun trimRecentPlays(limit: Int) {}
    override suspend fun clearRecentPlays() {}
}
