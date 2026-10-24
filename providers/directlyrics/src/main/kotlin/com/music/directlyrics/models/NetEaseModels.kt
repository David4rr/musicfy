// NetEaseModels.kt

package com.music.directlyrics.models

import kotlinx.serialization.Serializable

@Serializable
internal data class NetEaseSearchResponse(val result: SearchResult? = null) {
    @Serializable
    data class SearchResult(val songs: List<Song> = emptyList())

    @Serializable
    data class Song(
        val id: Long,
        val name: String = "",
        /** Milliseconds. */
        val duration: Long = 0,
        val artists: List<Artist> = emptyList(),
        /** Other names the song goes by, often its English or original title. */
        val alias: List<String> = emptyList(),
    )

    @Serializable
    data class Artist(val name: String = "")
}

@Serializable
internal data class NetEaseLyricResponse(
    /** Line-timed LRC. */
    val lrc: Lyric? = null,
    /** Word-timed lyrics in NetEase's own format, when the song has them. */
    val yrc: Lyric? = null,
) {
    @Serializable
    data class Lyric(val lyric: String? = null)
}
