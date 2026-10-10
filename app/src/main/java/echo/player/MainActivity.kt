package echo.player

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import echo.player.design.EchoColors
import echo.player.design.EchoShapes
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography
import echo.player.domain.PlaylistLogic
import echo.player.ui.library.PlaylistChooserDialog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import echo.player.design.EchoMotion
import echo.player.design.EchoTheme
import echo.player.domain.MusicRoot
import echo.player.domain.Screen
import echo.player.platform.android.MusicRootManager
import echo.player.ui.EchoViewModel
import echo.player.ui.audioPermission
import echo.player.ui.discover.DiscoverScreen
import echo.player.ui.folders.MusicFoldersScreen
import echo.player.ui.folders.MusicSetupScreen
import echo.player.ui.library.LibraryScreen
import echo.player.ui.library.PermissionScreen
import echo.player.ui.nowplaying.NowPlayingScreen
import echo.player.ui.settings.SettingsScreen
import echo.player.ui.welcome.WelcomeHints
import echo.player.widget.EchoWidgetUpdater

/** A request from outside the app (a widget tap) to land on Now Playing. It is consumed once by the UI. */
object LaunchRequests {
    var openNowPlaying by mutableStateOf(false)
}

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleLaunchIntent(intent)
        setContent {
            EchoTheme {
                EchoApp()
            }
        }
    }
}

private fun MainActivity.handleLaunchIntent(intent: Intent?) {
    if (intent?.getBooleanExtra(EchoWidgetUpdater.EXTRA_OPEN_NOW_PLAYING, false) == true) LaunchRequests.openNowPlaying = true
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun EchoApp() {
    val viewModel: EchoViewModel = viewModel()
    val context = LocalContext.current
    val permission = audioPermission()

    // Permission is Compose state (not re-read from the system on every recomposition) so a grant,
    // a return from App Settings, or a revoke all change what is on screen.
    var hasPermission by remember { mutableStateOf(viewModel.hasPermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            val canAskAgain = context.findActivity()
                ?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, permission) }
                ?: true
            viewModel.handlePermissionResult(granted, canAskAgain)
            hasPermission = granted
        },
    )

    // Android 13+ needs this for the playback notification and lock-screen controls. Asked once, the
    // first time the user starts playback; playback itself never waits for the answer.
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { },
    )
    val onPlaybackRequested = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !viewModel.askedForNotificationPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.askedForNotificationPermission = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // The system folder picker (ACTION_OPEN_DOCUMENT_TREE). When it was opened to fix a revoked or missing
    // folder, pendingReselect remembers which one the new pick replaces.
    var pendingReselect by remember { mutableStateOf<MusicRoot?>(null) }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            val replacing = pendingReselect
            pendingReselect = null
            if (uri != null) {
                if (replacing != null) viewModel.reselectMusicFolder(replacing, uri) else viewModel.addMusicFolder(uri)
            }
        },
    )
    val pickFolder = {
        pendingReselect = null
        folderPicker.launch(MusicRootManager.musicFolderPickerStart())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = viewModel.hasPermission()
                if (granted != hasPermission) hasPermission = granted
                viewModel.onPermissionStateChanged(granted)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            PermissionScreen(
                prompt = viewModel.permissionPrompt,
                onAllow = { permissionLauncher.launch(permission) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null)),
                    )
                },
            )
            // A fresh install sees the welcome first, before it is asked for anything.
            if (viewModel.showWelcome) WelcomeHints(onDone = viewModel::dismissWelcome)
        }
        return
    }

    // Back unwinds the full-screen destinations first (queue, then Now Playing / folders), then the
    // library's own drill-downs, then leaves the app.
    BackHandler(enabled = viewModel.nav.canGoBack) { viewModel.navigateBack() }

    // A widget tap asks for Now Playing; it applies once the library UI is up (and only if something is loaded).
    val openRequest = LaunchRequests.openNowPlaying
    LaunchedEffect(openRequest, hasPermission) {
        if (openRequest && hasPermission) {
            LaunchRequests.openNowPlaying = false // consumed: a rotation must not reopen it
            viewModel.openNowPlaying()
        }
    }

    val screen = viewModel.nav.current

    // Once a full-screen destination has finished sliding in, the library underneath is no longer painted:
    // it stays composed (its scroll position is kept) but costs the renderer nothing. Painting resumes the
    // moment the user heads back to it, before the destination starts to slide away.
    var libraryPainted by remember { mutableStateOf(true) }
    LaunchedEffect(screen) {
        if (screen == Screen.LIBRARY) {
            libraryPainted = true
        } else {
            delay(EchoMotion.Slow + 150L)
            libraryPainted = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // While a full-screen destination covers the library it is hidden from accessibility services, so a
        // screen reader cannot wander into covered content.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent { if (libraryPainted) drawContent() }
                .then(if (screen != Screen.LIBRARY) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            if (viewModel.needsMusicSetup) {
                MusicSetupScreen(onUseDefaultFolder = viewModel::useDefaultMusicFolder, onAddFolder = pickFolder)
            } else {
                LibraryScreen(
                    viewModel = viewModel,
                    onRescan = viewModel::refreshLibrary,
                    onPlaybackRequested = onPlaybackRequested,
                )
            }
        }

        AnimatedVisibility(
            visible = screen == Screen.NOW_PLAYING,
            enter = slideInVertically(tween(EchoMotion.Slow, easing = EchoMotion.Emphasized)) { it / 3 } + fadeIn(tween(EchoMotion.Medium)),
            exit = slideOutVertically(tween(EchoMotion.Medium, easing = EchoMotion.Standard)) { it / 3 } + fadeOut(tween(EchoMotion.Medium)),
        ) {
            NowPlayingScreen(
                controls = viewModel.playback,
                favoriteIds = viewModel.favorites,
                engine = viewModel.visualizer,
                mode = viewModel.visualizerMode,
                showQueue = viewModel.showQueue,
                companionEnabled = viewModel.showCompanion,
                sleepTimer = viewModel.sleepTimer,
                onModeChange = viewModel::selectVisualizerMode,
                onToggleQueue = { viewModel.showQueue = !viewModel.showQueue },
                onToggleFavorite = viewModel::toggleFavoriteById,
                onAddToPlaylist = viewModel::addCurrentToPlaylist,
                onClose = { viewModel.navigateBack() },
            )
        }

        AnimatedVisibility(
            visible = screen == Screen.MUSIC_FOLDERS,
            enter = slideInVertically(tween(EchoMotion.Slow, easing = EchoMotion.Emphasized)) { it / 4 } + fadeIn(tween(EchoMotion.Medium)),
            exit = slideOutVertically(tween(EchoMotion.Medium, easing = EchoMotion.Standard)) { it / 4 } + fadeOut(tween(EchoMotion.Medium)),
        ) {
            MusicFoldersScreen(
                folders = viewModel.musicFolders,
                message = viewModel.folderMessage,
                onBack = { viewModel.navigateBack() },
                onAddFolder = pickFolder,
                onReselect = { root ->
                    pendingReselect = root
                    folderPicker.launch(root.treeUri?.let(Uri::parse) ?: MusicRootManager.musicFolderPickerStart())
                },
                onRemove = viewModel::removeMusicFolder,
                onRescan = viewModel::refreshLibrary,
                onUseDefaultFolder = viewModel::useDefaultMusicFolder,
            )
        }

        AnimatedVisibility(
            visible = screen == Screen.DISCOVER,
            enter = slideInVertically(tween(EchoMotion.Slow, easing = EchoMotion.Emphasized)) { it / 4 } + fadeIn(tween(EchoMotion.Medium)),
            exit = slideOutVertically(tween(EchoMotion.Medium, easing = EchoMotion.Standard)) { it / 4 } + fadeOut(tween(EchoMotion.Medium)),
        ) {
            DiscoverScreen(
                viewModel = viewModel,
                onPlaybackRequested = onPlaybackRequested,
                onBack = { viewModel.navigateBack() },
            )
        }

        AnimatedVisibility(
            visible = screen == Screen.SETTINGS,
            enter = slideInVertically(tween(EchoMotion.Slow, easing = EchoMotion.Emphasized)) { it / 4 } + fadeIn(tween(EchoMotion.Medium)),
            exit = slideOutVertically(tween(EchoMotion.Medium, easing = EchoMotion.Standard)) { it / 4 } + fadeOut(tween(EchoMotion.Medium)),
        ) {
            SettingsScreen(
                showCompanion = viewModel.showCompanion,
                onShowCompanionChange = viewModel::updateShowCompanion,
                onShowWelcome = viewModel::showWelcomeAgain,
                onBack = { viewModel.navigateBack() },
            )
        }

        // The playlist chooser and its confirmation live here so the "+" works from the library and from Now Playing.
        PlaylistOverlays(viewModel)

        if (viewModel.showWelcome) WelcomeHints(onDone = viewModel::dismissWelcome)
    }
}

/** The "Add to playlist" chooser and the short confirmation bar, shared by every screen. */
@Composable
private fun BoxScope.PlaylistOverlays(viewModel: EchoViewModel) {
    viewModel.playlistTarget?.let { track ->
        PlaylistChooserDialog(
            songTitle = track.title,
            choices = PlaylistLogic.choices(viewModel.playlists, track.id),
            onDismiss = { viewModel.playlistTarget = null },
            onPick = { playlistId ->
                viewModel.addTrackToPlaylist(playlistId, track)
                viewModel.playlistTarget = null
            },
            onCreate = { name ->
                viewModel.createPlaylistAndAdd(name, track)
                viewModel.playlistTarget = null
            },
        )
    }
    viewModel.playlistMessage?.let { message ->
        LaunchedEffect(message) {
            delay(2500)
            if (viewModel.playlistMessage == message) viewModel.playlistMessage = null
        }
        Text(
            text = message,
            style = EchoTypography.Label.copy(color = EchoColors.OnLime),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = EchoSpacing.S)
                .background(EchoColors.Lime, EchoShapes.Small)
                .padding(horizontal = EchoSpacing.L, vertical = EchoSpacing.S),
        )
    }
}
