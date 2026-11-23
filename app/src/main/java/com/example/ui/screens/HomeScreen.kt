package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.config.AppConfig
import com.example.model.Song
import com.example.ui.components.SongListItem
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.SaffronGold

@Composable
fun HomeScreen(
    selectedTab: String,
    songs: List<Song>,
    currentPlayingSongId: String?,
    favoriteIds: Set<String>,
    downloadProgress: Map<String, Float>,
    hasAudioPermission: Boolean,
    isRefreshing: Boolean,
    adsRemoved: Boolean,
    songsJsonUrl: String,
    onSelectTab: (String) -> Unit,
    onRequestAudioPermission: () -> Unit,
    onPickAudioFile: () -> Unit,
    onConfigureServerUrl: () -> Unit,
    onOpenRemoveAds: () -> Unit,
    onRefresh: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onDownloadSong: (Song) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = AppConfig.LANGUAGE_TABS.indexOf(selectedTab).coerceAtLeast(0)

    Column(modifier = modifier.fillMaxSize()) {
        // Hero Banner Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(18.dp)),
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_desi_beats_1795412770868),
                contentDescription = "Desi Beats banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                ObsidianBackground.copy(alpha = 0.92f),
                                ObsidianBackground.copy(alpha = 0.45f),
                            ),
                        ),
                    )
                    .padding(16.dp),
            ) {
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    Text(
                        text = "Desi Beats",
                        style = MaterialTheme.typography.headlineMedium,
                        color = SaffronGold,
                    )
                    Text(
                        text = "${songs.size} tracks in $selectedTab",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = onOpenRemoveAds,
                            label = {
                                Text(if (adsRemoved) "Ad-Free" else "Remove Ads")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.WorkspacePremium,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                labelColor = SaffronGold,
                                leadingIconContentColor = SaffronGold,
                            ),
                            modifier = Modifier.testTag("remove_ads_chip"),
                        )

                        AssistChip(
                            onClick = onConfigureServerUrl,
                            label = {
                                Text(if (songsJsonUrl.isBlank()) "Add songs.json" else "Server Synced")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.CloudSync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            modifier = Modifier.testTag("configure_server_chip"),
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .testTag("refresh_songs_button"),
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = SaffronGold,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh songs",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // Language Filter Tabs: Hindi, Punjabi, Haryanvi, Telugu, Rajasthani, All
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = SaffronGold,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    color = SaffronGold,
                )
            },
            modifier = Modifier.testTag("language_tab_row"),
        ) {
            AppConfig.LANGUAGE_TABS.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.testTag("tab_$tab"),
                    text = {
                        Text(
                            text = tab,
                            fontWeight = if (tab == selectedTab) FontWeight.Bold else FontWeight.Medium,
                            color = if (tab == selectedTab) SaffronGold else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }

        // Permission banner if local device audio permission is not granted
        if (!hasAudioPermission) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = SaffronGold,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Allow audio access to scan music stored on your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onRequestAudioPermission,
                        modifier = Modifier.testTag("allow_audio_permission_button"),
                    ) {
                        Text("Allow")
                    }
                }
            }
        }

        // Song List
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = SaffronGold.copy(alpha = 0.7f),
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No $selectedTab songs yet",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Add audio files on your phone, pick a local audio file, or connect your legal songs.json server URL.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = onPickAudioFile,
                            modifier = Modifier.testTag("pick_local_audio_button"),
                        ) {
                            Icon(Icons.Rounded.AudioFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick Audio")
                        }
                        OutlinedButton(
                            onClick = onConfigureServerUrl,
                            modifier = Modifier.testTag("empty_configure_server_button"),
                        ) {
                            Text("songs.json URL")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            ) {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    SongListItem(
                        song = song,
                        isCurrentlyPlaying = song.id == currentPlayingSongId,
                        isFavorite = favoriteIds.contains(song.id),
                        downloadProgress = downloadProgress[song.id],
                        onPlay = { onPlaySongs(songs, index) },
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
