// ImportHistory.kt

package com.example.musicfy.importer

import android.content.Context
import android.net.Uri
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.db.entities.ImportSession
import com.example.musicfy.db.entities.SongEntity
import com.example.musicfy.extensions.isSyncEnabled
import com.example.musicfy.utils.SyncUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Takes imports back out: the newest session, everything from one service, or everything. Each
 * session only reverses what it changed itself (see `ImportSessionSong`), so a song that was in
 * the library or liked before an import stays that way.
 *
 * Only local data changes. Likes the import sent to YouTube are taken back the same way a tap on
 * the heart would; playlists it copied to YouTube stay there.
 */
@Singleton
class ImportHistory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
) {
    /** Undoes the newest import from a service. Null when there is none to undo. */
    suspend fun undoLatestImport(): Int? = withContext(Dispatchers.IO) {
        database.importSessionsExcept(ImportSession.DEVICE_SOURCE).firstOrNull()?.let { undo(it) }
    }

    /** Undoes the newest import of files from the phone. Null when there is none to undo. */
    suspend fun undoLatestLocalImport(): Int? = withContext(Dispatchers.IO) {
        database.importSessions(ImportSession.DEVICE_SOURCE).firstOrNull()?.let { undo(it) }
    }

    /** Everything one service brought in. Returns how many songs left the library. */
    suspend fun deleteImportsFrom(source: ImportSource): Int = withContext(Dispatchers.IO) {
        database.importSessions(source.key).sumOf { undo(it) }
    }

    /** Every import from every service; files from the phone are left alone. */
    suspend fun deleteAllImports(): Int = withContext(Dispatchers.IO) {
        database.importSessionsExcept(ImportSession.DEVICE_SOURCE).sumOf { undo(it) }
    }

    /** Takes files imported from the phone out of the library. The files themselves stay on the phone. */
    suspend fun removeLocalSongs(songIds: Collection<String>) = withContext(Dispatchers.IO) {
        val songs = songIds.mapNotNull { database.getSongById(it)?.song }.filter { it.isLocal }
        if (songs.isEmpty()) return@withContext
        database.withTransaction {
            songs.forEach { deleteLocalSong(it) }
        }
        songs.forEach { deleteOwnCopies(it) }
    }

    private suspend fun undo(session: ImportSession): Int {
        val rows = database.importSessionSongs(session.id)
        // read before the transaction: a Flow query inside it would wait on the transaction itself
        val playlists = database.importSessionPlaylistIds(session.id)
            .mapNotNull { database.playlist(it).first()?.playlist }
        val isLocal = session.source == ImportSession.DEVICE_SOURCE

        val unliked = mutableListOf<SongEntity>()
        val removedFiles = mutableListOf<SongEntity>()
        var removed = 0
        database.withTransaction {
            for (row in rows) {
                val song = getSongById(row.songId)?.song ?: continue
                if (isLocal) {
                    // a file the session added has nothing to fall back to: it goes entirely
                    deleteLocalSong(song)
                    removedFiles += song
                    removed++
                    continue
                }
                val claimedElsewhere = otherSessionsWithSong(row.songId, session.id) > 0
                var updated = song
                if (row.liked && song.liked) updated = updated.copy(liked = false, likedDate = null)
                if (row.addedToLibrary && !claimedElsewhere && song.inLibrary != null) updated = updated.copy(inLibrary = null)
                if (updated != song) {
                    update(updated)
                    removed++
                }
                if (updated.liked != song.liked) unliked += updated
                if (otherSessionsFromSource(row.songId, session.source, session.id) == 0) {
                    deleteImportedSong(row.songId, session.source)
                }
            }
            playlists.forEach { playlist ->
                delete(playlist)
                deleteImportedPlaylist(playlist.id)
            }
            deleteImportSessionSongs(session.id)
            deleteImportSession(session.id)
        }

        removedFiles.forEach { deleteOwnCopies(it) }
        // a like still waiting to go up to YouTube shouldn't go now; one already there is taken back
        PendingYouTubeLikes.remove(context, unliked.map { it.id })
        if (context.isSyncEnabled()) unliked.forEach { syncUtils.likeSong(it) }
        return removed
    }

    /** Removes a local song's row and everything hanging off it that has no foreign key to it. */
    private fun MusicDatabase.deleteLocalSong(song: SongEntity) {
        delete(song)
        deleteFormat(song.id)
        deleteLyrics(song.id)
        deleteImportSessionSong(song.id)
    }

    /**
     * The artwork pulled out of the file's tags, and the app's own copy of a file shared from
     * another app. Both live in the app's folders and are no use once the song is gone.
     */
    private fun deleteOwnCopies(song: SongEntity) {
        runCatching {
            song.thumbnailUrl?.let(Uri::parse)?.takeIf { it.scheme == "file" }?.path?.let { File(it) }
                ?.takeIf { it.startsWith(context.cacheDir) }
                ?.delete()
            val sharedRoot = File(context.filesDir, "shared_audio")
            song.localUri?.let(Uri::parse)?.takeIf { it.scheme == "file" }?.path?.let { File(it) }
                ?.takeIf { it.startsWith(sharedRoot) }
                ?.parentFile?.deleteRecursively()
        }
    }
}
