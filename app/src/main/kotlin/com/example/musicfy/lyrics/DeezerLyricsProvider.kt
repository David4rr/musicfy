// DeezerLyricsProvider.kt

package com.example.musicfy.lyrics

import android.content.Context
import com.music.directlyrics.Deezer
import com.example.musicfy.constants.EnableDeezerKey
import com.example.musicfy.utils.dataStore
import com.example.musicfy.utils.get

object DeezerLyricsProvider : LyricsProvider {
    override val name = "Deezer"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableDeezerKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = Deezer.getLyrics(title, artist, duration)
}
