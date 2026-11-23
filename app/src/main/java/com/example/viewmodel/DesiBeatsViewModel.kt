package com.example.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.AppConfig
import com.example.data.AuthManager
import com.example.data.FirestoreLibraryRepository
import com.example.data.LocalAudioDataSource
import com.example.data.OfflineDownloadManager
import com.example.data.RemoteSongsDataSource
import com.example.model.LyricLine
import com.example.model.Playlist
import com.example.model.SleepTimerOption
import com.example.model.Song
import com.example.model.UserProfile
import com.example.player.PlaybackUiState
import com.example.player.PlayerController
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class DesiBeatsViewModel(
    private val currentUserId: String,
    private val authManager: AuthManager,
    private val libraryRepo: FirestoreLibraryRepository,
    private val localAudio: LocalAudioDataSource,
    private val remoteSongs: RemoteSongsDataSource,
    private val offlineDownloads: OfflineDownloadManager,
    val playerController: PlayerController,
) : ViewModel() {

    private val _selectedLanguage = MutableStateFlow(AppConfig.LANGUAGE_TABS.first())
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _hasAudioPermission = MutableStateFlow(false)
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()

    private val _localSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _importedSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _serverSongs = MutableStateFlow<List<Song>>(emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _selectedPlaylistId = MutableStateFlow<String?>(null)
    val selectedPlaylistId: StateFlow<String?> = _selectedPlaylistId.asStateFlow()

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()

    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toastMessages: SharedFlow<String> = _toastMessages.asSharedFlow()

    val playbackState: StateFlow<PlaybackUiState> = playerController.uiState
    val downloadProgress: StateFlow<Map<String, Float>> = offlineDownloads.downloadProgress
    val downloadedSongsMap: StateFlow<Map<String, Song>> = offlineDownloads.downloadedSongsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())
    val totalOfflineBytes: StateFlow<Long> = offlineDownloads.totalStorageBytesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    val songsJsonUrl: StateFlow<String> = remoteSongs.songsJsonUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    val adsRemoved: StateFlow<Boolean> = remoteSongs.adsRemovedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val userProfile: StateFlow<UiState<UserProfile?>> = libraryRepo.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            emit(UiState.Error((fsError ?: error).message ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val favoritesState: StateFlow<UiState<List<Song>>> = combine(
        libraryRepo.observeFavorites(currentUserId)
            .map<List<Song>, UiState<List<Song>>> { UiState.Success(it) }
            .catch { error ->
                val fsError = unwrapFirestoreError(error)
                if (fsError == null && error is CancellationException) throw error
                emit(UiState.Error((fsError ?: error).message ?: "Failed to load favorites"))
            },
        downloadedSongsMap,
    ) { favState, offlineMap ->
        when (favState) {
            is UiState.Success -> UiState.Success(favState.data.map { offlineMap[it.id] ?: it })
            else -> favState
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val playlistsState: StateFlow<UiState<List<Playlist>>> = libraryRepo.observePlaylists(currentUserId)
        .map<List<Playlist>, UiState<List<Playlist>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            emit(UiState.Error((fsError ?: error).message ?: "Failed to load playlists"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val selectedPlaylistTracksState: StateFlow<UiState<List<Song>>> = combine(
        _selectedPlaylistId.flatMapLatest { playlistId ->
            if (playlistId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                libraryRepo.observePlaylistTracks(currentUserId, playlistId)
                    .map<List<Song>, UiState<List<Song>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsError = unwrapFirestoreError(error)
                        if (fsError == null && error is CancellationException) throw error
                        emit(UiState.Error((fsError ?: error).message ?: "Failed to load tracks"))
                    }
            }
        },
        downloadedSongsMap,
    ) { tracksState, offlineMap ->
        when (tracksState) {
            is UiState.Success -> UiState.Success(tracksState.data.map { offlineMap[it.id] ?: it })
            else -> tracksState
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))

    val allMergedSongs: StateFlow<List<Song>> = combine(
        _serverSongs,
        _localSongs,
        _importedSongs,
        downloadedSongsMap,
    ) { server, local, imported, offlineMap ->
        val map = LinkedHashMap<String, Song>()
        (server + local + imported).forEach { song ->
            map[song.id] = offlineMap[song.id] ?: song
        }
        offlineMap.values.forEach { downloaded ->
            map.putIfAbsent(downloaded.id, downloaded)
        }
        map.values.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val filteredHomeSongs: StateFlow<List<Song>> = combine(
        allMergedSongs,
        _selectedLanguage,
    ) { songs, tab ->
        if (tab == "All") songs
        else songs.filter { it.language.equals(tab, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val searchResults: StateFlow<List<Song>> = combine(
        allMergedSongs,
        _searchQuery,
    ) { songs, query ->
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) songs
        else songs.filter {
            it.title.lowercase().contains(trimmed) ||
                it.artist.lowercase().contains(trimmed) ||
                it.language.lowercase().contains(trimmed)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeLyricIndex: StateFlow<Int> = combine(
        _lyrics,
        playbackState.map { it.positionMs },
    ) { lines, positionMs ->
        if (lines.isEmpty()) -1
        else {
            var active = -1
            for (i in lines.indices) {
                if (positionMs >= lines[i].timestampMs) active = i
                else break
            }
            active
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), -1)

    init {
        refreshAllSongs()
        observeCurrentSongLyrics()
    }

    private fun observeCurrentSongLyrics() {
        viewModelScope.launch {
            playbackState
                .map { it.currentSong }
                .distinctUntilChanged { a, b -> a?.id == b?.id && a?.localLrcPath == b?.localLrcPath }
                .collect { song ->
                    if (song == null) {
                        _lyrics.value = emptyList()
                        _isLyricsLoading.value = false
                    } else {
                        _isLyricsLoading.value = true
                        _lyrics.value = remoteSongs.loadLyricsForSong(song)
                        _isLyricsLoading.value = false
                    }
                }
        }
    }

    fun onAudioPermissionResult(granted: Boolean) {
        _hasAudioPermission.value = granted
        if (granted) {
            viewModelScope.launch {
                _localSongs.value = localAudio.loadDeviceSongs()
            }
        }
    }

    fun refreshAllSongs() {
        viewModelScope.launch {
            _isRefreshing.value = true
            if (_hasAudioPermission.value) {
                _localSongs.value = localAudio.loadDeviceSongs()
            }
            val url = remoteSongs.songsJsonUrlFlow.first()
            if (url.isNotBlank()) {
                val res = remoteSongs.fetchServerSongs(url)
                res.onSuccess { _serverSongs.value = it }
                    .onFailure {
                        _toastMessages.tryEmit(it.localizedMessage ?: "Failed to load songs.json")
                    }
            } else {
                _serverSongs.value = emptyList()
            }
            _isRefreshing.value = false
        }
    }

    fun saveSongsJsonUrl(url: String) {
        viewModelScope.launch {
            remoteSongs.setSongsJsonUrl(url)
            if (url.isBlank()) {
                _serverSongs.value = emptyList()
                _toastMessages.tryEmit("Server catalog URL cleared")
            } else {
                _isRefreshing.value = true
                val res = remoteSongs.fetchServerSongs(url)
                res.onSuccess { list ->
                    _serverSongs.value = list
                    _toastMessages.tryEmit("Loaded ${list.size} licensed songs")
                }.onFailure {
                    _toastMessages.tryEmit(it.localizedMessage ?: "Could not load songs.json")
                }
                _isRefreshing.value = false
            }
        }
    }

    fun importPickedAudioUri(uri: Uri) {
        viewModelScope.launch {
            val song = localAudio.songFromPickedUri(uri)
            if (song != null) {
                _importedSongs.update { current ->
                    (listOf(song) + current).distinctBy { it.id }
                }
                playerController.playQueue(listOf(song), 0)
                _toastMessages.tryEmit("Playing ${song.title}")
            }
        }
    }

    fun selectLanguageTab(tab: String) {
        _selectedLanguage.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun playSongs(queue: List<Song>, startIndex: Int) {
        val offlineMap = downloadedSongsMap.value
        val resolvedQueue = queue.map { offlineMap[it.id] ?: it }
        playerController.playQueue(resolvedQueue, startIndex)
    }

    fun togglePlayPause() = playerController.togglePlayPause()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)
    fun skipNext() = playerController.skipNext()
    fun skipPrevious() = playerController.skipPrevious()
    fun toggleShuffle() = playerController.toggleShuffle()
    fun cycleRepeatMode() = playerController.cycleRepeatMode()
    fun setSleepTimer(option: SleepTimerOption) = playerController.setSleepTimer(option)

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val favs = (favoritesState.value as? UiState.Success)?.data.orEmpty()
            val safeId = FirestoreLibraryRepository.sanitizeFirestoreId(song.id)
            val isFav = favs.any { it.id == safeId || it.id == song.id }
            val result = libraryRepo.toggleFavorite(currentUserId, song, isFav)
            result.onSuccess {
                _toastMessages.tryEmit(if (isFav) "Removed from Favorites" else "Added to Favorites")
            }.onFailure {
                _toastMessages.tryEmit(it.localizedMessage ?: "Could not update favorite")
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val result = libraryRepo.createPlaylist(currentUserId, name)
            result.onSuccess {
                _toastMessages.tryEmit("Playlist \"$name\" created")
            }.onFailure {
                _toastMessages.tryEmit(it.localizedMessage ?: "Could not create playlist")
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
            val result = libraryRepo.deletePlaylist(currentUserId, playlistId)
            result.onFailure {
                _toastMessages.tryEmit(it.localizedMessage ?: "Could not delete playlist")
            }
        }
    }

    fun selectPlaylist(playlistId: String?) {
        _selectedPlaylistId.value = playlistId
    }

    fun addSongToPlaylist(playlistId: String, song: Song) {
        viewModelScope.launch {
            val result = libraryRepo.addSongToPlaylist(currentUserId, playlistId, song)
            result.onSuccess {
                _toastMessages.tryEmit("Added \"${song.title}\" to playlist")
            }.onFailure {
                _toastMessages.tryEmit(it.localizedMessage ?: "Could not add song to playlist")
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            val result = libraryRepo.removeSongFromPlaylist(currentUserId, playlistId, songId)
            result.onFailure {
                _toastMessages.tryEmit(it.localizedMessage ?: "Could not remove song")
            }
        }
    }

    fun downloadSong(song: Song) {
        offlineDownloads.startDownload(song) { errMsg ->
            _toastMessages.tryEmit(errMsg)
        }
        _toastMessages.tryEmit("Downloading \"${song.title}\"...")
    }

    fun cancelDownload(songId: String) {
        offlineDownloads.cancelDownload(songId)
    }

    fun deleteDownload(songId: String) {
        viewModelScope.launch {
            offlineDownloads.deleteDownload(songId)
            _toastMessages.tryEmit("Removed offline copy")
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            offlineDownloads.clearAllDownloads()
            _toastMessages.tryEmit("Cleared all offline downloads")
        }
    }

    fun updateDisplayName(newName: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val fsResult = libraryRepo.updateDisplayName(currentUserId, newName)
            if (fsResult.isSuccess) {
                authManager.updateFirebaseDisplayName(newName)
                _toastMessages.tryEmit("Display name updated")
                onDone(true)
            } else {
                _toastMessages.tryEmit(
                    fsResult.exceptionOrNull()?.localizedMessage ?: "Failed to update name",
                )
                onDone(false)
            }
        }
    }

    fun setAdsRemoved(removed: Boolean) {
        viewModelScope.launch {
            remoteSongs.setAdsRemoved(removed)
            _toastMessages.tryEmit(
                if (removed) "Ad-Free preview enabled" else "Ads re-enabled",
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
            ?.also { Log.w("DesiBeatsVM", "Firestore stream error", it) }
    }
}
