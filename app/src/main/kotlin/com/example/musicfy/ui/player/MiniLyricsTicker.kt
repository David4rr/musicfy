// MiniLyricsTicker.kt

package com.example.musicfy.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.constants.LyricsHighBloomKey
import com.example.musicfy.constants.LyricsMotionStyle
import com.example.musicfy.constants.LyricsMotionStyleKey
import com.example.musicfy.constants.LyricsWaveAnimationKey
import com.example.musicfy.lyrics.LyricsEntry
import com.example.musicfy.lyrics.RubyToken
import com.example.musicfy.lyrics.LyricsUtils
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine

private val TickerFontSize = 17.sp
private val TickerLineHeight = 22.sp

/** The letter motion is sized for 32sp lines; scaled down to match. */
private const val TickerMotionScale = 17f / 32f

/** Soft edges where the line runs past the card. */
private val TickerEdgeFade = 18.dp

/**
 * How far into the card the sweep is kept while a long line slides. Past the middle, so the reader
 * still sees a little of what's coming.
 */
private const val SweepKeepFraction = 0.62f

/** Critically damped follow time of the sideways slide: it trails the sweep, never jumps. */
private const val SlideFollowSec = 0.32f

/** Interlude dots at card size. */
private const val DotsScale = 0.5f

/** The dots row is 56dp tall with its dots inset by the lyric margin; this undoes that inset. */
private val DotsRowInset = LyricsHeaderArtX

private val LineChangeSpring = spring(
    dampingRatio = 0.86f,
    stiffness = Spring.StiffnessLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)
private val LineFadeSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

/** What the card is showing. Equal slots are the same content, so nothing re-animates. */
@Immutable
private data class TickerSlot(val kind: Int, val index: Int) {
    companion object {
        const val EMPTY = 0
        const val LINE = 1
        const val GAP = 2
        const val END = 3
        /** The lyrics are still being looked up. */
        const val LOADING = 4
    }
}

private fun tickerSlot(lines: List<LyricsEntry>, position: Long): TickerSlot {
    if (lines.isEmpty()) return TickerSlot(TickerSlot.EMPTY, -1)
    val index = LyricsUtils.findCurrentLineIndex(lines, position)
    if (index >= lines.size) return TickerSlot(TickerSlot.END, lines.lastIndex)
    if (index < 0) {
        return if (lines.first().time >= MinInterludeGapMs) {
            TickerSlot(TickerSlot.GAP, -1)
        } else {
            TickerSlot(TickerSlot.LINE, 0)
        }
    }
    // A long instrumental gap after this line reads as dots, like on the lyrics page.
    val next = lines.getOrNull(index + 1)
    if (next != null) {
        val end = LyricsUtils.lineEndMs(lines, index)
        if (next.time - end >= MinInterludeGapMs && position > end) {
            return TickerSlot(TickerSlot.GAP, index)
        }
    }
    return TickerSlot(TickerSlot.LINE, index)
}

/** The line the card is showing at [position], or -1 while it shows something else (a gap). */
internal fun cardLyricLineIndex(lines: List<LyricsEntry>, position: Long): Int {
    val slot = tickerSlot(lines, position)
    return if (slot.kind == TickerSlot.LINE) slot.index else -1
}

/**
 * The lyrics page, card-sized: the current line with the same karaoke sweep and letter motion. A
 * line too long for the card slides left as it's sung, keeping the sweep in view, and each new line
 * rolls up from below as the last one leaves.
 *
 * Only the slot (which line, or a gap) is collected from the playback position, so the card
 * recomposes once per line, not on every position tick; the sweep itself runs in frame callbacks.
 */
@Composable
internal fun MiniLyricsTicker(
    lines: List<LyricsEntry>,
    /** Lyrics have been looked up (an empty [lines] then means there are none). */
    loaded: Boolean,
    morph: CardMorphState,
    modifier: Modifier = Modifier,
    /**
     * How much taller than usual the card is, px (pulled in the player editor). The line keeps the
     * card's usual row at the top; under it go its reading, its translation and the lines after it.
     */
    growthProvider: () -> Float = { 0f },
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val tall by remember { derivedStateOf { growthProvider() > 0.5f } }
    val rowPx = with(LocalDensity.current) { PlayerCardHeight.roundToPx() }
    val positionProvider = remember(playerConnection) {
        { playerConnection.progressState.value.position + playerConnection.lyricsOffsetMs.value }
    }
    val slot by remember(lines, playerConnection) {
        combine(playerConnection.progressState, playerConnection.lyricsOffsetMs) { progress, offsetMs ->
            tickerSlot(lines, progress.position + offsetMs)
        }.distinctUntilChanged()
    }.collectAsState(initial = tickerSlot(lines, positionProvider()))

    val (motionStyle) = rememberEnumPreference(LyricsMotionStyleKey, defaultValue = LyricsMotionStyle.MOTION)
    val (waveEnabled) = rememberPreference(LyricsWaveAnimationKey, defaultValue = true)
    val (highBloom) = rememberPreference(LyricsHighBloomKey, defaultValue = true)

    // Still looking the lyrics up is its own state, so the dots hand over to the first line (or to
    // "No lyrics") with the same roll as any line change.
    val shown = if (!loaded && slot.kind == TickerSlot.EMPTY) TickerSlot(TickerSlot.LOADING, -1) else slot

    // The line gets the card's own row; a taller card lists what comes next under it, as its own
    // list that scrolls up a line at a time (see TickerUpcoming). Clipped as a whole, so a new line
    // rises into the row from where it sat in that list.
    Column(modifier = modifier.clipToBounds()) {
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                // Next line: the old one rolls up and out, the new one up and in - the lyrics page's
                // scroll, in a card. One row's height, however tall the card is.
                (slideInVertically(LineChangeSpring) { minOf(it, rowPx) } + fadeIn(LineFadeSpring))
                    .togetherWith(slideOutVertically(LineChangeSpring) { -minOf(it, rowPx) } + fadeOut(LineFadeSpring))
                    .using(SizeTransform(clip = false))
            },
            contentAlignment = Alignment.CenterStart,
            label = "miniLyricsLine",
            modifier = Modifier
                .fillMaxWidth()
                .height(PlayerCardHeight),
        ) { current ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (current.kind) {
                    TickerSlot.LINE, TickerSlot.END -> lines.getOrNull(current.index)?.let { entry ->
                        TickerLine(
                            entry = entry,
                            dim = current.kind == TickerSlot.END,
                            lineEndMs = LyricsUtils.lineEndMs(lines, current.index),
                            positionProvider = positionProvider,
                            motionStyle = motionStyle,
                            waveEnabled = waveEnabled,
                            highBloom = highBloom,
                            morph = morph,
                        )
                    }

                    TickerSlot.GAP -> {
                        val startMs = if (current.index < 0) 0L else LyricsUtils.lineEndMs(lines, current.index)
                        val endMs = lines.getOrNull(current.index + 1)?.time ?: startMs
                        LyricsInterludeDots(
                            startMs = startMs,
                            endMs = endMs,
                            positionProvider = positionProvider,
                            accentColor = Color.White,
                            alignment = LyricsAlignment.START,
                            modifier = Modifier.graphicsLayer {
                                scaleX = DotsScale
                                scaleY = DotsScale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                translationX = -DotsRowInset.toPx() * DotsScale
                            },
                        )
                    }

                    TickerSlot.LOADING -> LyricsLoadingDots(alignStart = true)

                    else -> Text(
                        text = "No lyrics for this song",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (tall) TickerUpcoming(lines, shown, growthProvider)
    }
}

/** How bright the next line is under a tall card's current one; each line after it a little dimmer. */
private const val NextLineAlpha = 0.38f
private const val NextLineDimStep = 0.14f

/** Lines after the current one a tall card lists at most. The ones that don't fit stay hidden. */
private const val UpcomingLinesMax = 6

/** The first line after the current one is barely soft; each one further down a little softer. */
private val UpcomingBlurFirst = 0.6.dp
private val UpcomingBlurStep = 0.45.dp

/** The upcoming lines' line height, which is also how far the list moves for each new line. */
private val UpcomingLineHeight = 17.sp

private val UpcomingScrollSpring = spring<Float>(dampingRatio = 0.86f, stiffness = Spring.StiffnessLow)

/**
 * What a taller card shows under its line: how the line is pronounced and what it means (when
 * translation is on), then the lines after it, as many as the card has room for, each dimmer and a
 * little more blurred than the one above. When the song moves on, the list scrolls up one line, so
 * the next line rises into the card's row rather than everything being swapped out at once.
 *
 * The reading and the translation fill in as the lyrics' readings pass gets to the line; what isn't
 * there takes no room.
 */
@Composable
private fun TickerUpcoming(lines: List<LyricsEntry>, slot: TickerSlot, growthProvider: () -> Float) {
    // during a break, the lines after it; at the end, nothing
    val firstUpcoming = when (slot.kind) {
        TickerSlot.LINE, TickerSlot.GAP -> slot.index + 1
        else -> return
    }
    val current = if (slot.kind == TickerSlot.LINE) lines.getOrNull(slot.index) else null
    val upcoming = remember(lines, firstUpcoming) {
        lines.drop(firstUpcoming.coerceAtLeast(0)).filter { it.text.isNotBlank() }.take(UpcomingLinesMax)
    }

    val stepPx = with(LocalDensity.current) { UpcomingLineHeight.toPx() }
    val scroll = remember { Animatable(0f) }
    var shownFrom by remember { mutableIntStateOf(firstUpcoming) }
    LaunchedEffect(firstUpcoming) {
        val movedOn = firstUpcoming > shownFrom
        shownFrom = firstUpcoming
        // moving on starts the list where it was and glides it up; a seek back just shows the new spot
        if (movedOn) {
            scroll.snapTo(stepPx)
            scroll.animateTo(0f, UpcomingScrollSpring)
        } else {
            scroll.snapTo(0f)
        }
    }

    Box(modifier = Modifier.graphicsLayer { translationY = scroll.value }) {
        CardExtraLines(growthProvider) {
            current?.let { TickerReadings(it) }
            upcoming.forEachIndexed { i, entry ->
                UpcomingLine(
                    text = entry.text,
                    alpha = NextLineAlpha * (1f - NextLineDimStep * i).coerceAtLeast(0.3f),
                    blur = UpcomingBlurFirst + UpcomingBlurStep * i,
                )
            }
        }
    }
}

/** The current line's reading and translation, under it on a tall card. */
@Composable
private fun TickerReadings(entry: LyricsEntry) {
    val romanized by entry.romanizedTextFlow.collectAsState()
    val ruby by entry.rubyFlow.collectAsState()
    val translated by entry.translatedTextFlow.collectAsState()
    // Japanese and Chinese carry their reading word by word over the text; in one line it's those
    // readings in order
    val reading = romanized ?: ruby?.takeIf { it.isNotEmpty() }?.let { rubyReading(entry.text, it) }
    reading?.let { CardExtraLine(it, alpha = 0.62f, italic = true) }
    translated?.takeIf { it.isNotBlank() }?.let { CardExtraLine(it, alpha = 0.82f) }
}

/**
 * One line further down the lyrics. Blurred through a layer of its own, so the list scrolling
 * moves the finished picture rather than blurring it again each frame. Android 12+ only: on older
 * phones the line is just dimmer.
 */
@Composable
private fun UpcomingLine(text: String, alpha: Float, blur: Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = UpcomingLineHeight),
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(alpha = alpha),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.blur(blur, BlurredEdgeTreatment.Unbounded),
    )
}

/** [text] read out: each run with a reading replaced by it, the rest (Latin words, say) kept as it is. */
private fun rubyReading(text: String, ruby: List<RubyToken>): String = buildString {
    var at = 0
    ruby.sortedBy { it.start }.forEach { token ->
        if (token.start > at) append(text, at, token.start.coerceAtMost(text.length))
        if (isNotEmpty() && !last().isWhitespace()) append(' ')
        append(token.text)
        at = maxOf(at, token.end)
    }
    if (at < text.length) append(text, at, text.length)
}.trim()

@Composable
private fun TickerLine(
    entry: LyricsEntry,
    dim: Boolean,
    lineEndMs: Long,
    positionProvider: () -> Long,
    motionStyle: LyricsMotionStyle,
    waveEnabled: Boolean,
    highBloom: Boolean,
    morph: CardMorphState,
) {
    val parts = remember(entry.text, entry.words) { splitBackingVocal(entry.text, entry.words) }
    val text = parts.lead.ifBlank { entry.text }
    val words = parts.leadWords
    val karaoke = !dim && !words.isNullOrEmpty() && text.isNotEmpty()

    val probe = remember(entry) { SweepProbe() }
    val viewport = remember { mutableFloatStateOf(0f) }
    val scroll = remember(entry) { mutableFloatStateOf(0f) }

    // The sideways slide: follows the sweep for a word-synced line, the clock for a line-synced
    // one. A critically damped follow, so a sweep that jumps (a seek) still glides.
    LaunchedEffect(entry, karaoke) {
        var value = 0f
        var velocity = 0f
        var lastMs = -1L
        while (true) {
            withFrameMillis { frameMs ->
                val dt = if (lastMs < 0L) 0.016f else ((frameMs - lastMs) / 1000f).coerceIn(0f, 0.064f)
                lastMs = frameMs
                val overflow = (probe.textWidth - viewport.floatValue).coerceAtLeast(0f)
                val target = when {
                    overflow <= 0f -> 0f
                    karaoke -> (probe.headX - viewport.floatValue * SweepKeepFraction).coerceIn(0f, overflow)
                    else -> {
                        val span = (lineEndMs - entry.time).coerceAtLeast(1L).toFloat()
                        val through = (positionProvider() - entry.time) / span
                        overflow * smoothstepF((through - 0.1f) / 0.75f)
                    }
                }
                val omega = 1f / SlideFollowSec
                val y = value - target
                val e = kotlin.math.exp(-omega * dt)
                val tmp = (velocity + omega * y) * dt
                value = target + (y + tmp) * e
                velocity = (velocity - omega * tmp) * e
                if (kotlin.math.abs(value - target) < 0.05f && kotlin.math.abs(velocity) < 1f) {
                    value = target
                    velocity = 0f
                }
                if (value != scroll.floatValue) scroll.floatValue = value
                morph.cardLyricScroll = value
            }
        }
    }

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewport.floatValue = it.width.toFloat() }
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Fade only the edge the line actually runs past, and only as far as it does.
                val overflow = (probe.textWidth - size.width).coerceAtLeast(0f)
                if (overflow <= 0f) return@drawWithContent
                val fade = TickerEdgeFade.toPx()
                val left = (scroll.floatValue / fade).coerceIn(0f, 1f)
                val right = ((overflow - scroll.floatValue) / fade).coerceIn(0f, 1f)
                val edge = (fade / size.width).coerceIn(0f, 0.5f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 1f - left),
                        edge to Color.Black,
                        1f - edge to Color.Black,
                        1f to Color.Black.copy(alpha = 1f - right),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = -scroll.floatValue },
        ) {
            if (karaoke) {
                KaraokeSweepText(
                    text = text,
                    words = words!!,
                    positionProvider = positionProvider,
                    waveAmplitudeProvider = { if (waveEnabled) 1f else 0f },
                    riseAmplitudeProvider = { 1f },
                    baseColor = Color.White.copy(alpha = 0.45f),
                    highlightColor = Color.White,
                    fontSize = TickerFontSize,
                    lineHeight = TickerLineHeight,
                    highBloom = highBloom,
                    motionStyle = motionStyle,
                    singleLine = true,
                    motionScale = TickerMotionScale,
                    probe = probe,
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = TickerFontSize,
                        lineHeight = TickerLineHeight,
                    ),
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = if (dim) 0.55f else 1f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    onTextLayout = { probe.textWidth = it.size.width.toFloat() },
                )
            }
        }
    }
}
