package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.OfflineDownloadManager
import com.example.model.Playlist
import com.example.model.Song
import com.example.ui.components.SongArtwork
import com.example.ui.components.SongListItem
import com.example.ui.theme.EmeraldOffline
import com.example.ui.theme.SaffronGold
import com.example.viewmodel.UiState

@Composable
fun LibraryScreen(
    playlistsState: UiState<List<Playlist>>,
    selectedPlaylistId: String?,
    selectedPlaylistTracksState: UiState<List<Song>>,
    favoritesState: UiState<List<Song>>,
    downloadedSongs: List<Song>,
    totalOfflineBytes: Long,
    currentPlayingSongId: String?,
    favoriteIds: Set<String>,
    downloadProgress: Map<String, Float>,
    onSelectPlaylist: (String?) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onRemoveSongFromPlaylist: (String, String) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onDownloadSong: (Song) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onClearAllDownloads: () -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeSubTab by remember { mutableIntStateOf(0) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    val playlists = (playlistsState as? UiState.Success)?.data.orEmpty()
    val selectedPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }

    if (selectedPlaylist != null) {
        BackHandler { onSelectPlaylist(null) }
        PlaylistDetailView(
            playlist = selectedPlaylist,
            tracksState = selectedPlaylistTracksState,
            currentPlayingSongId = currentPlayingSongId,
            favoriteIds = favoriteIds,
            downloadProgress = downloadProgress,
            onBack = { onSelectPlaylist(null) },
            onPlaySongs = onPlaySongs,
            onToggleFavorite = onToggleFavorite,
            onDownloadSong = onDownloadSong,
            onCancelDownload = onCancelDownload,
            onDeleteDownload = onDeleteDownload,
            onRemoveFromPlaylist = { songId -> onRemoveSongFromPlaylist(selectedPlaylist.id, songId) },
            onAddToPlaylist = onAddToPlaylist,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Your Library",
                style = MaterialTheme.typography.headlineMedium,
                color = SaffronGold,
                modifier = Modifier.weight(1f),
            )
            if (activeSubTab == 0) {
                FilledTonalButton(
                    onClick = { showCreatePlaylistDialog = true },
                    modifier = Modifier.testTag("create_playlist_button"),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Playlist")
                }
            }
        }

        val tabs = listOf("Playlists", "Favorites", "Offline")
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = SaffronGold,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeSubTab]),
                    color = SaffronGold,
                )
            },
        ) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = activeSubTab == idx,
                    onClick = { activeSubTab = idx },
                    modifier = Modifier.testTag("library_tab_$title"),
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (activeSubTab == idx) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                )
            }
        }

        when (activeSubTab) {
            0 -> PlaylistsTab(
                playlistsState = playlistsState,
                onSelectPlaylist = { onSelectPlaylist(it.id) },
                onDeletePlaylist = onDeletePlaylist,
            )

            1 -> FavoritesTab(
                favoritesState = favoritesState,
                currentPlayingSongId = currentPlayingSongId,
                favoriteIds = favoriteIds,
                downloadProgress = downloadProgress,
                onPlaySongs = onPlaySongs,
                onToggleFavorite = onToggleFavorite,
                onDownloadSong = onDownloadSong,
                onCancelDownload = onCancelDownload,
                onDeleteDownload = onDeleteDownload,
                onAddToPlaylist = onAddToPlaylist,
            )

            else -> OfflineDownloadsTab(
                downloadedSongs = downloadedSongs,
                totalOfflineBytes = totalOfflineBytes,
                onPlaySongs = onPlaySongs,
                onDeleteDownload = onDeleteDownload,
                onClearAllDownloads = onClearAllDownloads,
            )
        }
    }

    if (showCreatePlaylistDialog) {
        var nameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            onCreatePlaylist(nameInput.trim())
                            showCreatePlaylistDialog = false
                        }
                    },
                    enabled = nameInput.isNotBlank(),
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun PlaylistsTab(
    playlistsState: UiState<List<Playlist>>,
    onSelectPlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (String) -> Unit,
) {
    when (playlistsState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SaffronGold)
        }

        is UiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(playlistsState.message, color = MaterialTheme.colorScheme.error)
        }

        is UiState.Success -> {
            if (playlistsState.data.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No playlists yet",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Tap New Playlist to organize your favorite tracks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(playlistsState.data, key = { it.id }) { playlist ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectPlaylist(playlist) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = SaffronGold,
                                    modifier = Modifier.size(32.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = "${playlist.songCount} ${if (playlist.songCount == 1L) "song" else "songs"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onDeletePlaylist(playlist.id) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete playlist",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailView(
    playlist: Playlist,
    tracksState: UiState<List<Song>>,
    currentPlayingSongId: String?,
    favoriteIds: Set<String>,
    downloadProgress: Map<String, Float>,
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onDownloadSong: (Song) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onRemoveFromPlaylist: (String) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
) {
    val tracks = (tracksState as? UiState.Success)?.data.orEmpty()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to playlists")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${tracks.size} tracks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tracks.isNotEmpty()) {
                FilledTonalButton(onClick = { onPlaySongs(tracks, 0) }) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play All")
                }
            }
        }

        when (tracksState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SaffronGold)
            }

            is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(tracksState.message, color = MaterialTheme.colorScheme.error)
            }

            is UiState.Success -> {
                if (tracks.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No songs in this playlist yet.\nTap the playlist icon on any song to add it.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        itemsIndexed(tracks, key = { _, song -> song.id }) { index, song ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1f)) {
                                    SongListItem(
                                        song = song,
                                        isCurrentlyPlaying = song.id == currentPlayingSongId,
                                        isFavorite = favoriteIds.contains(song.id),
                                        downloadProgress = downloadProgress[song.id],
                                        onPlay = { onPlaySongs(tracks, index) },
                                        onToggleFavorite = { onToggleFavorite(song) },
                                        onDownloadClick = { onDownloadSong(song) },
                                        onCancelDownload = { onCancelDownload(song.id) },
                                        onDeleteDownload = { onDeleteDownload(song.id) },
                                        onAddToPlaylist = { onAddToPlaylist(song) },
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveFromPlaylist(song.id) },
                                    modifier = Modifier.padding(end = 8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Remove from playlist",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesTab(
    favoritesState: UiState<List<Song>>,
    currentPlayingSongId: String?,
    favoriteIds: Set<String>,
    downloadProgress: Map<String, Float>,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onDownloadSong: (Song) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
) {
    when (favoritesState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SaffronGold)
        }

        is UiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(favoritesState.message, color = MaterialTheme.colorScheme.error)
        }

        is UiState.Success -> {
            if (favoritesState.data.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No favorites yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap the heart on any song to save it here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    itemsIndexed(favoritesState.data, key = { _, song -> song.id }) { index, song ->
                        SongListItem(
                            song = song,
                            isCurrentlyPlaying = song.id == currentPlayingSongId,
                            isFavorite = favoriteIds.contains(song.id),
                            downloadProgress = downloadProgress[song.id],
                            onPlay = { onPlaySongs(favoritesState.data, index) },
                            onToggleFavorite = { onToggleFavorite(song) },
                            onDownloadClick = { onDownloadSong(song) },
                            onCancelDownload = { onCancelDownload(song.id) },
                            onDeleteDownload = { onDeleteDownload(song.id) },
                            onAddToPlaylist = { onAddToPlaylist(song) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineDownloadsTab(
    downloadedSongs: List<Song>,
    totalOfflineBytes: Long,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onClearAllDownloads: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = EmeraldOffline,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = OfflineDownloadManager.formatBytes(totalOfflineBytes),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "${downloadedSongs.size} ${if (downloadedSongs.size == 1) "song" else "songs"} saved offline",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (downloadedSongs.isNotEmpty()) {
                    TextButton(onClick = onClearAllDownloads) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All")
                    }
                }
            }
        }

        if (downloadedSongs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.OfflinePin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No offline downloads yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Download licensed server songs from Home or Search to play without internet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(downloadedSongs, key = { _, song -> song.id }) { index, song ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 5.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPlaySongs(downloadedSongs, index) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SongArtwork(song = song, size = 48.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${song.artist} • ${OfflineDownloadManager.formatBytes(song.downloadedBytes)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onDeleteDownload(song.id) }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete offline song",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
