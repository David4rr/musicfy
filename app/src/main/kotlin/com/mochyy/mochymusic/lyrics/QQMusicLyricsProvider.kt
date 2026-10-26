// QQMusicLyricsProvider.kt

package com.example.musicfy.lyrics

import android.content.Context
import com.music.directlyrics.QQMusic
import com.example.musicfy.constants.EnableQQMusicKey
import com.example.musicfy.utils.dataStore
import com.example.musicfy.utils.get

object QQMusicLyricsProvider : LyricsProvider {
    override val name = "QQMusic"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableQQMusicKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = QQMusic.getLyrics(title, artist, duration)
}
