// ScrobbleSite.kt

package com.mochyy.mochymusic.scrobble

import androidx.datastore.preferences.core.Preferences
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.constants.EnableLastFMScrobblingKey
import com.mochyy.mochymusic.constants.EnableLibreFmScrobblingKey
import com.mochyy.mochymusic.constants.LastFMSessionKey
import com.mochyy.mochymusic.constants.LastFMUsernameKey
import com.mochyy.mochymusic.constants.LibreFmSessionKey
import com.mochyy.mochymusic.constants.LibreFmUsernameKey
import com.music.lastfm.Audioscrobbler

/**
 * The services Mochy scrobbles to. Both speak Last.fm's API, so one client serves both. Their
 * API keys come from local.properties (or CI secrets) at build time and never sit in the source.
 */
enum class ScrobbleSite(
    val label: String,
    val sessionKey: Preferences.Key<String>,
    val usernameKey: Preferences.Key<String>,
    /** Scrobbling on or paused, with the account kept either way. */
    val enabledKey: Preferences.Key<Boolean>,
    private val profilePage: String,
    val api: Audioscrobbler,
) {
    LASTFM(
        label = "Last.fm",
        sessionKey = LastFMSessionKey,
        usernameKey = LastFMUsernameKey,
        enabledKey = EnableLastFMScrobblingKey,
        profilePage = "https://www.last.fm/user/",
        api = Audioscrobbler(
            apiRoot = "https://ws.audioscrobbler.com/2.0/",
            authPage = "https://www.last.fm/api/auth/",
            apiKey = BuildConfig.LASTFM_API_KEY,
            secret = BuildConfig.LASTFM_SECRET,
        ),
    ),
    LIBREFM(
        label = "Libre.fm",
        sessionKey = LibreFmSessionKey,
        usernameKey = LibreFmUsernameKey,
        enabledKey = EnableLibreFmScrobblingKey,
        profilePage = "https://libre.fm/user/",
        api = Audioscrobbler(
            apiRoot = "https://libre.fm/2.0/",
            authPage = "https://www.libre.fm/api/auth/",
            apiKey = BuildConfig.LIBREFM_API_KEY,
            secret = BuildConfig.LIBREFM_SECRET,
        ),
    );

    fun profileUrl(username: String) = profilePage + username
}
