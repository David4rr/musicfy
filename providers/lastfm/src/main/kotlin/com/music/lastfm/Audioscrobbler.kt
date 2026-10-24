// Audioscrobbler.kt

package com.music.lastfm

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.http.userAgent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.security.MessageDigest

/** A song as a scrobbling service wants it. Times are in seconds. */
@Serializable
data class ScrobbleTrack(
    val artist: String,
    val track: String,
    val album: String? = null,
    val durationSeconds: Int? = null,
)

/** A signed-in account: the name it goes by and the key every scrobble is sent with. */
data class AudioscrobblerSession(val name: String, val key: String)

/** What the service said went wrong, by its own error codes (see the Last.fm API docs). */
class AudioscrobblerException(val code: Int, message: String) : Exception(message) {
    /** The account took back Musicfy's access, or the session is gone: sign in again. */
    val isSignedOut: Boolean get() = code == INVALID_SESSION

    /** Turned down for good, like a song with no title: sending it again won't help. */
    val isRejected: Boolean get() = code == INVALID_PARAMETERS || code == INVALID_RESOURCE

    /** Worth trying the same request again later. */
    val isTemporary: Boolean get() = code in TemporaryCodes

    /** Signing in: the user hasn't allowed Musicfy on the page yet. */
    val isWaitingForUser: Boolean get() = code == TOKEN_NOT_AUTHORIZED

    /** Signing in: the token is too old or unknown, so a new one is needed. */
    val isTokenExpired: Boolean get() = code == TOKEN_EXPIRED || code == INVALID_TOKEN

    companion object {
        /** Not one of the service's codes: the server itself failed, or answered with no JSON. */
        const val SERVER_ERROR = -1
        private const val INVALID_TOKEN = 4
        private const val INVALID_PARAMETERS = 6
        private const val INVALID_RESOURCE = 7
        private const val INVALID_SESSION = 9
        private const val TOKEN_NOT_AUTHORIZED = 14
        private const val TOKEN_EXPIRED = 15
        private val TemporaryCodes = setOf(SERVER_ERROR, 8, 11, 16, 29)
    }
}

/**
 * One Audioscrobbler 2.0 server: Last.fm's own, or one that copies its API, like Libre.fm. Every
 * call is signed the way the API asks: the parameters sorted by name and joined, the shared
 * secret added, then MD5. Network failures come out as IOExceptions, the service's own errors as
 * [AudioscrobblerException].
 */
class Audioscrobbler(
    private val apiRoot: String,
    private val authPage: String,
    private val apiKey: String,
    private val secret: String,
) {
    /** False in a build made without this service's API key, which can't sign anyone in. */
    val isConfigured: Boolean get() = apiKey.isNotEmpty() && secret.isNotEmpty()

    /** A request token: the first step of signing in. */
    suspend fun getToken(): String = call("auth.getToken").string("token") ?: throw unreadable()

    /** The service's page where the user signs in and lets Musicfy scrobble for them. */
    fun authUrl(token: String): String = "$authPage?api_key=$apiKey&token=$token"

    /** Once the user has allowed Musicfy on [authUrl]; until then this fails with [AudioscrobblerException.isWaitingForUser]. */
    suspend fun getSession(token: String): AudioscrobblerSession {
        val session = call("auth.getSession", mapOf("token" to token))["session"] as? JsonObject ?: throw unreadable()
        return AudioscrobblerSession(
            name = session.string("name") ?: throw unreadable(),
            key = session.string("key") ?: throw unreadable(),
        )
    }

    suspend fun updateNowPlaying(sessionKey: String, track: ScrobbleTrack) {
        call("track.updateNowPlaying", track.params(index = null), sessionKey)
    }

    /** Up to [MAX_BATCH] plays in one go, each with the time it started playing. */
    suspend fun scrobble(sessionKey: String, plays: List<Pair<ScrobbleTrack, Long>>) {
        require(plays.size <= MAX_BATCH) { "At most $MAX_BATCH scrobbles per request" }
        val params = buildMap {
            plays.forEachIndexed { index, (track, startedAt) ->
                putAll(track.params(index))
                put("timestamp[$index]", startedAt.toString())
            }
        }
        call("track.scrobble", params, sessionKey)
    }

    suspend fun love(sessionKey: String, artist: String, track: String, love: Boolean) {
        call(if (love) "track.love" else "track.unlove", mapOf("artist" to artist, "track" to track), sessionKey)
    }

    private suspend fun call(method: String, params: Map<String, String> = emptyMap(), sessionKey: String? = null): JsonObject {
        val signed = buildMap {
            put("method", method)
            put("api_key", apiKey)
            sessionKey?.let { put("sk", it) }
            putAll(params)
        }
        val response = client.post(apiRoot) {
            userAgent(USER_AGENT)
            setBody(
                FormDataContent(
                    Parameters.build {
                        signed.forEach { (name, value) -> append(name, value) }
                        append("api_sig", signature(signed))
                        // left out of the signature, as the API asks
                        append("format", "json")
                    },
                ),
            )
        }
        val body = response.bodyAsText()
        val json = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        (json?.get("error") as? JsonPrimitive)?.let { error ->
            val code = error.contentOrNull?.toIntOrNull() ?: AudioscrobblerException.SERVER_ERROR
            throw AudioscrobblerException(code, json.string("message") ?: "Error $code")
        }
        if (!response.status.isSuccess() || json == null) {
            throw AudioscrobblerException(AudioscrobblerException.SERVER_ERROR, "The server answered ${response.status.value}")
        }
        return json
    }

    private fun signature(params: Map<String, String>): String {
        val text = params.toSortedMap().entries.joinToString("") { it.key + it.value } + secret
        return MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun ScrobbleTrack.params(index: Int?): Map<String, String> {
        val suffix = index?.let { "[$it]" }.orEmpty()
        return buildMap {
            put("artist$suffix", artist)
            put("track$suffix", track)
            album?.let { put("album$suffix", it) }
            durationSeconds?.let { put("duration$suffix", it.toString()) }
        }
    }

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

    private fun unreadable() = AudioscrobblerException(AudioscrobblerException.SERVER_ERROR, "The answer couldn't be read")

    companion object {
        /** The most plays one track.scrobble request takes. */
        const val MAX_BATCH = 50

        private const val USER_AGENT = "Musicfy"

        private val client by lazy {
            HttpClient(OkHttp) {
                install(HttpTimeout) {
                    connectTimeoutMillis = 15_000
                    requestTimeoutMillis = 30_000
                    socketTimeoutMillis = 30_000
                }
                expectSuccess = false
            }
        }
    }
}
