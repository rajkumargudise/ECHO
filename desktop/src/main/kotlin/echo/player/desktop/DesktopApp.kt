package echo.player.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import echo.player.domain.LibraryScan
import echo.player.domain.LibraryScanState
import echo.player.domain.PlaylistLogic
import echo.player.domain.TrackSort
import echo.player.domain.model.Playlist
import echo.player.domain.model.PlaylistEntry
import echo.player.domain.model.Track
import echo.player.playback.SleepTimer
import echo.player.playback.TickScheduler
import echo.player.playback.TimeSource
import echo.player.visualizer.VisualizerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.Timer
import java.util.TimerTask

enum class DesktopTab(val label: String) { SONGS("SONGS"), ARTISTS("ARTISTS"), ALBUMS("ALBUMS"), FOLDERS("FOLDERS"), FAVORITES("FAVORITES"), PLAYLISTS("PLAYLISTS") }

enum class DesktopScreen { LIBRARY, NOW_PLAYING, SETTINGS, MUSIC_FOLDERS }

sealed interface DesktopDetail {
    data class Artist(val name: String) : DesktopDetail
    data class Album(val name: String) : DesktopDetail
    data class Folder(val name: String) : DesktopDetail
    data class PlaylistTarget(val id: Long) : DesktopDetail
}

/** Everything the desktop screens show and change. The Android version of this is EchoViewModel. */
class DesktopApp(private val store: DesktopStore, private val dataDir: File) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val player = DesktopPlayer(scope)

    private var data = store.load()

    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var loadedOnce by mutableStateOf(false)
        private set
    var scanning by mutableStateOf(false)
        private set
    val roots = mutableStateListOf<String>().also { it.addAll(data.roots.ifEmpty { listOfNotNull(defaultRoot()) }) }
    var favorites by mutableStateOf(data.favorites)
        private set
    val playlists = mutableStateListOf<Playlist>().also { it.addAll(data.playlists) }

    var screen by mutableStateOf(DesktopScreen.LIBRARY)
    var tab by mutableStateOf(DesktopTab.SONGS)
    var detail by mutableStateOf<DesktopDetail?>(null)
    var searchQuery by mutableStateOf("")
    var songSort by mutableStateOf(TrackSort.A_Z)
    var playlistTarget by mutableStateOf<Track?>(null)
    var message by mutableStateOf<String?>(null)

    /** True while a text box has the keyboard, so Space types a space instead of pausing. */
    var typing by mutableStateOf(false)
    var visualizerMode by mutableStateOf(VisualizerMode.fromName(data.visualizerMode))
        private set
    var showCompanion by mutableStateOf(data.showCompanion)
        private set
    var folderTiles by mutableStateOf(data.folderTiles)
        private set
    var showWelcome by mutableStateOf(!data.welcomeSeen)
        private set

    val scanState: LibraryScanState get() = LibraryScan.stateOf(loadedOnce, scanning, tracks.size)

    val sleepTimer = SleepTimer(
        time = TimeSource { System.nanoTime() / 1_000_000L },
        scheduler = object : TickScheduler {
            private var timer: Timer? = null
            override fun schedule(delayMs: Long, task: () -> Unit) {
                cancel()
                timer = Timer("echo-sleep", true).also { it.schedule(object : TimerTask() { override fun run() = task() }, delayMs) }
            }

            override fun cancel() {
                timer?.cancel()
                timer = null
            }
        },
    ).also { it.onExpired = { if (player.state.value.isPlaying) player.togglePlayPause() } }

    private val source = DesktopTrackSource(roots = { roots.map(::File) }, cacheFile = File(dataDir, "tag-cache.json"))

    init {
        rescan()
    }

    private fun defaultRoot(): String? = DesktopPaths.defaultMusicFolder().takeIf { it.isDirectory }?.path

    fun rescan() {
        if (scanning) return
        scanning = true
        scope.launch {
            val result = runCatching { source.loadTracks() }.getOrDefault(emptyList())
            DesktopArtwork.register(result.associate { it.id to File(java.net.URI(it.uri.toString())) })
            tracks = result
            loadedOnce = true
            scanning = false
        }
    }

    // ---- music folders ----
    fun addRoot(path: String) {
        val clean = File(path).absolutePath
        if (roots.any { it.equals(clean, ignoreCase = true) }) return
        roots.add(clean)
        persist()
        rescan()
    }

    fun removeRoot(path: String) {
        roots.remove(path)
        persist()
        rescan()
    }

    // ---- favorites and playlists ----
    fun toggleFavorite(track: Track) {
        favorites = if (track.id in favorites) favorites - track.id else favorites + track.id
        persist()
    }

    fun createPlaylist(rawName: String, track: Track? = null): Playlist? {
        val name = PlaylistLogic.cleanName(rawName).ifEmpty { return null }
        val entries = listOfNotNull(track?.let { PlaylistEntry(it.id, it.title, it.artist, it.album) })
        val playlist = Playlist(data.nextPlaylistId, name, entries.map { it.trackId }, System.currentTimeMillis(), entries)
        data = data.copy(nextPlaylistId = data.nextPlaylistId + 1)
        playlists.add(playlist)
        persist()
        return playlist
    }

    fun renamePlaylist(id: Long, rawName: String) {
        val name = PlaylistLogic.cleanName(rawName).ifEmpty { return }
        val i = playlists.indexOfFirst { it.id == id }
        if (i >= 0) playlists[i] = playlists[i].copy(name = name)
        persist()
    }

    fun deletePlaylist(id: Long) {
        playlists.removeAll { it.id == id }
        if ((detail as? DesktopDetail.PlaylistTarget)?.id == id) detail = null
        persist()
    }

    fun addToPlaylist(playlistId: Long, track: Track) {
        val i = playlists.indexOfFirst { it.id == playlistId }
        if (i < 0) return
        val p = playlists[i]
        message = if (PlaylistLogic.shouldAdd(p.entries.map { it.trackId }, track.id)) {
            playlists[i] = p.copy(
                trackIds = p.trackIds + track.id,
                entries = p.entries + PlaylistEntry(track.id, track.title, track.artist, track.album),
            )
            persist()
            PlaylistLogic.addedMessage(p.name)
        } else {
            PlaylistLogic.alreadyInMessage(p.name)
        }
    }

    fun createPlaylistAndAdd(name: String, track: Track) {
        val p = createPlaylist(name, track) ?: return
        message = PlaylistLogic.createdMessage(p.name)
    }

    fun removeFromPlaylist(playlistId: Long, trackId: String) {
        val i = playlists.indexOfFirst { it.id == playlistId }
        if (i < 0) return
        val p = playlists[i]
        playlists[i] = p.copy(trackIds = p.trackIds - trackId, entries = p.entries.filterNot { it.trackId == trackId })
        persist()
    }

    // ---- settings ----
    fun updateVisualizerMode(mode: VisualizerMode) {
        visualizerMode = mode
        persist()
    }

    fun updateShowCompanion(value: Boolean) {
        showCompanion = value
        persist()
    }

    fun updateFolderTiles(value: Boolean) {
        folderTiles = value
        persist()
    }

    fun dismissWelcome() {
        showWelcome = false
        persist()
    }

    fun showWelcomeAgain() {
        showWelcome = true
    }

    // ---- navigation ----
    fun openNowPlaying() {
        screen = DesktopScreen.NOW_PLAYING
    }

    fun back() {
        when {
            screen != DesktopScreen.LIBRARY -> screen = DesktopScreen.LIBRARY
            detail != null -> detail = null
        }
    }

    fun playAll(list: List<Track>, shuffle: Boolean) {
        if (list.isEmpty()) return
        if (shuffle && !player.state.value.shuffleEnabled) player.toggleShuffle()
        if (!shuffle && player.state.value.shuffleEnabled) player.toggleShuffle()
        player.playQueue(list, if (shuffle) list.indices.random() else 0)
    }

    private fun persist() {
        data = data.copy(
            roots = roots.toList(),
            favorites = favorites,
            playlists = playlists.toList(),
            showCompanion = showCompanion,
            folderTiles = folderTiles,
            welcomeSeen = !showWelcome,
            visualizerMode = visualizerMode.name,
        )
        runCatching { store.save(data) }
    }

    fun close() {
        player.release()
        sleepTimer.cancel()
        persist()
        scope.cancel()
    }
}
