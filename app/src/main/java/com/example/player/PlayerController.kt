package com.example.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.config.AppConfig
import com.example.model.RepeatModeOption
import com.example.model.SleepTimerOption
import com.example.model.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackUiState(
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeOption = RepeatModeOption.OFF,
    val sleepTimerOption: SleepTimerOption = SleepTimerOption.OFF,
    val sleepTimerRemainingMs: Long = 0L,
    val playbackError: String? = null,
)

class PlayerController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var positionJob: Job? = null
    private var sleepTimerJob: Job? = null

    private var currentQueue: List<Song> = emptyList()
    private var songsPlayedCounter = 0

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val _showInterstitialTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showInterstitialTrigger: SharedFlow<Unit> = _showInterstitialTrigger.asSharedFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val controller = mediaController ?: return
            val index = controller.currentMediaItemIndex
            val newSong = currentQueue.getOrNull(index)
            _uiState.update {
                it.copy(
                    currentSong = newSong,
                    positionMs = 0L,
                    durationMs = controller.duration.coerceAtLeast(newSong?.durationMs ?: 0L),
                    playbackError = null,
                )
            }

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                if (_uiState.value.sleepTimerOption == SleepTimerOption.END_OF_TRACK) {
                    controller.pause()
                    cancelSleepTimer()
                    return
                }
                if (newSong != null) {
                    incrementSongPlayCounter()
                }
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _uiState.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val mapped = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatModeOption.ONE
                Player.REPEAT_MODE_ALL -> RepeatModeOption.ALL
                else -> RepeatModeOption.OFF
            }
            _uiState.update { it.copy(repeatMode = mapped) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val controller = mediaController ?: return
            if (playbackState == Player.STATE_READY) {
                _uiState.update {
                    it.copy(
                        durationMs = controller.duration.coerceAtLeast(it.currentSong?.durationMs ?: 0L),
                    )
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    playbackError = error.localizedMessage ?: "Unable to play this track.",
                )
            }
        }
    }

    init {
        connectController()
    }

    private fun connectController() {
        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, DesiBeatsPlaybackService::class.java),
        )
        val future = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    mediaController = controller
                    controller.addListener(playerListener)
                    startPositionUpdater()
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    private fun startPositionUpdater() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.coerceAtLeast(_uiState.value.currentSong?.durationMs ?: 0L)
                    _uiState.update { it.copy(positionMs = pos, durationMs = dur) }
                }
                delay(400L)
            }
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        currentQueue = songs

        val controller = mediaController
        if (controller == null) {
            connectController()
            return
        }

        val mediaItems = songs.map { buildMediaItem(it) }
        controller.setMediaItems(mediaItems, safeIndex, 0L)
        controller.prepare()
        controller.play()

        val selectedSong = songs[safeIndex]
        _uiState.update {
            it.copy(
                currentSong = selectedSong,
                queue = songs,
                isPlaying = true,
                positionMs = 0L,
                durationMs = selectedSong.durationMs,
                playbackError = null,
            )
        }

        incrementSongPlayCounter()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs.coerceAtLeast(0L))
        _uiState.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun skipNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            incrementSongPlayCounter()
        }
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3_000L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else {
            controller.seekTo(0L)
        }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSleepTimer(option: SleepTimerOption) {
        sleepTimerJob?.cancel()
        if (option == SleepTimerOption.OFF) {
            _uiState.update {
                it.copy(sleepTimerOption = SleepTimerOption.OFF, sleepTimerRemainingMs = 0L)
            }
            return
        }

        if (option == SleepTimerOption.END_OF_TRACK) {
            _uiState.update {
                it.copy(sleepTimerOption = SleepTimerOption.END_OF_TRACK, sleepTimerRemainingMs = 0L)
            }
            return
        }

        val duration = option.durationMs ?: return
        _uiState.update {
            it.copy(sleepTimerOption = option, sleepTimerRemainingMs = duration)
        }
        sleepTimerJob = scope.launch {
            var remaining = duration
            while (remaining > 0L && isActive) {
                delay(1000L)
                remaining -= 1000L
                _uiState.update { it.copy(sleepTimerRemainingMs = remaining.coerceAtLeast(0L)) }
            }
            mediaController?.pause()
            _uiState.update {
                it.copy(sleepTimerOption = SleepTimerOption.OFF, sleepTimerRemainingMs = 0L)
            }
        }
    }

    fun cancelSleepTimer() {
        setSleepTimer(SleepTimerOption.OFF)
    }

    fun clearPlaybackError() {
        _uiState.update { it.copy(playbackError = null) }
    }

    private fun incrementSongPlayCounter() {
        songsPlayedCounter += 1
        if (songsPlayedCounter % AppConfig.INTERSTITIAL_EVERY_N_SONGS == 0) {
            _showInterstitialTrigger.tryEmit(Unit)
        }
    }

    private fun buildMediaItem(song: Song): MediaItem {
        val artworkUri = when {
            !song.localCoverPath.isNullOrBlank() && File(song.localCoverPath).exists() ->
                Uri.fromFile(File(song.localCoverPath))
            !song.coverUrl.isNullOrBlank() -> Uri.parse(song.coverUrl)
            else -> null
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album ?: song.language)
            .setArtworkUri(artworkUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(song.effectiveAudioUri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun release() {
        positionJob?.cancel()
        sleepTimerJob?.cancel()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }
}
