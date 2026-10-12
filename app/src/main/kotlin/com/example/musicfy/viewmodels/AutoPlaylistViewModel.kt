// AutoPlaylistViewModel.kt

package com.example.musicfy.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.constants.HideExplicitKey
import com.example.musicfy.constants.HideVideoSongsKey
import com.example.musicfy.constants.LikedSongSortDescendingKey
import com.example.musicfy.constants.LikedSongSortType
import com.example.musicfy.constants.LikedSongSortTypeKey
import com.example.musicfy.constants.SongSortDescendingKey
import com.example.musicfy.constants.SongSortType
import com.example.musicfy.constants.SongSortTypeKey
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.db.entities.ImportedSong
import com.example.musicfy.db.entities.Song
import com.example.musicfy.extensions.filterExplicit
import com.example.musicfy.extensions.filterVideoSongs
import com.example.musicfy.extensions.toEnum
import com.example.musicfy.importer.ImportSource
import com.example.musicfy.utils.SyncUtils
import com.example.musicfy.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Liked songs that came from one place; [source] null is the ones liked here in Musicfy. */
data class LikedSourceSection(val source: ImportSource?, val songs: List<Song>)

data class AutoPlaylistSongs(
    val songs: List<Song>,
    /** Only for Liked songs in import order, when some were imported: [songs], split by service. */
    val sections: List<LikedSourceSection> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AutoPlaylistViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val playlist = savedStateHandle.get<String>("playlist")!!

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private data class ListPrefs(
        val sortType: SongSortType,
        val descending: Boolean,
        val likedSortType: LikedSongSortType,
        val likedDescending: Boolean,
        val hideExplicit: Boolean,
        val hideVideoSongs: Boolean,
    )

    /** null until the first load */
    val content: StateFlow<AutoPlaylistSongs?> =
        context.dataStore.data
            .map {
                ListPrefs(
                    sortType = it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                    descending = it[SongSortDescendingKey] ?: true,
                    likedSortType = it[LikedSongSortTypeKey].toEnum(LikedSongSortType.CREATE_DATE),
                    likedDescending = it[LikedSongSortDescendingKey] ?: true,
                    hideExplicit = it[HideExplicitKey] ?: false,
                    hideVideoSongs = it[HideVideoSongsKey] ?: false,
                )
            }
            .distinctUntilChanged()
            .flatMapLatest { prefs ->
                val visible: (List<Song>) -> List<Song> = {
                    it.filterExplicit(prefs.hideExplicit).filterVideoSongs(prefs.hideVideoSongs)
                }
                val (sortType, descending) = prefs.sortType to prefs.descending
                when (playlist) {
                    "liked" -> if (prefs.likedSortType == LikedSongSortType.IMPORT_ORDER) {
                        combine(
                            database.likedSongs(SongSortType.CREATE_DATE, prefs.likedDescending),
                            database.likedImportSources(),
                        ) { songs, imported -> bySource(visible(songs), imported) }
                    } else {
                        database.likedSongs(prefs.likedSortType.songSortType, prefs.likedDescending)
                            .map { AutoPlaylistSongs(visible(it)) }
                    }

                    "downloaded" -> database.downloadedSongs(sortType, descending)
                        .map { AutoPlaylistSongs(visible(it)) }

                    "uploaded" -> database.uploadedSongs(sortType, descending)
                        .map { AutoPlaylistSongs(visible(it)) }

                    "local" -> database.localSongs(sortType, descending)
                        .map { AutoPlaylistSongs(visible(it)) }

                    "songs" -> database.songs(sortType, descending)
                        .map { AutoPlaylistSongs(visible(it)) }

                    "recently_added" -> database.songs(SongSortType.CREATE_DATE, true)
                        .map { AutoPlaylistSongs(visible(it)) }

                    else -> flowOf(AutoPlaylistSongs(emptyList()))
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, null)

    /**
     * Liked songs by the service each was imported from, the ones liked here first and then the
     * services in the Library's order. Inside each, the order they were liked in: an imported like
     * keeps the date it was liked on the service, so that's the service's own order.
     */
    private fun bySource(songs: List<Song>, imported: List<ImportedSong>): AutoPlaylistSongs {
        // a song that came from two services goes with the one it came from first
        val sourceOf = imported
            .groupBy { it.songId }
            .mapValues { (_, rows) ->
                val first = rows.minWith(compareBy<ImportedSong>({ it.importedAt }, { ImportSource.fromKey(it.source)?.ordinal ?: Int.MAX_VALUE }))
                ImportSource.fromKey(first.source) ?: ImportSource.OTHER
            }
        val bySource = songs.groupBy { sourceOf[it.id] }
        // nothing imported: one plain list, no headings
        if (bySource.keys.all { it == null }) return AutoPlaylistSongs(songs)
        val sections = (listOf<ImportSource?>(null) + ImportSource.entries).mapNotNull { source ->
            bySource[source]?.let { LikedSourceSection(source, it) }
        }
        return AutoPlaylistSongs(songs = sections.flatMap { it.songs }, sections = sections)
    }

    fun syncLikedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
    }

    fun syncUploadedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncUploadedSongs() }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            when (playlist) {
                "liked" -> syncUtils.syncLikedSongsSuspend()
                "uploaded" -> syncUtils.syncUploadedSongsSuspend()
            }
            _isRefreshing.value = false
        }
    }
}

/** Import order lists by the date liked, then splits that by service. */
private val LikedSongSortType.songSortType: SongSortType
    get() = when (this) {
        LikedSongSortType.CREATE_DATE, LikedSongSortType.IMPORT_ORDER -> SongSortType.CREATE_DATE
        LikedSongSortType.NAME -> SongSortType.NAME
        LikedSongSortType.ARTIST -> SongSortType.ARTIST
        LikedSongSortType.PLAY_TIME -> SongSortType.PLAY_TIME
    }
