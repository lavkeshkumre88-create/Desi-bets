package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.io.File

enum class SongSourceType {
    LOCAL_DEVICE,
    REMOTE_LICENSED,
}

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val language: String,
    val audioUrl: String,
    val coverUrl: String? = null,
    val lrcUrl: String? = null,
    val sourceType: SongSourceType = SongSourceType.REMOTE_LICENSED,
    val album: String? = null,
    val durationMs: Long = 0L,
    val localAudioPath: String? = null,
    val localCoverPath: String? = null,
    val localLrcPath: String? = null,
    val downloadedBytes: Long = 0L,
) {
    val isDownloaded: Boolean
        get() = !localAudioPath.isNullOrBlank() && File(localAudioPath).exists()

    val isPlayableOffline: Boolean
        get() = sourceType == SongSourceType.LOCAL_DEVICE || isDownloaded

    val effectiveAudioUri: String
        get() = if (isDownloaded && localAudioPath != null) {
            File(localAudioPath).toURI().toString()
        } else {
            audioUrl
        }

    val effectiveCoverModel: Any?
        get() = when {
            !localCoverPath.isNullOrBlank() && File(localCoverPath).exists() -> File(localCoverPath)
            !coverUrl.isNullOrBlank() -> coverUrl
            else -> null
        }

    fun toFirestoreSongMap(userId: String): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "songId" to id.take(128),
            "title" to title.ifBlank { "Unknown Title" }.take(200),
            "artist" to artist.ifBlank { "Unknown Artist" }.take(200),
            "language" to language.ifBlank { "All" }.take(40),
            "audioUrl" to audioUrl.take(1000),
            "coverUrl" to coverUrl?.takeIf { it.isNotBlank() }?.take(1000),
            "lrcUrl" to lrcUrl?.takeIf { it.isNotBlank() }?.take(1000),
            "sourceType" to sourceType.name,
            "createdAt" to FieldValue.serverTimestamp(),
        ).filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class FirestoreSongRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val songId: String = "",
    val title: String = "",
    val artist: String = "",
    val language: String = "All",
    val audioUrl: String = "",
    val coverUrl: String? = null,
    val lrcUrl: String? = null,
    val sourceType: String = SongSourceType.REMOTE_LICENSED.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
) {
    fun toDomainSong(): Song {
        val parsedSource = SongSourceType.entries.firstOrNull { it.name == sourceType }
            ?: SongSourceType.REMOTE_LICENSED
        return Song(
            id = songId.ifBlank { id },
            title = title.ifBlank { "Unknown Title" },
            artist = artist.ifBlank { "Unknown Artist" },
            language = language.ifBlank { "All" },
            audioUrl = audioUrl,
            coverUrl = coverUrl,
            lrcUrl = lrcUrl,
            sourceType = parsedSource,
        )
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class Playlist(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val songCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class LyricLine(
    val timestampMs: Long,
    val text: String,
)

enum class RepeatModeOption {
    OFF,
    ALL,
    ONE,
}

enum class SleepTimerOption(val label: String, val durationMs: Long?) {
    OFF("Off", null),
    MIN_15("15 minutes", 15 * 60_000L),
    MIN_30("30 minutes", 30 * 60_000L),
    MIN_45("45 minutes", 45 * 60_000L),
    MIN_60("60 minutes", 60 * 60_000L),
    END_OF_TRACK("End of current song", -1L),
}
