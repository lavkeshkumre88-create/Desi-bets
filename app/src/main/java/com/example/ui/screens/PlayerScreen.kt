package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LyricLine
import com.example.model.RepeatModeOption
import com.example.model.SleepTimerOption
import com.example.model.Song
import com.example.model.SongSourceType
import com.example.player.PlaybackUiState
import com.example.ui.components.AdMobBanner
import com.example.ui.components.SongArtwork
import com.example.ui.components.formatDurationMs
import com.example.ui.theme.CoralPulse
import com.example.ui.theme.DeepPlumSurface
import com.example.ui.theme.EmeraldOffline
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.SaffronGold
import com.example.ui.theme.SaffronTextOnPrimary

@Composable
fun PlayerScreen(
    playbackState: PlaybackUiState,
    lyrics: List<LyricLine>,
    activeLyricIndex: Int,
    isLyricsLoading: Boolean,
    isFavorite: Boolean,
    downloadProgress: Float?,
    adsRemoved: Boolean,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onDownloadSong: (Song) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
) {
    val song = playbackState.currentSong ?: return
    var showLyricsView by remember { mutableStateOf(false) }

    BackHandler {
        if (showLyricsView) {
            showLyricsView = false
        } else {
            onCollapse()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ObsidianBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DeepPlumSurface, ObsidianBackground),
                    ),
                )
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Top bar: Collapse, Player/Lyrics toggle, Sleep Timer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.testTag("collapse_player_button"),
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimize player")
                }

                FilterChip(
                    selected = showLyricsView,
                    onClick = { showLyricsView = !showLyricsView },
                    label = { Text(if (showLyricsView) "Now Playing" else "Lyrics") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    modifier = Modifier.testTag("toggle_lyrics_button"),
                )

                IconButton(
                    onClick = onOpenSleepTimer,
                    modifier = Modifier.testTag("sleep_timer_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = "Sleep timer",
                        tint = if (playbackState.sleepTimerOption != SleepTimerOption.OFF) {
                            SaffronGold
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }

            if (playbackState.sleepTimerOption != SleepTimerOption.OFF) {
                val label = if (playbackState.sleepTimerOption == SleepTimerOption.END_OF_TRACK) {
                    "Sleep timer: pausing at end of track"
                } else {
                    "Sleep timer: ${formatDurationMs(playbackState.sleepTimerRemainingMs)}"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = SaffronGold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Main content: Lyrics OR Artwork + Song info
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (showLyricsView) {
                    SyncedLyricsView(
                        song = song,
                        lyrics = lyrics,
                        activeIndex = activeLyricIndex,
                        isLoading = isLyricsLoading,
                        onSeekToLine = onSeekTo,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        SongArtwork(
                            song = song,
                            size = 260.dp,
                            cornerRadius = 24.dp,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text(song.language) },
                        )
                    }
                }
            }

            // Quick Actions Row + Seek Bar + Transport Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onToggleFavorite(song) }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) CoralPulse else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (song.sourceType == SongSourceType.REMOTE_LICENSED) {
                        when {
                            downloadProgress != null -> CircularProgressIndicator(
                                progress = { downloadProgress.coerceIn(0.05f, 1f) },
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = SaffronGold,
                            )

                            song.isDownloaded -> IconButton(onClick = { onDeleteDownload(song.id) }) {
                                Icon(
                                    imageVector = Icons.Rounded.DownloadDone,
                                    contentDescription = "Downloaded offline",
                                    tint = EmeraldOffline,
                                )
                            }

                            else -> IconButton(onClick = { onDownloadSong(song) }) {
                                Icon(
                                    imageVector = Icons.Rounded.DownloadForOffline,
                                    contentDescription = "Download offline",
                                )
                            }
                        }
                    }

                    IconButton(onClick = { onAddToPlaylist(song) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = "Add to playlist",
                        )
                    }
                }

                // Seek bar
                val duration = playbackState.durationMs.coerceAtLeast(1L)
                var isDragging by remember { mutableStateOf(false) }
                var dragValue by remember { mutableFloatStateOf(0f) }
                val sliderPosition = if (isDragging) {
                    dragValue
                } else {
                    (playbackState.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                }

                Slider(
                    value = sliderPosition,
                    onValueChange = {
                        isDragging = true
                        dragValue = it
                    },
                    onValueChangeFinished = {
                        onSeekTo((dragValue * duration).toLong())
                        isDragging = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = SaffronGold,
                        activeTrackColor = SaffronGold,
                    ),
                    modifier = Modifier.testTag("player_seek_slider"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatDurationMs(
                            if (isDragging) (dragValue * duration).toLong() else playbackState.positionMs,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDurationMs(playbackState.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Shuffle, Previous, Play/Pause, Next, Repeat
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.testTag("player_shuffle_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playbackState.shuffleEnabled) {
                                SaffronGold
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }

                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("player_previous_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous song",
                            modifier = Modifier.size(34.dp),
                        )
                    }

                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = SaffronGold,
                            contentColor = SaffronTextOnPrimary,
                        ),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("player_play_pause_button"),
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("player_next_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next song",
                            modifier = Modifier.size(34.dp),
                        )
                    }

                    IconButton(
                        onClick = onCycleRepeat,
                        modifier = Modifier.testTag("player_repeat_button"),
                    ) {
                        val (icon, tint) = when (playbackState.repeatMode) {
                            RepeatModeOption.ONE -> Icons.Rounded.RepeatOne to SaffronGold
                            RepeatModeOption.ALL -> Icons.Rounded.Repeat to SaffronGold
                            RepeatModeOption.OFF -> Icons.Rounded.Repeat to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Repeat mode",
                            tint = tint,
                        )
                    }
                }
            }

            // AdMob Banner at bottom of Player screen
            AdMobBanner(adsRemoved = adsRemoved)
        }
    }
}

@Composable
private fun SyncedLyricsView(
    song: Song,
    lyrics: List<LyricLine>,
    activeIndex: Int,
    isLoading: Boolean,
    onSeekToLine: (Long) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && activeIndex < lyrics.size) {
            listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        Text(
            text = "${song.title} — Lyrics",
            style = MaterialTheme.typography.titleMedium,
            color = SaffronGold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SaffronGold)
            }

            lyrics.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("lyrics_not_available"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Lyrics not available",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("synced_lyrics_list"),
            ) {
                itemsIndexed(lyrics) { index, line ->
                    val isActive = index == activeIndex
                    Text(
                        text = line.text,
                        fontSize = if (isActive) 22.sp else 17.sp,
                        lineHeight = if (isActive) 30.sp else 24.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) {
                            SaffronGold
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSeekToLine(line.timestampMs) }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                    )
                }
            }
        }
    }
}
