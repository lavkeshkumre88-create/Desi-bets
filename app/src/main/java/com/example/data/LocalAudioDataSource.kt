package com.example.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.config.AppConfig
import com.example.model.Song
import com.example.model.SongSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalAudioDataSource(private val context: Context) {
    private val appContext = context.applicationContext

    suspend fun loadDeviceSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            appContext.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol)?.trim().orEmpty().ifEmpty { "Unknown Track" }
                    val rawArtist = cursor.getString(artistCol)?.trim().orEmpty()
                    val artist = if (rawArtist.isEmpty() || rawArtist == "<unknown>") {
                        "Unknown Artist"
                    } else {
                        rawArtist
                    }
                    val album = cursor.getString(albumCol)?.trim()
                    val albumId = cursor.getLong(albumIdCol)
                    val durationMs = cursor.getLong(durationCol)
                    val filePath = cursor.getString(dataCol).orEmpty()

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id,
                    )
                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId,
                    )

                    val detectedLanguage = inferLanguage("$title $artist ${album.orEmpty()} $filePath")

                    songs.add(
                        Song(
                            id = "local_$id",
                            title = title,
                            artist = artist,
                            language = detectedLanguage,
                            audioUrl = contentUri.toString(),
                            coverUrl = albumArtUri.toString(),
                            lrcUrl = null,
                            sourceType = SongSourceType.LOCAL_DEVICE,
                            album = album,
                            durationMs = durationMs,
                        ),
                    )
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        songs
    }

    suspend fun songFromPickedUri(uri: Uri): Song? = withContext(Dispatchers.IO) {
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
            // Persistable permission may not be supported by all providers
        }

        var displayName = "Imported Audio"
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx != -1 && cursor.moveToFirst()) {
                displayName = cursor.getString(nameIdx)
                    ?.substringBeforeLast('.')
                    ?.ifBlank { "Imported Audio" }
                    ?: "Imported Audio"
            }
        }

        Song(
            id = "local_uri_${uri.toString().hashCode().toUInt()}",
            title = displayName,
            artist = "Device Audio",
            language = inferLanguage(displayName),
            audioUrl = uri.toString(),
            sourceType = SongSourceType.LOCAL_DEVICE,
        )
    }

    private fun inferLanguage(metadata: String): String {
        val lower = metadata.lowercase()
        for (lang in AppConfig.LANGUAGE_TABS) {
            if (lang == "All") continue
            if (lower.contains(lang.lowercase())) return lang
        }
        return "All"
    }
}
