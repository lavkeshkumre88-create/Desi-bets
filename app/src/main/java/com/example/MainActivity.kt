package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AuthManager
import com.example.data.FirestoreLibraryRepository
import com.example.data.LocalAudioDataSource
import com.example.data.OfflineDownloadManager
import com.example.data.RemoteSongsDataSource
import com.example.data.authStateFlow
import com.example.model.Song
import com.example.player.PlayerController
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.AdMobBanner
import com.example.ui.components.InterstitialAdManager
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.RemoveAdsDialog
import com.example.ui.components.ServerUrlDialog
import com.example.ui.components.SleepTimerSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.DeepPlumSurface
import com.example.ui.theme.DesiBeatsTheme
import com.example.ui.theme.SaffronGold
import com.example.viewmodel.DesiBeatsViewModel
import com.example.viewmodel.UiState
import com.google.android.gms.ads.MobileAds
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    SEARCH("Search", Icons.Rounded.Search),
    LIBRARY("Library", Icons.Rounded.LibraryMusic),
    PROFILE("Profile", Icons.Rounded.Person),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MobileAds.initialize(this) {}

        setContent {
            DesiBeatsTheme {
                DesiBeatsAppRoot()
            }
        }
    }
}

@Composable
fun DesiBeatsAppRoot() {
    val context = LocalContext.current
    val auth = remember { Firebase.auth }
    val authManager = remember(context) { AuthManager(context) }

    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            authManager.attemptAutoSignIn()
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            authManager = authManager,
            onAuthSuccess = { /* authStateFlow updates automatically */ },
        )
    } else {
        key(user.uid) {
            AuthenticatedDesiBeatsShell(
                user = user,
                authManager = authManager,
            )
        }
    }
}

@Composable
private fun AuthenticatedDesiBeatsShell(
    user: FirebaseUser,
    authManager: AuthManager,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val playerController = remember(context) { PlayerController(context) }
    val interstitialAdManager = remember(context) {
        InterstitialAdManager(context).apply { preload() }
    }

    DisposableEffect(playerController) {
        onDispose {
            playerController.release()
        }
    }

    val libraryRepo = remember(context) {
        val dbId = context.applicationContext.getString(R.string.firestore_database_id)
        FirestoreLibraryRepository(FirebaseFirestore.getInstance(dbId))
    }

    val viewModel: DesiBeatsViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                DesiBeatsViewModel(
                    currentUserId = user.uid,
                    authManager = authManager,
                    libraryRepo = libraryRepo,
                    localAudio = LocalAudioDataSource(app),
                    remoteSongs = RemoteSongsDataSource(app),
                    offlineDownloads = OfflineDownloadManager(app),
                    playerController = playerController,
                )
            }
        },
    )

    LaunchedEffect(user.uid) {
        libraryRepo.ensureUserProfile(user)
    }

    val audioPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onAudioPermissionResult(granted)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Notification controls still work gracefully if denied */ }

    val pickAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importPickedAudioUri(uri)
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            audioPermission,
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onAudioPermissionResult(granted)
        if (!granted) {
            audioPermissionLauncher.launch(audioPermission)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!notifGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val filteredHomeSongs by viewModel.filteredHomeSongs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val hasAudioPermission by viewModel.hasAudioPermission.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadedSongsMap by viewModel.downloadedSongsMap.collectAsStateWithLifecycle()
    val totalOfflineBytes by viewModel.totalOfflineBytes.collectAsStateWithLifecycle()
    val favoritesState by viewModel.favoritesState.collectAsStateWithLifecycle()
    val playlistsState by viewModel.playlistsState.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val selectedPlaylistTracksState by viewModel.selectedPlaylistTracksState.collectAsStateWithLifecycle()
    val userProfileState by viewModel.userProfile.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val activeLyricIndex by viewModel.activeLyricIndex.collectAsStateWithLifecycle()
    val isLyricsLoading by viewModel.isLyricsLoading.collectAsStateWithLifecycle()
    val songsJsonUrl by viewModel.songsJsonUrl.collectAsStateWithLifecycle()
    val adsRemoved by viewModel.adsRemoved.collectAsStateWithLifecycle()

    val favoriteSongs = (favoritesState as? UiState.Success)?.data.orEmpty()
    val favoriteIds = remember(favoriteSongs) { favoriteSongs.map { it.id }.toSet() }
    val playlists = (playlistsState as? UiState.Success)?.data.orEmpty()
    val downloadedSongs = remember(downloadedSongsMap) { downloadedSongsMap.values.toList() }

    var activeTab by remember { mutableStateOf(MainTab.HOME) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showServerUrlDialog by remember { mutableStateOf(false) }
    var showRemoveAdsDialog by remember { mutableStateOf(false) }
    var songForPlaylistDialog by remember { mutableStateOf<Song?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.toastMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(playbackState.playbackError) {
        val err = playbackState.playbackError
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            playerController.clearPlaybackError()
        }
    }

    LaunchedEffect(adsRemoved) {
        playerController.showInterstitialTrigger.collect {
            interstitialAdManager.showIfReady(activity, adsRemoved)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    Column {
                        MiniPlayerBar(
                            playbackState = playbackState,
                            onOpenFullPlayer = { isPlayerExpanded = true },
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onSkipNext = viewModel::skipNext,
                        )
                        if (activeTab == MainTab.HOME) {
                            AdMobBanner(adsRemoved = adsRemoved)
                        }
                        if (!useNavRail) {
                            NavigationBar(
                                containerColor = DeepPlumSurface,
                            ) {
                                MainTab.entries.forEach { tab ->
                                    NavigationBarItem(
                                        selected = activeTab == tab,
                                        onClick = { activeTab = tab },
                                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag("nav_${tab.name.lowercase()}"),
                                    )
                                }
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    if (useNavRail) {
                        NavigationRail(
                            containerColor = DeepPlumSurface,
                            modifier = Modifier.fillMaxHeight(),
                        ) {
                            MainTab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = activeTab == tab,
                                    onClick = { activeTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (activeTab) {
                            MainTab.HOME -> HomeScreen(
                                selectedTab = selectedLanguage,
                                songs = filteredHomeSongs,
                                currentPlayingSongId = playbackState.currentSong?.id,
                                favoriteIds = favoriteIds,
                                downloadProgress = downloadProgress,
                                hasAudioPermission = hasAudioPermission,
                                isRefreshing = isRefreshing,
                                adsRemoved = adsRemoved,
                                songsJsonUrl = songsJsonUrl,
                                onSelectTab = viewModel::selectLanguageTab,
                                onRequestAudioPermission = {
                                    audioPermissionLauncher.launch(audioPermission)
                                },
                                onPickAudioFile = {
                                    pickAudioLauncher.launch(arrayOf("audio/*"))
                                },
                                onConfigureServerUrl = { showServerUrlDialog = true },
                                onOpenRemoveAds = { showRemoveAdsDialog = true },
                                onRefresh = viewModel::refreshAllSongs,
                                onPlaySongs = { queue, index ->
                                    viewModel.playSongs(queue, index)
                                },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onDownloadSong = viewModel::downloadSong,
                                onCancelDownload = viewModel::cancelDownload,
                                onDeleteDownload = viewModel::deleteDownload,
                                onAddToPlaylist = { songForPlaylistDialog = it },
                            )

                            MainTab.SEARCH -> SearchScreen(
                                query = searchQuery,
                                results = searchResults,
                                currentPlayingSongId = playbackState.currentSong?.id,
                                favoriteIds = favoriteIds,
                                downloadProgress = downloadProgress,
                                onQueryChange = viewModel::updateSearchQuery,
                                onPlaySongs = { queue, index ->
                                    viewModel.playSongs(queue, index)
                                },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onDownloadSong = viewModel::downloadSong,
                                onCancelDownload = viewModel::cancelDownload,
                                onDeleteDownload = viewModel::deleteDownload,
                                onAddToPlaylist = { songForPlaylistDialog = it },
                            )

                            MainTab.LIBRARY -> LibraryScreen(
                                playlistsState = playlistsState,
                                selectedPlaylistId = selectedPlaylistId,
                                selectedPlaylistTracksState = selectedPlaylistTracksState,
                                favoritesState = favoritesState,
                                downloadedSongs = downloadedSongs,
                                totalOfflineBytes = totalOfflineBytes,
                                currentPlayingSongId = playbackState.currentSong?.id,
                                favoriteIds = favoriteIds,
                                downloadProgress = downloadProgress,
                                onSelectPlaylist = viewModel::selectPlaylist,
                                onCreatePlaylist = viewModel::createPlaylist,
                                onDeletePlaylist = viewModel::deletePlaylist,
                                onRemoveSongFromPlaylist = viewModel::removeSongFromPlaylist,
                                onPlaySongs = { queue, index ->
                                    viewModel.playSongs(queue, index)
                                },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onDownloadSong = viewModel::downloadSong,
                                onCancelDownload = viewModel::cancelDownload,
                                onDeleteDownload = viewModel::deleteDownload,
                                onClearAllDownloads = viewModel::clearAllDownloads,
                                onAddToPlaylist = { songForPlaylistDialog = it },
                            )

                            MainTab.PROFILE -> ProfileScreen(
                                userProfileState = userProfileState,
                                fallbackName = user.displayName ?: "Desi Beats Listener",
                                fallbackEmail = user.email ?: "",
                                fallbackPhotoUrl = user.photoUrl?.toString(),
                                favoritesCount = favoriteSongs.size,
                                playlistsCount = playlists.size,
                                offlineCount = downloadedSongs.size,
                                songsJsonUrl = songsJsonUrl,
                                adsRemoved = adsRemoved,
                                onUpdateDisplayName = viewModel::updateDisplayName,
                                onOpenServerUrlDialog = { showServerUrlDialog = true },
                                onOpenRemoveAdsDialog = { showRemoveAdsDialog = true },
                                onSignOut = viewModel::signOut,
                            )
                        }
                    }
                }
            }
        }

        // Full-Screen Player & Synced Lyrics Overlay
        AnimatedVisibility(
            visible = isPlayerExpanded && playbackState.currentSong != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            val currentSong = playbackState.currentSong
            if (currentSong != null) {
                PlayerScreen(
                    playbackState = playbackState,
                    lyrics = lyrics,
                    activeLyricIndex = activeLyricIndex,
                    isLyricsLoading = isLyricsLoading,
                    isFavorite = favoriteIds.contains(currentSong.id),
                    downloadProgress = downloadProgress[currentSong.id],
                    adsRemoved = adsRemoved,
                    onCollapse = { isPlayerExpanded = false },
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onSeekTo = viewModel::seekTo,
                    onSkipNext = viewModel::skipNext,
                    onSkipPrevious = viewModel::skipPrevious,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeatMode,
                    onOpenSleepTimer = { showSleepTimerSheet = true },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onDownloadSong = viewModel::downloadSong,
                    onDeleteDownload = viewModel::deleteDownload,
                    onAddToPlaylist = { songForPlaylistDialog = it },
                )
            }
        }
    }

    if (showSleepTimerSheet) {
        SleepTimerSheet(
            currentOption = playbackState.sleepTimerOption,
            remainingMs = playbackState.sleepTimerRemainingMs,
            onSelectOption = viewModel::setSleepTimer,
            onDismiss = { showSleepTimerSheet = false },
        )
    }

    if (showServerUrlDialog) {
        ServerUrlDialog(
            currentUrl = songsJsonUrl,
            onSaveUrl = viewModel::saveSongsJsonUrl,
            onDismiss = { showServerUrlDialog = false },
        )
    }

    if (showRemoveAdsDialog) {
        RemoveAdsDialog(
            adsRemoved = adsRemoved,
            onConfirmToggle = { viewModel.setAdsRemoved(!adsRemoved) },
            onDismiss = { showRemoveAdsDialog = false },
        )
    }

    songForPlaylistDialog?.let { song ->
        AddToPlaylistDialog(
            song = song,
            playlists = playlists,
            onSelectPlaylist = { playlist ->
                viewModel.addSongToPlaylist(playlist.id, song)
                songForPlaylistDialog = null
            },
            onCreatePlaylist = viewModel::createPlaylist,
            onDismiss = { songForPlaylistDialog = null },
        )
    }
}
