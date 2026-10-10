package echo.player.desktop

import echo.player.domain.folderOf
import echo.player.domain.groupedByFolder
import echo.player.domain.model.Playlist
import echo.player.domain.model.PlaylistEntry
import echo.player.ui.userMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DesktopStoreTests {
    @get:Rule val tmp = TemporaryFolder()

    private val sample = DesktopData(
        roots = listOf("C:/Music", "D:/Songs"),
        favorites = setOf("f:aa", "f:bb"),
        playlists = listOf(
            Playlist(3L, "Road trip", listOf("f:aa"), 1234L, listOf(PlaylistEntry("f:aa", "Vennela", "Test Singer", "Raagam"))),
        ),
        showCompanion = false,
        folderTiles = true,
        welcomeSeen = true,
        visualizerMode = "SPECTRUM",
        nextPlaylistId = 9L,
    )

    @Test
    fun whatIsSavedComesBackUnchanged() {
        val store = DesktopStore(tmp.newFolder("data"))
        store.save(sample)
        assertEquals(sample, store.load())
    }

    @Test
    fun aMissingFileStartsFresh() {
        assertEquals(DesktopData(), DesktopStore(File(tmp.root, "nothing-here")).load())
    }

    @Test
    fun aDamagedFileStartsFreshButIsKeptForInspection() {
        val dir = tmp.newFolder("damaged")
        File(dir, "echo-data.json").writeText("{ this is not json")
        assertEquals(DesktopData(), DesktopStore(dir).load())
        assertTrue(File(dir, "echo-data.damaged.json").isFile)
    }

    @Test
    fun indianScriptsInPlaylistNamesSurvive() {
        val data = sample.copy(playlists = listOf(Playlist(1L, "తెలుగు పాటలు", emptyList(), 0L, emptyList())))
        val store = DesktopStore(tmp.newFolder("unicode"))
        store.save(data)
        assertEquals("తెలుగు పాటలు", store.load().playlists.single().name)
    }

    @Test
    fun theNextPlaylistIdNeverReusesAnExistingId() {
        val decoded = DesktopStore.decode("""{"nextPlaylistId":1,"playlists":[{"id":7,"name":"A","entries":[]}]}""")
        assertTrue(decoded.nextPlaylistId > 7)
    }

    @Test
    fun dataFolderFollowsEachSystemsConvention() {
        assertEquals(File("C:/Users/x/AppData/Roaming", "ECHO"), DesktopPaths.dataDir(mapOf("APPDATA" to "C:/Users/x/AppData/Roaming"), "Windows 11", "C:/Users/x"))
        assertEquals(File("/home/x/.local/share", "echo"), DesktopPaths.dataDir(emptyMap(), "Linux", "/home/x"))
        assertEquals(File("/data/xdg", "echo"), DesktopPaths.dataDir(mapOf("XDG_DATA_HOME" to "/data/xdg"), "Linux", "/home/x"))
    }
}

class ScannerTests {
    @get:Rule val tmp = TemporaryFolder()

    private fun touch(root: File, path: String): File = File(root, path).also { it.parentFile.mkdirs(); it.writeBytes(ByteArray(10) { 1 }) }

    @Test
    fun theSameFileAlwaysGetsTheSameIdAndDifferentFilesDiffer() {
        val a = touch(tmp.root, "Music/a.mp3")
        val b = touch(tmp.root, "Music/b.mp3")
        assertEquals(AudioFiles.idFor(a), AudioFiles.idFor(File(a.path)))
        assertNotEquals(AudioFiles.idFor(a), AudioFiles.idFor(b))
        assertTrue(AudioFiles.idFor(a).startsWith("f:"))
    }

    @Test
    fun foldersLookLikeTheAndroidOnesAndGroupTogether() {
        val root = File(tmp.root, "Music")
        val one = TrackFactory.build(touch(tmp.root, "Music/Telugu/Vennela.mp3"), root, TagInfo(title = "Vennela", artist = "Singer", album = "Raagam", durationMs = 61_000))
        val two = TrackFactory.build(touch(tmp.root, "Music/Telugu/Pallavi.mp3"), root, null)
        val three = TrackFactory.build(touch(tmp.root, "Music/a.mp3"), root, null)
        assertEquals("Music/Telugu/", one.relativePath)
        assertEquals("Music/", three.relativePath)
        assertEquals(listOf("Music", "Music/Telugu"), listOf(one, two, three).groupedByFolder().map { it.name })
        assertEquals("Music/Telugu", folderOf(one.relativePath))
    }

    @Test
    fun missingTagsFallBackToTheFileNameAndUnknowns() {
        val root = File(tmp.root, "Music")
        val t = TrackFactory.build(touch(tmp.root, "Music/My Song.mp3"), root, null)
        assertEquals("My Song", t.title)
        assertEquals("Unknown Artist", t.artist)
        assertEquals("Unknown Album", t.album)
        assertEquals("audio/mpeg", t.mimeType)
    }

    @Test
    fun onlyAudioFilesAreListedAndNothingIsChanged() = runBlocking {
        val root = File(tmp.root, "Music")
        val song = touch(tmp.root, "Music/Rock/song.mp3")
        touch(tmp.root, "Music/Rock/notes.txt")
        touch(tmp.root, "Music/Rock/cover.jpg")
        touch(tmp.root, "Music/Hindi/गीत.flac")
        val before = song.lastModified()
        val tracks = DesktopTrackSource({ listOf(root) }, File(tmp.root, "cache.json"), tags = { TagInfo(title = it.nameWithoutExtension) }).loadTracks()
        assertEquals(setOf("song", "गीत"), tracks.map { it.title }.toSet())
        assertEquals(before, song.lastModified())
        assertEquals(10L, song.length())
    }

    @Test
    fun aSecondScanReusesTheCachedTagsForUnchangedFiles() = runBlocking {
        val root = File(tmp.root, "Music")
        touch(tmp.root, "Music/one.mp3")
        var reads = 0
        val source = DesktopTrackSource({ listOf(root) }, File(tmp.root, "cache.json"), tags = { reads++; TagInfo(title = "T") })
        source.loadTracks()
        source.loadTracks()
        assertEquals(1, reads)
        touch(tmp.root, "Music/two.mp3")
        source.loadTracks()
        assertEquals(2, reads)
    }

    @Test
    fun aMissingFolderIsSkippedNotAnError() = runBlocking {
        val tracks = DesktopTrackSource({ listOf(File(tmp.root, "gone")) }, null, tags = { null }).loadTracks()
        assertTrue(tracks.isEmpty())
    }

    @Test
    fun formatsAreRecognisedByExtensionIgnoringCase() {
        assertTrue(AudioFiles.isAudio(File("a.MP3")))
        assertTrue(AudioFiles.isAudio(File("a.m4a")))
        assertFalse(AudioFiles.isAudio(File("a.txt")))
        assertFalse(AudioFiles.isAudio(File("mp3")))
    }
}

class DesktopPlayerTests {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob())

    private fun track(id: String) = TrackFactory.build(File("/nonexistent/Music/$id.mp3"), File("/nonexistent/Music"), TagInfo(title = id))

    @Test
    fun aMissingFileIsReportedAsUnavailableNotACrash() {
        val player = DesktopPlayer(scope)
        player.playQueue(listOf(track("a")), 0)
        val deadline = System.currentTimeMillis() + 3000
        while (player.state.value.error == null && System.currentTimeMillis() < deadline) Thread.sleep(25)
        assertEquals(echo.player.playback.PlaybackError.FILE_UNAVAILABLE, player.state.value.error)
        assertFalse(player.state.value.isPlaying)
        player.release()
    }

    @Test
    fun theQueueShuffleAndRepeatStateFollowTheControls() {
        val player = DesktopPlayer(scope)
        player.playQueue(listOf(track("a"), track("b"), track("c")), 1)
        assertEquals(3, player.state.value.queue.size)
        assertEquals(1, player.state.value.currentIndex)
        player.toggleShuffle()
        assertTrue(player.state.value.shuffleEnabled)
        assertEquals("the playing song stays first when shuffling", 0, player.state.value.currentIndex)
        assertEquals("b", player.state.value.title)
        player.toggleShuffle()
        assertEquals("unshuffling puts the queue back in order around the same song", 1, player.state.value.currentIndex)
        player.cycleRepeatMode()
        assertEquals(echo.player.playback.EchoRepeatMode.ALL, player.state.value.repeatMode)
        assertNull(player.state.value.queue.firstOrNull { it.title == "zzz" })
        player.release()
    }

    @Test
    fun everyPlaybackProblemHasAReadableMessage() {
        for (e in echo.player.playback.PlaybackError.entries) assertTrue(e.name, e.userMessage().length > 10)
    }
}
