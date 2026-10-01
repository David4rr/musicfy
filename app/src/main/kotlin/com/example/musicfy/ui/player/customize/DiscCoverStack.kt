// DiscCoverStack.kt

package com.example.musicfy.ui.player.customize

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.example.musicfy.constants.DiscNameKey
import com.example.musicfy.constants.DiscRealisticModeKey
import com.example.musicfy.constants.DiscRotatingAnimationKey
import com.example.musicfy.constants.PlayerCoverStyle
import com.example.musicfy.utils.rememberPreference
import kotlinx.coroutines.isActive

private const val SpinDegreesPerSecond = 26f

@Composable
fun DiscCoverStack(
    style: PlayerCoverStyle,
    artworkUrl: String?,
    mediaId: String,
    queueIndex: Int,
    isPlaying: Boolean,
    spinActive: Boolean,
    editMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val (rotatingEnabled) = rememberPreference(DiscRotatingAnimationKey, defaultValue = true)
    val (realistic) = rememberPreference(DiscRealisticModeKey, defaultValue = true)
    val (customName) = rememberPreference(DiscNameKey, defaultValue = "")
    val labelContent by com.example.musicfy.utils.rememberEnumPreference(
        com.example.musicfy.constants.DiscLabelContentKey,
        com.example.musicfy.constants.DiscLabelContent.CUSTOM,
    )
    val track = com.example.musicfy.LocalPlayerConnection.current?.uiState?.trackInfo?.collectAsState()?.value
    // what the label says, as chosen in the cover editor
    val discName = when (labelContent) {
        com.example.musicfy.constants.DiscLabelContent.CUSTOM -> customName
        com.example.musicfy.constants.DiscLabelContent.MUSIC_NAME -> track?.title.orEmpty()
        com.example.musicfy.constants.DiscLabelContent.ARTIST_NAME -> track?.artist.orEmpty()
        com.example.musicfy.constants.DiscLabelContent.ALBUM_NAME -> track?.album.orEmpty()
        com.example.musicfy.constants.DiscLabelContent.MUSIC_AND_ARTIST ->
            listOfNotNull(track?.title, track?.artist).filter { it.isNotBlank() }.joinToString(" · ")
        com.example.musicfy.constants.DiscLabelContent.NONE -> ""
    }

    val skip = rememberDiscSkipState(
        mediaId = mediaId,
        artworkUrl = artworkUrl,
        queueIndex = queueIndex,
    )

    val angle = remember { mutableFloatStateOf(0f) }
    val lastFrameNanos = remember { mutableLongStateOf(0L) }

    val spinning = rotatingEnabled && isPlaying && spinActive
    LaunchedEffect(spinning) {
        if (!spinning) {

            lastFrameNanos.longValue = 0L
            return@LaunchedEffect
        }
        while (isActive) {
            withInfiniteAnimationFrameNanos { frameTimeNanos ->
                val previous = lastFrameNanos.longValue
                lastFrameNanos.longValue = frameTimeNanos
                if (previous != 0L) {
                    val deltaSeconds = (frameTimeNanos - previous) / 1_000_000_000f
                    angle.floatValue = (angle.floatValue + SpinDegreesPerSecond * deltaSeconds).mod(360f)
                }
            }
        }
    }

    Box(modifier = modifier) {

        val outgoing = skip.outgoingArtworkUrl
        if (outgoing != null) {
            DiscCover(
                style = style,
                artworkUrl = outgoing,
                discName = discName,
                realistic = realistic,
                editMode = false,
                rotationProvider = { angle.floatValue },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = skip.outgoingOffset(size.width)
                        alpha = skip.outgoingAlpha()
                        val s = skip.outgoingScale()
                        scaleX = s
                        scaleY = s
                    },
            )
        }

        DiscCover(
            style = style,
            artworkUrl = artworkUrl,
            discName = discName,
            realistic = realistic,
            editMode = editMode,
            rotationProvider = { angle.floatValue },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = skip.incomingOffset(size.width)
                    alpha = skip.incomingAlpha()
                },
        )

        if (style.hasTonearm) {
            DiscTonearm(
                liftProvider = { skip.tonearmLift() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
