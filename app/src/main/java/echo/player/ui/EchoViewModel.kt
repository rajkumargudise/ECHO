package echo.player.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import echo.player.BuildConfig
import echo.player.data.local.EchoDatabase
import echo.player.data.local.FavoriteSignal
import echo.player.data.local.LibraryRepository
import echo.player.domain.AddRootResult
import echo.player.domain.LibraryScan
import echo.player.domain.LibraryScanState
import echo.player.domain.MusicRoot
import echo.player.domain.MusicRootStatus
import echo.player.domain.MusicRoots
import echo.player.domain.NavState
import echo.player.domain.PlaylistLogic
import echo.player.domain.TrackSort
import echo.player.domain.Screen
import echo.player.domain.model.PlayableTrack
import echo.player.domain.model.Playlist
import echo.player.domain.model.RemoteTrack
import echo.player.domain.model.Track
import echo.player.online.HistoryRepository
import echo.player.online.JamendoSource
import echo.player.online.JioSaavnSource
import echo.player.online.OnlineSearchController
import echo.player.online.RecentPlay
import echo.player.online.RemoteTrackResolver
import echo.player.online.SourceError
import echo.player.online.SourceRegistry
import echo.player.online.sourceError
import echo.player.platform.android.AndroidSettingsStore
import echo.player.platform.android.MusicRootInfo
import echo.player.platform.android.SleepTimers
import echo.player.platform.android.MusicRootManager
import echo.player.platform.android.PickFolderResult
import echo.player.playback.PlaybackController
import echo.player.playback.PlaybackControls
import echo.player.visualizer.AudioSampleTap
import echo.player.visualizer.VisualizerEngine
import echo.player.visualizer.VisualizerMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibraryTab(val label: String) {
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    FOLDERS("Folders"),
    FAVORITES("Favorites"),
    PLAYLISTS("Playlists"),
}

/** A drill-down opened from a library tab. Back returns to the parent tab. */
sealed interface DetailTarget {
    data class ArtistTarget(val name: String) : DetailTarget
    data class AlbumTarget(val name: String) : DetailTarget
    data class FolderTarget(val name: String) : DetailTarget
    data class PlaylistTarget(val id: Long) : DetailTarget
}

enum class PermissionPrompt {
    /** Never asked in this session: show the rationale and the Allow button. */
    INITIAL,

    /** Denied once; the system dialog can still be shown again. */
    DENIED,

    /** Denied and Android will no longer show the dialog; only App Settings can grant it. */
    BLOCKED,
}

fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

class EchoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LibraryRepository(application.applicationContext)
    private val musicRoots = MusicRootManager(application)
    private val settings = AndroidSettingsStore(application)

    /** The only handle on playback; the player itself lives in PlaybackService. */
    val playback: PlaybackControls = PlaybackController(application, viewModelScope)

    /** One visualizer for the whole app; it holds only arrays, no native resources. */
    val visualizer = VisualizerEngine(AudioSampleTap)

    var tracks by mutableStateOf<List<Track>>(emptyList())
    var favorites by mutableStateOf<Set<String>>(emptySet())
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
    var searchQuery by mutableStateOf("")
    var selectedTab by mutableStateOf(LibraryTab.SONGS)
    var detail by mutableStateOf<DetailTarget?>(null)
    var permissionPrompt by mutableStateOf(PermissionPrompt.INITIAL)
    var isLoading by mutableStateOf(false)

    /** True once a library scan has finished at least once. Until then the list says it is scanning, never "no music". */
    var libraryLoadedOnce by mutableStateOf(false)
        private set

    val scanState: LibraryScanState get() = LibraryScan.stateOf(libraryLoadedOnce, isLoading, tracks.size)

    /** The one sleep timer. It lives in the process (see SleepTimers), so rotation and screen changes never touch it. */
    val sleepTimer = SleepTimers.timer

    // region online discovery

    /** Sources compiled into ECHO. Only configured ones are used; JioSaavn stays inactive (see JioSaavnSource). */
    private val sourceRegistry = SourceRegistry(listOf(JamendoSource(BuildConfig.JAMENDO_CLIENT_ID), JioSaavnSource()))
    private val history = HistoryRepository(EchoDatabase.getInstance(application).echoDao())
    private val resolver = RemoteTrackResolver(sourceRegistry)

    val discover = OnlineSearchController({ sourceRegistry.configured() }, viewModelScope) { query ->
        viewModelScope.launch { history.recordSearch(query); loadHistory() }
    }
    var discoverQuery by mutableStateOf("")

    /** Narrow the results to one language. Only offered for languages that really appear in them. */
    var languageFilter by mutableStateOf<echo.player.domain.model.EchoLanguage?>(null)
    var recentSearches by mutableStateOf<List<String>>(emptyList())
        private set
    var recentPlays by mutableStateOf<List<RecentPlay>>(emptyList())
        private set

    /** A one-line, truthful note about the last online play attempt ("unavailable", "offline"...). */
    var onlineMessage by mutableStateOf<String?>(null)

    /** What the track's own source says can be done with it. */
    fun capabilitiesOf(track: RemoteTrack) =
        sourceRegistry.byId(track.sourceId)?.capabilities(track) ?: echo.player.domain.model.TrackCapabilities(false, false, false)

    fun openDiscover() {
        onlineMessage = null
        nav = nav.open(Screen.DISCOVER)
        viewModelScope.launch { loadHistory() }
    }

    fun loadHistory() {
        viewModelScope.launch {
            recentSearches = history.recentSearches()
            recentPlays = history.recentPlays()
        }
    }

    fun submitDiscoverSearch() {
        onlineMessage = null
        languageFilter = null
        discover.submit(discoverQuery)
    }

    fun runRecentSearch(query: String) {
        discoverQuery = query
        submitDiscoverSearch()
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch { history.removeSearch(query); loadHistory() }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { history.clearSearches(); loadHistory() }
    }

    fun clearRecentPlays() {
        viewModelScope.launch { history.clearPlays(); loadHistory() }
    }

    /** Plays a list of search results through the one player, starting at [index]. */
    fun playOnline(tracks: List<RemoteTrack>, index: Int) {
        onlineMessage = null
        playback.playItems(tracks.map { PlayableTrack.Remote(it) }, index)
    }

    /**
     * Plays something from Recently played. Online tracks are resolved again by their stable id (no stored stream
     * address); if that fails the reason is shown and nothing else is played in its place.
     */
    fun replay(play: RecentPlay) {
        onlineMessage = null
        if (play.isLocal) {
            val local = tracks.firstOrNull { it.id == play.sourceTrackId }
            if (local != null) playback.playQueue(listOf(local), 0) else onlineMessage = "Track unavailable: this file is no longer in your library."
            return
        }
        viewModelScope.launch {
            val result = resolver.resolve(play.sourceId, play.sourceTrackId)
            val track = result.getOrNull()
            if (track != null) {
                playback.playItems(listOf(PlayableTrack.Remote(track)), 0)
            } else {
                val source = sourceRegistry.byId(play.sourceId)?.displayName ?: "Source"
                onlineMessage = when (result.sourceError()) {
                    SourceError.NotConfigured, SourceError.NotAuthorized -> "Source unavailable: $source can't be reached from this build."
                    SourceError.Offline -> "You're offline. \"${play.title}\" needs a connection."
                    SourceError.Timeout -> "$source took too long to answer. Try again."
                    SourceError.TrackUnavailable -> "Track unavailable: \"${play.title}\" is no longer offered by $source."
                    else -> "Couldn't load \"${play.title}\" from $source."
                }
            }
        }
    }

    // endregion

    var nav by mutableStateOf(NavState())
        private set
    var showQueue by mutableStateOf(false)
    var visualizerMode by mutableStateOf(VisualizerMode.fromName(settings.visualizerModeName))
        private set
    var showCompanion by mutableStateOf(settings.showCompanion)
        private set
    var askedForNotificationPermission = false

    var musicFolders by mutableStateOf<List<MusicRootInfo>>(emptyList())
        private set
    var musicFoldersLoaded by mutableStateOf(false)
        private set
    var folderMessage by mutableStateOf<String?>(null)

    /** True until the user has chosen at least one music folder: ECHO never scans the whole device by default. */
    val needsMusicSetup: Boolean get() = musicFoldersLoaded && musicFolders.isEmpty()

    private var refreshJob: Job? = null

    // True once a library load has been started for the current "permission granted" period, so the
    // grant callback and the ON_RESUME check never trigger two scans for the same grant.
    private var loadedForCurrentGrant = false

    private val favoritesChanged: () -> Unit = { viewModelScope.launch { favorites = repository.favoriteIds() } }

    init {
        FavoriteSignal.add(favoritesChanged)
        onPermissionStateChanged(hasPermission())
        loadHistory()
    }

    // region navigation

    fun openNowPlaying() {
        nav = nav.open(Screen.NOW_PLAYING)
    }

    fun selectVisualizerMode(mode: VisualizerMode) {
        visualizerMode = mode
        settings.visualizerModeName = mode.name
    }

    fun updateShowCompanion(show: Boolean) {
        showCompanion = show
        settings.showCompanion = show
    }

    fun openSettings() {
        nav = nav.open(Screen.SETTINGS)
    }

    fun openMusicFolders() {
        folderMessage = null
        nav = nav.open(Screen.MUSIC_FOLDERS)
    }

    /** Back for the full-screen destinations. Returns false when there is nothing above the library. */
    fun navigateBack(): Boolean {
        if (!nav.canGoBack) return false
        if (nav.current == Screen.NOW_PLAYING && showQueue) {
            showQueue = false
        } else {
            nav = nav.back()
        }
        return true
    }

    // endregion

    // region permission

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(getApplication(), audioPermission()) == PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionStateChanged(granted: Boolean) {
        if (granted) {
            if (!loadedForCurrentGrant) {
                loadedForCurrentGrant = true
                refreshLibrary()
            }
        } else {
            loadedForCurrentGrant = false
        }
    }

    fun handlePermissionResult(granted: Boolean, canAskAgain: Boolean) {
        if (!granted) {
            permissionPrompt = if (canAskAgain) PermissionPrompt.DENIED else PermissionPrompt.BLOCKED
        }
        onPermissionStateChanged(granted)
    }

    // endregion

    // region library

    fun refreshLibrary() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            isLoading = true
            try {
                val infos = withContext(Dispatchers.IO) { musicRoots.rootInfos() }
                musicFolders = infos
                musicFoldersLoaded = true
                val active = infos.filter { it.status == MusicRootStatus.AVAILABLE }.map { it.root }
                val snapshot = repository.loadLibrary(active)
                tracks = snapshot.tracks
                favorites = snapshot.favoriteIds
                playlists = snapshot.playlists
            } finally {
                isLoading = false
                libraryLoadedOnce = true // a failed scan must not leave the list on "scanning" forever
            }
        }
    }

    // endregion

    // region music folders

    fun useDefaultMusicFolder() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { musicRoots.addDefaultMusicFolder() }
            // Replaces any earlier "Removed ..." message so it never outlives the state it described.
            folderMessage = if (result is AddRootResult.Added) "Added ${MusicRoots.DEFAULT_MUSIC.displayPath}." else null
            refreshLibrary()
        }
    }

    fun addMusicFolder(treeUri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { musicRoots.addFromTree(treeUri) }
            folderMessage = describe(result)
            refreshLibrary()
        }
    }

    /** Replaces a revoked or missing folder with a newly picked one (or refreshes it when the same folder is picked). */
    fun reselectMusicFolder(old: MusicRoot, treeUri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val picked = musicRoots.addFromTree(treeUri)
                val newKey = when (picked) {
                    is PickFolderResult.Added -> picked.root.key
                    is PickFolderResult.Duplicate -> picked.root.key
                    PickFolderResult.Unsupported -> null
                }
                // The broken entry is replaced whether the pick is new or already listed; picking the same
                // folder again just refreshes it.
                if (newKey != null && newKey != old.key) musicRoots.remove(old)
                picked
            }
            folderMessage = describe(result)
            refreshLibrary()
        }
    }

    fun removeMusicFolder(root: MusicRoot) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { musicRoots.remove(root) }
            folderMessage = "Removed ${root.displayPath}. Its songs are hidden; your favorites and playlists are kept."
            refreshLibrary()
        }
    }

    private fun describe(result: PickFolderResult): String = when (result) {
        is PickFolderResult.Added -> "Added ${result.root.displayPath}."
        is PickFolderResult.Duplicate -> "${result.root.displayPath} is already in your list."
        PickFolderResult.Unsupported -> "That location can't be used. Pick a folder on your phone's own storage."
    }

    // endregion

    // region favorites and playlists

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            repository.toggleFavorite(track)
            FavoriteSignal.changed()
            refreshLibrary()
        }
    }

    fun toggleFavoriteById(trackId: String) {
        tracks.firstOrNull { it.id == trackId }?.let(::toggleFavorite)
    }

    /** A short confirmation for the last playlist action; the screen shows it briefly and clears it. */
    var playlistMessage by mutableStateOf<String?>(null)

    var songSort by mutableStateOf(TrackSort.A_Z)

    /** The first-run hints. They show until they have been closed once; Settings can bring them back. */
    var showWelcome by mutableStateOf(!settings.welcomeSeen)
        private set

    fun dismissWelcome() {
        showWelcome = false
        settings.welcomeSeen = true
    }

    fun showWelcomeAgain() {
        showWelcome = true
    }

    /** The song the "+" chooser is open for (from a list row or from Now Playing); null when it is closed. */
    var playlistTarget by mutableStateOf<Track?>(null)

    /** Folders shown as tiles or as a list; remembered between launches. */
    var folderTiles by mutableStateOf(settings.folderTiles)
        private set

    fun updateFolderTiles(tiles: Boolean) {
        folderTiles = tiles
        settings.folderTiles = tiles
    }

    /** The "+" on Now Playing: opens the chooser for the song that is playing. Only library songs can be added. */
    fun addCurrentToPlaylist() {
        val id = playback.state.value.currentTrackId ?: return
        val track = tracks.firstOrNull { it.id == id }
        if (track != null) playlistTarget = track else playlistMessage = "Only songs from your library can be added to a playlist."
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            val name = playlists.firstOrNull { it.id == playlistId }?.name ?: "playlist"
            val added = repository.addTrackToPlaylist(playlistId, track)
            playlistMessage = if (added) PlaylistLogic.addedMessage(name) else PlaylistLogic.alreadyInMessage(name)
            refreshLibrary()
        }
    }

    /** Creates a playlist and puts [track] in it, in one step (the "+" chooser's Create playlist). */
    fun createPlaylistAndAdd(name: String, track: Track) {
        val clean = PlaylistLogic.cleanName(name)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val id = repository.createPlaylist(clean)
            repository.addTrackToPlaylist(id, track)
            playlistMessage = PlaylistLogic.createdMessage(clean)
            refreshLibrary()
        }
    }

    /** Plays a list of local songs, optionally shuffled from a random start. */
    fun playAll(tracks: List<Track>, shuffle: Boolean) {
        if (tracks.isEmpty()) return
        playback.playQueue(tracks, if (shuffle) tracks.indices.random() else 0)
        if (shuffle != playback.state.value.shuffleEnabled) playback.toggleShuffle()
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
            refreshLibrary()
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name.trim())
            refreshLibrary()
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, newName.trim())
            refreshLibrary()
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            if ((detail as? DetailTarget.PlaylistTarget)?.id == playlistId) detail = null
            refreshLibrary()
        }
    }

    // endregion

    override fun onCleared() {
        FavoriteSignal.remove(favoritesChanged)
        (playback as? PlaybackController)?.release()
        super.onCleared()
    }
}
