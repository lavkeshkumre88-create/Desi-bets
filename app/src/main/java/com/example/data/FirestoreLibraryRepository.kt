package com.example.data

import com.example.model.FirestoreSongRecord
import com.example.model.Playlist
import com.example.model.Song
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreLibraryRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val safeName = (user.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: "Desi Beats Listener").take(60)
            val safeEmail = user.email?.trim()?.take(160)
            val safePhoto = user.photoUrl?.toString()?.take(500)

            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to safePhoto,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "email" to safeEmail,
                    "photoUrl" to safePhoto,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null } as Map<String, Any>
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                }
                throw error
            }
    }

    suspend fun updateDisplayName(userId: String, newName: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user profile update." }
            val trimmed = newName.trim()
            require(trimmed.isNotEmpty() && trimmed.length <= 60) {
                "Display name must be between 1 and 60 characters."
            }
            db.collection("users").document(uid).update(
                mapOf(
                    "displayName" to trimmed,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeFavorites(userId: String): Flow<List<Song>> {
        val path = "users/$userId/favorites"
        return db.collection("users").document(userId).collection("favorites")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreSongRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                ).map { it.toDomainSong() }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun toggleFavorite(userId: String, song: Song, isCurrentlyFavorite: Boolean): Result<Unit> {
        val safeSongId = sanitizeFirestoreId(song.id)
        val path = "users/$userId/favorites/$safeSongId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user favorite write." }
            val docRef = db.collection("users").document(uid)
                .collection("favorites").document(safeSongId)

            if (isCurrentlyFavorite) {
                docRef.delete().await()
            } else {
                val sanitizedSong = song.copy(id = safeSongId)
                docRef.set(sanitizedSong.toFirestoreSongMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observePlaylists(userId: String): Flow<List<Playlist>> {
        val path = "users/$userId/playlists"
        return db.collection("users").document(userId).collection("playlists")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Playlist::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun createPlaylist(userId: String, name: String): Result<Unit> {
        val path = "users/$userId/playlists"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user playlist create." }
            val trimmed = name.trim()
            require(trimmed.isNotEmpty() && trimmed.length <= 80) {
                "Playlist name must be 1 to 80 characters."
            }
            val docRef = db.collection("users").document(uid).collection("playlists").document()
            val payload = mapOf(
                "userId" to uid,
                "name" to trimmed,
                "songCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePlaylist(userId: String, playlistId: String): Result<Unit> {
        val path = "users/$userId/playlists/$playlistId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user playlist delete." }
            require(playlistId.isNotBlank())
            val playlistRef = db.collection("users").document(uid)
                .collection("playlists").document(playlistId)
            val tracksSnap = playlistRef.collection("tracks").get().await()
            val batch = db.batch()
            tracksSnap.documents.forEach { batch.delete(it.reference) }
            batch.delete(playlistRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observePlaylistTracks(userId: String, playlistId: String): Flow<List<Song>> = flow {
        require(playlistId.isNotBlank())
        val path = "users/$userId/playlists/$playlistId/tracks"
        val upstream = db.collection("users").document(userId)
            .collection("playlists").document(playlistId)
            .collection("tracks")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreSongRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                ).map { it.toDomainSong() }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
        upstream.collect { emit(it) }
    }

    suspend fun addSongToPlaylist(userId: String, playlistId: String, song: Song): Result<Unit> {
        val safeSongId = sanitizeFirestoreId(song.id)
        val path = "users/$userId/playlists/$playlistId/tracks/$safeSongId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user playlist write." }
            val playlistRef = db.collection("users").document(uid)
                .collection("playlists").document(playlistId)
            val trackRef = playlistRef.collection("tracks").document(safeSongId)

            val existing = trackRef.get().await()
            if (!existing.exists()) {
                val batch = db.batch()
                batch.set(trackRef, song.copy(id = safeSongId).toFirestoreSongMap(uid))
                batch.update(
                    playlistRef,
                    mapOf(
                        "songCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeSongFromPlaylist(userId: String, playlistId: String, songId: String): Result<Unit> {
        val safeSongId = sanitizeFirestoreId(songId)
        val path = "users/$userId/playlists/$playlistId/tracks/$safeSongId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user playlist delete." }
            val playlistRef = db.collection("users").document(uid)
                .collection("playlists").document(playlistId)
            val trackRef = playlistRef.collection("tracks").document(safeSongId)

            val snap = trackRef.get().await()
            if (snap.exists()) {
                val batch = db.batch()
                batch.delete(trackRef)
                batch.update(
                    playlistRef,
                    mapOf(
                        "songCount" to FieldValue.increment(-1L),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    companion object {
        fun sanitizeFirestoreId(raw: String): String {
            val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            return cleaned.ifBlank { "song_${raw.hashCode().toUInt()}" }.take(128)
        }
    }
}
