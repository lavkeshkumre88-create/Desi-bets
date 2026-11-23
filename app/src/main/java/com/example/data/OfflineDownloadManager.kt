package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.Song
import com.example.model.SongSourceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private val Context.offlineDataStore by preferencesDataStore(name = "desi_beats_offline")

class OfflineDownloadManager(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val indexKey = stringPreferencesKey("offline_songs_index_v1")
    private val offlineDir = File(appContext.filesDir, "desi_beats_offline").apply {
        if (!exists()) mkdirs()
    }

    private val activeJobs = mutableMapOf<String, Job>()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    val downloadedSongsFlow: Flow<Map<String, Song>> = appContext.offlineDataStore.data.map { prefs ->
        parseIndex(prefs[indexKey].orEmpty())
            .filterValues { song ->
                !song.localAudioPath.isNullOrBlank() && File(song.localAudioPath).exists()
            }
    }

    val totalStorageBytesFlow: Flow<Long> = downloadedSongsFlow.map { songs ->
        songs.values.sumOf { it.downloadedBytes }
    }

    fun startDownload(song: Song, onError: (String) -> Unit = {}) {
        if (song.sourceType == SongSourceType.LOCAL_DEVICE) return
        if (activeJobs.containsKey(song.id)) return

        val job = scope.launch {
            _downloadProgress.update { it + (song.id to 0f) }
            try {
                val downloaded = downloadSongFiles(song)
                val current = downloadedSongsFlow.first().toMutableMap()
                current[downloaded.id] = downloaded
                saveIndex(current)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Download failed for ${song.title}")
            } finally {
                activeJobs.remove(song.id)
                _downloadProgress.update { it - song.id }
            }
        }
        activeJobs[song.id] = job
    }

    fun cancelDownload(songId: String) {
        activeJobs[songId]?.cancel()
        activeJobs.remove(songId)
        _downloadProgress.update { it - songId }
    }

    suspend fun deleteDownload(songId: String) = withContext(Dispatchers.IO) {
        val current = downloadedSongsFlow.first().toMutableMap()
        val removed = current.remove(songId)
        if (removed != null) {
            removed.localAudioPath?.let { File(it).delete() }
            removed.localCoverPath?.let { File(it).delete() }
            removed.localLrcPath?.let { File(it).delete() }
            saveIndex(current)
        }
    }

    suspend fun clearAllDownloads() = withContext(Dispatchers.IO) {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        _downloadProgress.value = emptyMap()
        offlineDir.listFiles()?.forEach { it.delete() }
        appContext.offlineDataStore.edit { it.remove(indexKey) }
    }

    private suspend fun downloadSongFiles(song: Song): Song = withContext(Dispatchers.IO) {
        if (!offlineDir.exists()) offlineDir.mkdirs()
        val safeId = song.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val ext = extractExtension(song.audioUrl, fallback = ".mp3")
        val finalAudio = File(offlineDir, "$safeId$ext")
        val tempAudio = File(offlineDir, "$safeId$ext.tmp")

        try {
            val request = Request.Builder().url(song.audioUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Empty audio response")
                val total = body.contentLength()
                var readTotal = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempAudio).use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            readTotal += read
                            if (total > 0L) {
                                val pct = (readTotal.toFloat() / total.toFloat()).coerceIn(0f, 0.95f)
                                _downloadProgress.update { it + (song.id to pct) }
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (finalAudio.exists()) finalAudio.delete()
            if (!tempAudio.renameTo(finalAudio)) {
                tempAudio.copyTo(finalAudio, overwrite = true)
                tempAudio.delete()
            }

            val localCover = downloadOptionalFile(song.coverUrl, File(offlineDir, "${safeId}_cover.jpg"))
            val localLrc = downloadOptionalFile(song.lrcUrl, File(offlineDir, "$safeId.lrc"))
            _downloadProgress.update { it + (song.id to 1f) }

            val totalBytes = finalAudio.length() +
                (localCover?.let { File(it).length() } ?: 0L) +
                (localLrc?.let { File(it).length() } ?: 0L)

            song.copy(
                localAudioPath = finalAudio.absolutePath,
                localCoverPath = localCover,
                localLrcPath = localLrc,
                downloadedBytes = totalBytes,
            )
        } catch (e: Exception) {
            if (tempAudio.exists()) tempAudio.delete()
            throw e
        }
    }

    private fun downloadOptionalFile(url: String?, target: File): String? {
        if (url.isNullOrBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return null
        }
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return null
                val bytes = res.body?.bytes() ?: return null
                if (bytes.isEmpty()) return null
                target.writeBytes(bytes)
                target.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractExtension(url: String, fallback: String): String {
        val clean = url.substringBefore('?')
        val lastSegment = clean.substringAfterLast('/')
        val dot = lastSegment.lastIndexOf('.')
        if (dot != -1 && lastSegment.length - dot in 2..5) {
            return lastSegment.substring(dot)
        }
        return fallback
    }

    private suspend fun saveIndex(map: Map<String, Song>) {
        val arr = JSONArray()
        map.values.forEach { song ->
            arr.put(
                JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("language", song.language)
                    put("audioUrl", song.audioUrl)
                    put("coverUrl", song.coverUrl ?: JSONObject.NULL)
                    put("lrcUrl", song.lrcUrl ?: JSONObject.NULL)
                    put("sourceType", song.sourceType.name)
                    put("localAudioPath", song.localAudioPath ?: JSONObject.NULL)
                    put("localCoverPath", song.localCoverPath ?: JSONObject.NULL)
                    put("localLrcPath", song.localLrcPath ?: JSONObject.NULL)
                    put("downloadedBytes", song.downloadedBytes)
                },
            )
        }
        appContext.offlineDataStore.edit { prefs ->
            prefs[indexKey] = arr.toString()
        }
    }

    private fun parseIndex(raw: String): Map<String, Song> {
        if (raw.isBlank()) return emptyMap()
        return try {
            val arr = JSONArray(raw)
            val result = mutableMapOf<String, Song>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").takeIf { it.isNotBlank() } ?: continue
                val source = SongSourceType.entries.firstOrNull {
                    it.name == obj.optString("sourceType")
                } ?: SongSourceType.REMOTE_LICENSED

                result[id] = Song(
                    id = id,
                    title = obj.optString("title", "Unknown Title"),
                    artist = obj.optString("artist", "Unknown Artist"),
                    language = obj.optString("language", "All"),
                    audioUrl = obj.optString("audioUrl", ""),
                    coverUrl = obj.optString("coverUrl", "").takeIf { it.isNotBlank() && it != "null" },
                    lrcUrl = obj.optString("lrcUrl", "").takeIf { it.isNotBlank() && it != "null" },
                    sourceType = source,
                    localAudioPath = obj.optString("localAudioPath", "").takeIf { it.isNotBlank() && it != "null" },
                    localCoverPath = obj.optString("localCoverPath", "").takeIf { it.isNotBlank() && it != "null" },
                    localLrcPath = obj.optString("localLrcPath", "").takeIf { it.isNotBlank() && it != "null" },
                    downloadedBytes = obj.optLong("downloadedBytes", 0L),
                )
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 MB"
            val kb = 1024.0
            val mb = kb * 1024.0
            val gb = mb * 1024.0
            return when {
                bytes >= gb -> String.format("%.2f GB", bytes / gb)
                bytes >= mb -> String.format("%.1f MB", bytes / mb)
                else -> String.format("%.0f KB", bytes / kb)
            }
        }
    }
}
