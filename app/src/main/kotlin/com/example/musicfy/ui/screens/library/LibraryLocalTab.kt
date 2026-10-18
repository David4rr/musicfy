// LibraryLocalTab.kt
//
// The Library's Local tab: everything that plays from this phone, songs downloaded for offline and
// audio files imported from storage. At the top, the ways to bring files in; under them the same
// cards as All, counting only what is local; at the bottom, Manage local.

package com.example.musicfy.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.musicfy.db.entities.Artist
import com.example.musicfy.db.entities.Playlist
import com.example.musicfy.db.entities.Song
import com.example.musicfy.ui.component.LikedCover
import com.example.musicfy.ui.screens.search.SearchColors
import com.example.musicfy.ui.screens.search.SearchHorizontalPadding
import com.example.musicfy.ui.theme.InterFontFamily
import com.example.musicfy.viewmodels.LibraryHomeViewModel
import com.example.musicfy.viewmodels.LocalAlbum

/** What the Local tab counts. Only collected while the tab is open: the album grouping isn't free. */
@Immutable
class LocalTabData(
    val songs: List<Song>,
    val liked: List<Song>,
    val albums: List<LocalAlbum>,
    val artists: List<Artist>,
    val playlists: List<Playlist>,
) {
    /** The files imported from the phone, as opposed to downloads. */
    val files: List<Song> = songs.filter { it.song.isLocal }
}

@Composable
fun collectLocalTabData(viewModel: LibraryHomeViewModel): LocalTabData {
    val songs by viewModel.localSongs.collectAsState()
    val liked by viewModel.localLikedSongs.collectAsState()
    val albums by viewModel.localAlbums.collectAsState()
    val artists by viewModel.localArtists.collectAsState()
    val playlists by viewModel.localPlaylists.collectAsState()
    return remember(songs, liked, albums, artists, playlists) { LocalTabData(songs, liked, albums, artists, playlists) }
}

internal fun LazyListScope.libraryLocalTab(
    data: LocalTabData,
    hasStorageAccess: Boolean,
    onImport: () -> Unit,
    onScan: () -> Unit,
    onManage: () -> Unit,
    navController: NavController,
) {
    item(key = "local_import") {
        LocalImportCard(
            count = data.files.size,
            onClick = onImport,
            modifier = Modifier.padding(horizontal = SearchHorizontalPadding),
        )
    }

    // once Musicfy may read the storage, the import card itself lists the phone's songs
    if (!hasStorageAccess) {
        item(key = "local_storage") {
            Text(
                text = "Or",
                color = SearchColors.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = SearchHorizontalPadding + 2.dp, vertical = 10.dp),
            )
            LocalCard(onClick = onScan, modifier = Modifier.padding(horizontal = SearchHorizontalPadding)) {
                LocalCardTitle("Let Musicfy read your storage to find all your songs")
            }
        }
    }

    // Nothing downloaded and nothing imported: every card below would be an empty shell linking to
    // an empty page, and the import card above already says what to do.
    if (data.songs.isNotEmpty()) {
        item(key = "local_categories") {
            Spacer(modifier = Modifier.height(16.dp))
            LibraryCardGrid(
                listOf(
                    LibraryCardEntry("Songs", data.songs.size, data.songs.take(4).map { it.song.thumbnailUrl }, {
                        navController.navigate("auto_playlist/local")
                    }),
                    LibraryCardEntry("Playlist", data.playlists.size, data.playlists.take(2).flatMap { it.thumbnails }, {
                        navController.navigate("library/local/playlists")
                    }),
                    LibraryCardEntry("Albums", data.albums.size, data.albums.take(4).map { it.thumbnailUrl }, {
                        navController.navigate("library/local/albums")
                    }),
                    LibraryCardEntry(
                        "Artist",
                        data.artists.size,
                        data.artists.take(4).map { it.artist.thumbnailUrl },
                        { navController.navigate("library/local/artists") },
                        roundCovers = true,
                    ),
                ),
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        item(key = "local_recently_added") {
            LibraryWideCard(
                title = "Recently added",
                count = data.songs.size,
                covers = data.songs.take(4).map { it.song.thumbnailUrl },
                onClick = { navController.navigate("library/local/added") },
                modifier = Modifier.padding(horizontal = SearchHorizontalPadding),
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // only the liked songs that are local; every liked song is on the All tab
        if (data.liked.isNotEmpty()) {
            item(key = "local_liked") {
                LibraryWideCard(
                    title = "Liked",
                    count = data.liked.size,
                    covers = data.liked.take(4).map { it.song.thumbnailUrl },
                    onClick = { navController.navigate("auto_playlist/local_liked") },
                    sharedElementKey = "playlist-local_liked",
                    art = { LikedCover(it) },
                    modifier = Modifier.padding(horizontal = SearchHorizontalPadding),
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    item(key = "local_manage") {
        Spacer(modifier = Modifier.height(12.dp))
        LibraryManagePill(text = "Manage local", onClick = onManage)
    }
}

/** How many files came in from the phone, in a big light number, under the way to add more. */
@Composable
private fun LocalImportCard(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LocalCard(onClick = onClick, modifier = modifier) {
        LocalCardTitle("Import your local music here")
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = count.toString(),
                color = SearchColors.Secondary,
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Thin,
                    fontSize = 44.sp,
                    lineHeight = 44.sp,
                ),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Imported from local",
                color = SearchColors.Secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 7.dp),
            )
        }
    }
}

@Composable
private fun LocalCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SearchColors.Tile)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        content()
    }
}

@Composable
private fun LocalCardTitle(text: String) {
    Text(
        text = text,
        color = SearchColors.Primary,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.3).sp,
        fontWeight = FontWeight.Bold,
    )
}
