// ImportSession.kt

package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * One run of an import: everything brought in from one service (or from the phone's storage) in
 * one go. Kept so "Undo import session" can take exactly that run back out, and nothing else.
 *
 * [source] is an `ImportSource.key`, or [DEVICE_SOURCE] for files imported from the phone.
 */
@Immutable
@Entity(tableName = "import_session")
data class ImportSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val startedAt: LocalDateTime,
) {
    companion object {
        /** The source of a session that imported audio files from the phone. */
        const val DEVICE_SOURCE = "device"
    }
}

/**
 * What one session did to one song. Undoing the session only reverses what it changed itself: a
 * song that was already in the library, or already liked, before the import stays that way.
 */
@Immutable
@Entity(
    tableName = "import_session_song",
    primaryKeys = ["sessionId", "songId"],
    indices = [Index(value = ["songId"])],
)
data class ImportSessionSong(
    val sessionId: Long,
    val songId: String,
    /** The import put the song in the library (or, for a file, created it). */
    val addedToLibrary: Boolean,
    /** The import liked the song. */
    val liked: Boolean,
)

/**
 * A playlist that came from a service, so the Library can count "Spotify playlists" and deleting
 * Spotify's imports can take its playlists with it. [sessionId] is null for a playlist that YouTube
 * sync brought in: it follows the account, so no import session owns it.
 */
@Immutable
@Entity(
    tableName = "imported_playlist",
    indices = [Index(value = ["source"])],
)
data class ImportedPlaylist(
    @PrimaryKey val playlistId: String,
    val source: String,
    val sessionId: Long?,
    val importedAt: LocalDateTime,
)
