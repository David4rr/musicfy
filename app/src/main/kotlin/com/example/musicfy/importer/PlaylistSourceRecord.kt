// PlaylistSourceRecord.kt

package com.example.musicfy.importer

import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.db.entities.ImportedPlaylist
import com.example.musicfy.db.entities.ImportedPlaylistTrack
import com.example.musicfy.db.entities.PlaylistEntity
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/**
 * What Musicfy remembers about an imported playlist's life on its service: where it lives there,
 * and every song it had the last time it was read. Importing a playlist again and Re-sync both go
 * by this, so reading it again only brings in what the service added since.
 */
internal class PlaylistSourceRecord(private val database: MusicDatabase) {

    /**
     * The copy an earlier import made of this playlist: the one from the same place on [source],
     * or failing that (an import from before that was saved, a Musicfy backup) one with its name.
     * A copy already tied to a different playlist on [source] isn't this one, whatever its name.
     */
    suspend fun existingCopy(source: ImportSource, name: String, origin: PlaylistOrigin?): PlaylistEntity? {
        origin?.remoteId
            ?.let { database.importedPlaylistByRemoteId(source.key, it) }
            ?.let { database.playlist(it.playlistId).first()?.playlist }
            ?.let { return it }
        return database.playlistsByNameAsc().first()
            .filter { it.playlist.name == name && it.playlist.isEditable }
            .firstOrNull { candidate ->
                val tied = database.getImportedPlaylist(candidate.id)
                origin?.remoteId == null || tied?.source != source.key || tied.remoteId == null
            }
            ?.playlist
    }

    /**
     * The tracks [playlistId] hasn't had from the service before, each once. A playlist imported
     * before those were saved only has its songs to go by, so they're paired up by title and artist.
     */
    suspend fun newTracks(playlistId: String, tracks: List<ImportedTrack>): List<ImportedTrack> {
        val known = database.importedPlaylistTrackKeys(playlistId).toHashSet()
        if (known.isEmpty()) {
            database.playlistSongs(playlistId).first().forEach { entry ->
                val title = TrackMatcher.normalizeTitle(entry.song.song.title)
                known += youTubeKey(entry.song.id)
                entry.song.artists.forEach { known += "$title|${TrackMatcher.normalizeText(it.name)}" }
            }
        }
        val seen = HashSet<String>()
        return tracks.filter { track ->
            val key = track.trackKey()
            key !in known && seen.add(key)
        }
    }

    /** Saves where the playlist lives on [source] and every song it has there now. */
    suspend fun remember(source: ImportSource, playlistId: String, tracks: List<ImportedTrack>, origin: PlaylistOrigin?) {
        // one made here, or from another service, that an import topped up by name: nothing to re-sync with
        if (database.getImportedPlaylist(playlistId)?.source != source.key) return
        database.withTransaction {
            updateImportedPlaylistOrigin(playlistId, origin?.remoteId, origin?.remoteUrl, LocalDateTime.now())
            insertImportedPlaylistTracks(tracks.map { ImportedPlaylistTrack(playlistId, it.trackKey()) }.distinct())
        }
    }

    /**
     * A YouTube playlist saved as a copy from its own page. It never went through an import, so
     * nothing owned it before; this makes it one Re-sync can read again by [youTubeId].
     */
    suspend fun rememberYouTubeCopy(playlistId: String, youTubeId: String, songIds: List<String>) {
        val now = LocalDateTime.now()
        database.withTransaction {
            insert(
                ImportedPlaylist(
                    playlistId = playlistId,
                    source = ImportSource.YOUTUBE_MUSIC.key,
                    sessionId = null,
                    importedAt = now,
                    remoteId = youTubeId,
                    remoteUrl = "https://music.youtube.com/playlist?list=$youTubeId",
                    lastSyncedAt = now,
                ),
            )
            insertImportedPlaylistTracks(songIds.distinct().map { ImportedPlaylistTrack(playlistId, youTubeKey(it)) })
        }
    }
}

/**
 * What Re-sync knows a service's song by, from one read of the playlist to the next. Exact for
 * YouTube songs; for the other services the cleaned-up title and first artist, which come out the
 * same whether the playlist was read through a link or a signed-in account.
 */
internal fun ImportedTrack.trackKey(): String {
    (videoId ?: ytItem?.id)?.let { return youTubeKey(it) }
    val firstArtist = TrackMatcher.splitArtists(artist).firstOrNull().orEmpty()
    return "${TrackMatcher.normalizeTitle(title)}|${TrackMatcher.normalizeText(firstArtist)}"
}

private fun youTubeKey(videoId: String) = "yt:$videoId"
