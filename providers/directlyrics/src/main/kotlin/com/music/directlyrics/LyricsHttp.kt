// LyricsHttp.kt

package com.music.directlyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal val LyricsJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

// One client for NetEase, QQ Music and Deezer. Some of them label their JSON as plain text or
// HTML, so those are read as JSON too.
internal val lyricsHttp by lazy {
    HttpClient(OkHttp) {
        expectSuccess = true
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 10_000
        }
        // Says who's asking, so whoever runs the server can reach us instead of just blocking us.
        install(UserAgent) { agent = "Musicfy (+https://github.com/realidkroo/musicfy)" }
        install(ContentNegotiation) {
            json(LyricsJson)
            json(LyricsJson, ContentType.Text.Plain)
            json(LyricsJson, ContentType.Text.Html)
        }
        install(ContentEncoding) {
            gzip()
            deflate()
        }
    }
}
