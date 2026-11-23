package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.LyricLine
import com.example.model.Song
import com.example.model.SongSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

private val Context.settingsDataStore by preferencesDataStore(name = "desi_beats_settings")

object LrcParser {
    private val lineRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?](.*)""")

    fun parse(rawLrc: String): List<LyricLine> {
        if (rawLrc.isBlank()) return emptyList()
        val parsed = mutableListOf<LyricLine>()

        rawLrc.lines().forEach { rawLine ->
            val matches = lineRegex.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach

            // Text sits after the last timestamp tag on the line
            val lastMatch = matches.last()
            val text = rawLine.substring(lastMatch.range.last + 1)
                .replace(Regex("""\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?]"""), "")
                .trim()

            // Support standard lines (single tag) or multi-timestamp chorus lines
            val extractedText = text.ifEmpty { lastMatch.groupValues[4].trim() }
            if (extractedText.isEmpty()) return@forEach

            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val millisRaw = match.groupValues[3]
                val millis = when (millisRaw.length) {
                    0 -> 0L
                    1 -> (millisRaw.toLongOrNull() ?: 0L) * 100L
                    2 -> (millisRaw.toLongOrNull() ?: 0L) * 10L
                    else -> millisRaw.take(3).toLongOrNull() ?: 0L
                }
                val totalMs = (minutes * 60_000L) + (seconds * 1_000L) + millis
                parsed.add(LyricLine(timestampMs = totalMs, text = extractedText))
            }
        }

        return parsed.sortedBy { it.timestampMs }
    }
}

class RemoteSongsDataSource(context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val songsJsonUrlKey = stringPreferencesKey("songs_json_url")
    private val adsRemovedKey = booleanPreferencesKey("ads_removed")

    val songsJsonUrlFlow: Flow<String> = appContext.settingsDataStore.data.map { prefs ->
        prefs[songsJsonUrlKey].orEmpty()
    }

    val adsRemovedFlow: Flow<Boolean> = appContext.settingsDataStore.data.map { prefs ->
        prefs[adsRemovedKey] ?: false
    }

    suspend fun setSongsJsonUrl(url: String) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[songsJsonUrlKey] = url.trim()
        }
    }

    suspend fun setAdsRemoved(removed: Boolean) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[adsRemovedKey] = removed
        }
    }

    suspend fun fetchServerSongs(url: String): Result<List<Song>> = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return@withContext Result.success(emptyList())
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return@withContext Result.failure(IllegalArgumentException("Server URL must start with http:// or https://"))
        }

        try {
            val request = Request.Builder().url(trimmed).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Server returned HTTP ${response.code}"),
                    )
                }
                val body = response.body?.string().orEmpty()
                val jsonArray = JSONArray(body)
                val list = mutableListOf<Song>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val title = obj.optString("title", "").trim().ifEmpty { "Unknown Title" }
                    val artist = obj.optString("artist", "").trim().ifEmpty { "Unknown Artist" }
                    val language = obj.optString("language", "All").trim().ifEmpty { "All" }
                    val audioUrl = obj.optString("audioUrl", "").trim()
                    if (audioUrl.isEmpty()) continue

                    val coverUrl = obj.optString("coverUrl", "").trim().ifEmpty { null }
                    val lrcUrl = obj.optString("lrcUrl", "").trim().ifEmpty { null }
                    val rawId = obj.optString("id", "").trim().ifEmpty {
                        "${title}_${artist}_$audioUrl"
                    }
                    val safeId = "srv_${rawId.hashCode().toUInt()}"

                    list.add(
                        Song(
                            id = safeId,
                            title = title,
                            artist = artist,
                            language = language,
                            audioUrl = audioUrl,
                            coverUrl = coverUrl,
                            lrcUrl = lrcUrl,
                            sourceType = SongSourceType.REMOTE_LICENSED,
                        ),
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadLyricsForSong(song: Song): List<LyricLine> = withContext(Dispatchers.IO) {
        // 1. Prefer offline cached .lrc file when available
        val localLrc = song.localLrcPath?.let { File(it) }
        if (localLrc != null && localLrc.exists()) {
            val content = runCatching { localLrc.readText() }.getOrNull().orEmpty()
            val parsed = LrcParser.parse(content)
            if (parsed.isNotEmpty()) return@withContext parsed
        }

        // 2. Fallback to remote .lrc URL
        val lrcUrl = song.lrcUrl?.trim().orEmpty()
        if (lrcUrl.isEmpty() || (!lrcUrl.startsWith("http://") && !lrcUrl.startsWith("https://"))) {
            return@withContext emptyList()
        }

        return@withContext try {
            val request = Request.Builder().url(lrcUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val text = response.body?.string().orEmpty()
                LrcParser.parse(text)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
