// NetEaseLyricsProvider.kt

package com.example.musicfy.lyrics

import android.content.Context
import com.music.directlyrics.NetEase
import com.example.musicfy.constants.EnableNetEaseKey
import com.example.musicfy.utils.dataStore
import com.example.musicfy.utils.get

object NetEaseLyricsProvider : LyricsProvider {
    override val name = "NetEase"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableNetEaseKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = NetEase.getLyrics(title, artist, duration)
}
