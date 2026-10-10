package echo.player.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.player.companion.MascotExpression
import echo.player.design.EchoColors
import echo.player.design.EchoShapes
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography
import echo.player.design.components.EchoButton
import echo.player.design.components.EchoGroupRow
import echo.player.design.components.EchoIconButton
import echo.player.design.components.EchoTabRow
import echo.player.domain.AlphabetIndex
import echo.player.domain.FolderPaths
import echo.player.domain.LibraryScanState
import echo.player.domain.PlaylistLogic
import echo.player.domain.TrackSort
import echo.player.domain.countLabel
import echo.player.domain.groupedByAlbum
import echo.player.domain.groupedByArtist
import echo.player.domain.groupedByFolder
import echo.player.domain.model.Playlist
import echo.player.domain.model.Track
import echo.player.domain.resolveAgainst
import echo.player.ui.EchoMiniPlayer
import echo.player.ui.library.EmptyState
import echo.player.ui.library.FoldersPage
import echo.player.ui.library.IndexedLazyList
import echo.player.ui.library.PlainTrackList
import echo.player.ui.library.PlayAllBar
import echo.player.ui.library.PlaylistChooserDialog
import echo.player.ui.library.PlaylistEntryList
import echo.player.ui.library.PlaylistList
import echo.player.ui.library.PlaylistNameDialog
import echo.player.ui.library.ScanningState
import echo.player.ui.library.SortBar
import echo.player.ui.library.TrackSections

/** The five groupings of one list of tracks, each built the first time its tab is shown. */
private class LibrarySections(private val tracks: List<Track>) {
    val songs by lazy { AlphabetIndex.buildSections(tracks) { it.title } }
    val artists by lazy { AlphabetIndex.buildSections(tracks.groupedByArtist()) { it.name } }
    val albums by lazy { AlphabetIndex.buildSections(tracks.groupedByAlbum()) { it.name } }
    val folders by lazy { AlphabetIndex.buildSections(tracks.groupedByFolder()) { it.name } }
}

private const val NO_MUSIC = "NO MUSIC YET\nAdd a music folder with the folder button."
private const val NO_MATCHES = "NO MATCHES\nTry another song, artist, or album."

/** On a wide window the lists would stretch across the whole screen; they stay at a readable width, centered. */
@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxSize()) { content() }
    }
}

@Composable
fun DesktopLibrary(app: DesktopApp) {
    val playback by app.player.state.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }

    val filtered = remember(app.tracks, app.searchQuery) { app.tracks.filter { Track.matchesQuery(it, app.searchQuery) } }
    val sections = remember(filtered) { LibrarySections(filtered) }
    val tracksById = remember(app.tracks) { app.tracks.associateBy { it.id } }
    val favoriteSections = if (app.tab == DesktopTab.FAVORITES) {
        remember(filtered, app.favorites) { AlphabetIndex.buildSections(filtered.filter { it.id in app.favorites }) { it.title } }
    } else {
        null
    }

    // Tapping a song plays the list it was tapped in; tapping the song that is already playing opens Now Playing.
    val playFrom: (List<Track>, Int) -> Unit = { queue, index ->
        val tapped = queue.getOrNull(index)
        if (tapped != null && tapped.id == playback.currentTrackId && playback.error == null) app.openNowPlaying() else app.player.playQueue(queue, index)
    }

    Column(modifier = Modifier.fillMaxSize().background(EchoColors.Black)) {
        val detail = app.detail
        if (detail == null) {
            LibraryHeader(app)
            EchoTabRow(
                labels = DesktopTab.entries.map { it.label },
                selectedIndex = app.tab.ordinal,
                onSelect = { app.tab = DesktopTab.entries[it] },
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            Centered {
                if (detail != null) {
                    DetailPage(app, detail, tracksById, playback.currentTrackId, playFrom)
                } else if (app.scanState == LibraryScanState.SCANNING) {
                    ScanningState()
                } else {
                    when (app.tab) {
                        DesktopTab.SONGS -> Column(modifier = Modifier.fillMaxSize()) {
                            if (filtered.isNotEmpty()) SortBar(app.songSort) { app.songSort = it }
                            Box(modifier = Modifier.weight(1f)) {
                                if (app.songSort == TrackSort.A_Z || filtered.isEmpty()) {
                                    val empty = app.scanState == LibraryScanState.READY_EMPTY && app.searchQuery.isBlank()
                                    TrackSections(
                                        sections = sections.songs,
                                        emptyMessage = if (empty) NO_MUSIC else NO_MATCHES,
                                        emptyMascot = if (empty) MascotExpression.SLEEPING else MascotExpression.NEUTRAL,
                                        currentTrackId = playback.currentTrackId,
                                        favoriteIds = app.favorites,
                                        onPlay = playFrom,
                                        onToggleFavorite = app::toggleFavorite,
                                        onAddToPlaylist = { app.playlistTarget = it },
                                    )
                                } else {
                                    val ordered = remember(filtered, app.songSort) { app.songSort.apply(filtered) }
                                    PlainTrackList(ordered, playback.currentTrackId, app.favorites, playFrom, app::toggleFavorite) { app.playlistTarget = it }
                                }
                            }
                        }
                        DesktopTab.FAVORITES -> TrackSections(
                            sections = favoriteSections ?: sections.songs,
                            emptyMessage = "YOUR FAVORITES\nSave songs you love here.",
                            emptyMascot = MascotExpression.WAITING,
                            currentTrackId = playback.currentTrackId,
                            favoriteIds = app.favorites,
                            onPlay = playFrom,
                            onToggleFavorite = app::toggleFavorite,
                            onAddToPlaylist = { app.playlistTarget = it },
                        )
                        DesktopTab.ARTISTS -> if (sections.artists.items.isEmpty()) EmptyState(NO_MATCHES) else {
                            IndexedLazyList(sections.artists, itemKey = { "artist-${it.name}" }) { artist ->
                                EchoGroupRow(artist.name, countLabel(artist.tracks.size, "track"), { app.detail = DesktopDetail.Artist(artist.name) })
                            }
                        }
                        DesktopTab.ALBUMS -> if (sections.albums.items.isEmpty()) EmptyState(NO_MATCHES) else {
                            IndexedLazyList(sections.albums, itemKey = { "album-${it.name}" }) { album ->
                                EchoGroupRow(album.name, "${album.artist} · ${countLabel(album.tracks.size, "track")}", { app.detail = DesktopDetail.Album(album.name) })
                            }
                        }
                        DesktopTab.FOLDERS -> if (sections.folders.items.isEmpty()) EmptyState(NO_MATCHES) else {
                            FoldersPage(
                                folders = sections.folders,
                                tiles = app.folderTiles,
                                onTilesChange = app::updateFolderTiles,
                                onOpen = { app.detail = DesktopDetail.Folder(it) },
                            )
                        }
                        DesktopTab.PLAYLISTS -> Column(modifier = Modifier.fillMaxSize()) {
                            EchoButton(text = "New playlist", onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth().padding(EchoSpacing.L))
                            Box(modifier = Modifier.weight(1f)) {
                                if (app.playlists.isEmpty()) {
                                    EmptyState("NO PLAYLISTS\nCreate your first local playlist.")
                                } else {
                                    PlaylistList(
                                        playlists = app.playlists,
                                        tracksById = tracksById,
                                        onOpen = { app.detail = DesktopDetail.PlaylistTarget(it.id) },
                                        onDelete = { app.deletePlaylist(it.id) },
                                        onRename = { renameTarget = it },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        app.message?.let { text ->
            Text(
                text,
                style = EchoTypography.Caption.copy(color = EchoColors.Lime),
                modifier = Modifier.fillMaxWidth().background(EchoColors.Charcoal).padding(EchoSpacing.S),
            )
        }
        if (playback.currentTrackId != null) EchoMiniPlayer(state = playback, controls = app.player, onOpen = app::openNowPlaying)
    }

    if (showCreate) {
        PlaylistNameDialog("New playlist", "", "Create", { app.createPlaylist(it); showCreate = false }, { showCreate = false })
    }
    renameTarget?.let { p ->
        PlaylistNameDialog("Rename playlist", p.name, "Rename", { app.renamePlaylist(p.id, it); renameTarget = null }, { renameTarget = null })
    }
    app.playlistTarget?.let { track ->
        PlaylistChooserDialog(
            songTitle = track.title,
            choices = PlaylistLogic.choices(app.playlists, track.id),
            onPick = { app.addToPlaylist(it, track); app.playlistTarget = null },
            onCreate = { app.createPlaylistAndAdd(it, track); app.playlistTarget = null },
            onDismiss = { app.playlistTarget = null },
        )
    }
}

@Composable
private fun LibraryHeader(app: DesktopApp) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = EchoSpacing.L, end = EchoSpacing.Xs, top = EchoSpacing.S, bottom = EchoSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("ECHO", style = EchoTypography.Title.copy(color = EchoColors.Lime, letterSpacing = 4.sp))
            Text(
                text = if (app.scanning) "Scanning your library…" else "${countLabel(app.tracks.size, "track")} · ${countLabel(app.playlists.size, "playlist")}",
                style = EchoTypography.Readout.copy(color = EchoColors.TextMuted),
            )
        }
        SearchField(app.searchQuery, { app.searchQuery = it }, Modifier.weight(1f).widthIn(max = 560.dp).padding(horizontal = EchoSpacing.L).onFocusChanged { app.typing = it.isFocused })
        EchoIconButton(Icons.Filled.FolderOpen, "Music folders", { app.screen = DesktopScreen.MUSIC_FOLDERS })
        EchoIconButton(Icons.Filled.Refresh, "Rescan library", app::rescan)
        EchoIconButton(Icons.Filled.Settings, "Settings", { app.screen = DesktopScreen.SETTINGS })
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = EchoTypography.Body,
        cursorBrush = SolidColor(EchoColors.Lime),
        modifier = modifier.semantics { contentDescription = "Search music" },
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(EchoColors.Charcoal, EchoShapes.Medium)
                    .border(1.dp, EchoColors.Separator, EchoShapes.Medium)
                    .padding(start = EchoSpacing.M),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = EchoColors.TextMuted, modifier = Modifier.size(20.dp))
                Box(modifier = Modifier.weight(1f).padding(horizontal = EchoSpacing.S)) {
                    if (value.isEmpty()) Text("Search music", style = EchoTypography.Body.copy(color = EchoColors.TextMuted))
                    inner()
                }
                if (value.isNotEmpty()) EchoIconButton(Icons.Filled.Close, "Clear search", { onValueChange("") }, size = 44.dp, tint = EchoColors.TextSecondary)
            }
        },
    )
}

/** An artist, album, folder or playlist opened from the library. */
@Composable
private fun DetailPage(
    app: DesktopApp,
    detail: DesktopDetail,
    tracksById: Map<String, Track>,
    currentTrackId: String?,
    playFrom: (List<Track>, Int) -> Unit,
) {
    val tracks = app.tracks
    val playlist = (detail as? DesktopDetail.PlaylistTarget)?.let { d -> app.playlists.firstOrNull { it.id == d.id } }
    val title: String
    val subtitle: String
    var list: List<Track> = emptyList()
    when (detail) {
        is DesktopDetail.Artist -> {
            list = tracks.filter { it.artist == detail.name }
            title = detail.name
            subtitle = countLabel(list.size, "track")
        }
        is DesktopDetail.Album -> {
            list = tracks.filter { it.album == detail.name }.sortedWith(compareBy({ it.trackNumber ?: Int.MAX_VALUE }, { it.title.lowercase() }))
            title = detail.name
            subtitle = countLabel(list.size, "track")
        }
        is DesktopDetail.Folder -> {
            list = tracks.groupedByFolder().firstOrNull { it.name == detail.name }?.tracks.orEmpty().sortedBy { it.title.lowercase() }
            title = FolderPaths.leaf(detail.name)
            subtitle = FolderPaths.breadcrumb(detail.name)
        }
        is DesktopDetail.PlaylistTarget -> {
            title = playlist?.name ?: "Playlist"
            list = playlist?.trackIds?.mapNotNull { tracksById[it] }.orEmpty()
            subtitle = countLabel(playlist?.entries?.size ?: 0, "song")
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = EchoSpacing.Xs, vertical = EchoSpacing.Xs), verticalAlignment = Alignment.CenterVertically) {
            EchoIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", { app.detail = null })
            Column(modifier = Modifier.padding(start = EchoSpacing.S)) {
                Text(title, style = EchoTypography.Heading, maxLines = 1)
                Text(subtitle, style = EchoTypography.Caption, maxLines = 1)
            }
        }
        if (list.isNotEmpty()) PlayAllBar({ app.playAll(list, false) }, { app.playAll(list, true) })
        Box(modifier = Modifier.weight(1f)) {
            if (playlist != null) {
                if (playlist.entries.isEmpty()) {
                    EmptyState("EMPTY PLAYLIST\nUse the + on a song to add it here.")
                } else {
                    PlaylistEntryList(
                        entries = playlist.resolveAgainst(tracksById),
                        currentTrackId = currentTrackId,
                        favoriteIds = app.favorites,
                        onPlay = { track -> playFrom(list, list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)) },
                        onToggleFavorite = app::toggleFavorite,
                        onRemove = { app.removeFromPlaylist(playlist.id, it) },
                    )
                }
            } else if (list.isEmpty()) {
                EmptyState(NO_MATCHES)
            } else {
                PlainTrackList(list, currentTrackId, app.favorites, playFrom, app::toggleFavorite) { app.playlistTarget = it }
            }
        }
    }
}

/** The folders ECHO looks in. Adding one opens the system folder chooser. */
@Composable
fun DesktopMusicFolders(app: DesktopApp) {
    Column(modifier = Modifier.fillMaxSize().background(EchoColors.Black)) {
        Row(modifier = Modifier.fillMaxWidth().padding(EchoSpacing.Xs), verticalAlignment = Alignment.CenterVertically) {
            EchoIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", app::back)
            Text("MUSIC FOLDERS", style = EchoTypography.Heading, modifier = Modifier.padding(start = EchoSpacing.S))
        }
        Centered {
            Column(modifier = Modifier.fillMaxSize().padding(EchoSpacing.L), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(EchoSpacing.M)) {
                Text("ECHO plays the music in these folders, and nothing else. Your files are never changed or uploaded.", style = EchoTypography.Caption)
                for (root in app.roots.toList()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EchoColors.Graphite, EchoShapes.Large)
                            .border(1.dp, EchoColors.Separator, EchoShapes.Large)
                            .padding(start = EchoSpacing.L),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(root, style = EchoTypography.Body, modifier = Modifier.weight(1f), maxLines = 2)
                        EchoIconButton(Icons.Filled.Close, "Remove $root", { app.removeRoot(root) })
                    }
                }
                if (app.roots.isEmpty()) Text("No folder yet. Add the folder your music is in.", style = EchoTypography.Body.copy(color = EchoColors.TextSecondary))
                EchoButton(text = "Add a music folder", onClick = { pickFolder()?.let(app::addRoot) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(EchoSpacing.S))
            }
        }
    }
}

/** The system's own folder chooser, so it looks right on Windows and on Linux. */
fun pickFolder(): String? {
    var result: String? = null
    val chooser = javax.swing.JFileChooser(DesktopPaths.defaultMusicFolder().takeIf { it.isDirectory } ?: java.io.File(System.getProperty("user.home")))
    chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
    chooser.dialogTitle = "Choose your music folder"
    if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) result = chooser.selectedFile?.absolutePath
    return result
}
