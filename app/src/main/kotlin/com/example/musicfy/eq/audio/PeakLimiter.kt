// PeakLimiter.kt

package com.example.musicfy.eq.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log2
import kotlin.math.max

/**
 * Catches the equalizer's loudest peaks, so a boost can stay loud instead of the whole song being
 * turned down to make room for it. It looks [LookaheadSeconds] ahead and eases the level down just
 * before a peak would pass full scale. It holds that level for [HoldSeconds], longer than a bass
 * note's cycle, then lets it back up over [ReleaseSeconds]. Rising between every crest would put a
 * buzz on loud bass. Every channel gets the same gain, so the stereo picture never moves.
 *
 * Music comes out [latencyFrames] late. Below full scale nothing else changes, so with the
 * equalizer off the sound is the same, sample for sample, only a few milliseconds later.
 */
class PeakLimiter {

    var latencyFrames = 0
        private set

    private var channels = 0
    /** how long a dip is held and how long it takes to ease in: one frame more than the delay */
    private var window = 0
    private var holdFrames = 0
    private var releaseStep = 0.0

    private var delay = DoubleArray(0)
    private var delayPos = 0

    // the lowest gain asked for in the last window, kept as a queue that rises from front to back
    private var minGain = DoubleArray(0)
    private var minFrame = LongArray(0)
    private var minHead = 0
    private var minSize = 0

    // the held gains, averaged over the window so each dip becomes a smooth ramp
    private var held = DoubleArray(0)
    private var heldPos = 0
    private var heldSum = 0.0
    private var heldOnes = 0

    private var envelope = 1.0
    private var holdLeft = 0
    private var frame = 0L

    fun configure(sampleRate: Int, channels: Int) {
        this.channels = channels
        latencyFrames = (sampleRate * LookaheadSeconds).toInt().coerceAtLeast(1)
        window = latencyFrames + 1
        holdFrames = (sampleRate * HoldSeconds).toInt()
        releaseStep = 1.0 - exp(-1.0 / (ReleaseSeconds * sampleRate))
        delay = DoubleArray(latencyFrames * channels)
        minGain = DoubleArray(window)
        minFrame = LongArray(window)
        held = DoubleArray(window)
        reset()
    }

    fun reset() {
        delay.fill(0.0)
        delayPos = 0
        minHead = 0
        minSize = 0
        held.fill(1.0)
        heldPos = 0
        heldSum = window.toDouble()
        heldOnes = window
        envelope = 1.0
        holdLeft = 0
        frame = 0
    }

    /** limits [frames] interleaved frames of [buffer] in place; what comes out is [latencyFrames] older */
    fun process(buffer: DoubleArray, frames: Int) {
        if (channels == 0) return
        // the usual case, nothing near full scale: skip the gain maths and only delay
        if (envelope == 1.0 && heldOnes == window && peak(buffer, frames) <= Ceiling) {
            delayOnly(buffer, frames)
            return
        }
        var i = 0
        for (f in 0 until frames) {
            var peak = 0.0
            for (c in 0 until channels) peak = max(peak, abs(buffer[i + c]))
            val needed = if (peak > Ceiling) Ceiling / peak else 1.0
            if (needed < 1.0) holdLeft = holdFrames else if (holdLeft > 0) holdLeft--
            // drop at once, hold, then recover slowly; the window below turns the drop into a ramp
            val recovered = envelope + (1.0 - envelope) * releaseStep
            val resting = when {
                holdLeft > 0 -> envelope
                recovered > 1.0 - Settled -> 1.0
                else -> recovered
            }
            envelope = minOf(needed, resting)

            val gain = smooth(lowestInWindow(envelope))
            for (c in 0 until channels) {
                val d = delayPos + c
                val late = delay[d]
                delay[d] = buffer[i + c]
                buffer[i + c] = late * gain
            }
            delayPos += channels
            if (delayPos == delay.size) delayPos = 0
            i += channels
            frame++
        }
    }

    private fun peak(buffer: DoubleArray, frames: Int): Double {
        var peak = 0.0
        for (i in 0 until frames * channels) peak = max(peak, abs(buffer[i]))
        return peak
    }

    private fun delayOnly(buffer: DoubleArray, frames: Int) {
        var i = 0
        for (f in 0 until frames) {
            for (c in 0 until channels) {
                val d = delayPos + c
                val late = delay[d]
                delay[d] = buffer[i]
                buffer[i] = late
                i++
            }
            delayPos += channels
            if (delayPos == delay.size) delayPos = 0
        }
        frame += frames
        // every gain in the window was 1, so the queue has nothing worth keeping
        minSize = 0
    }

    /** the lowest of the last [window] envelopes, [value] being the newest */
    private fun lowestInWindow(value: Double): Double {
        while (minSize > 0 && minFrame[minHead] <= frame - window) {
            minHead = if (minHead + 1 == window) 0 else minHead + 1
            minSize--
        }
        // anything at the back that's no lower than the new value can never be the lowest again
        while (minSize > 0) {
            var back = minHead + minSize - 1
            if (back >= window) back -= window
            if (minGain[back] < value) break
            minSize--
        }
        var slot = minHead + minSize
        if (slot >= window) slot -= window
        minGain[slot] = value
        minFrame[slot] = frame
        minSize++
        return minGain[minHead]
    }

    /**
     * The average of the last [window] held gains. Each one is already at or below what the
     * oldest frame in the delay needs, so the ramp lands in time and never lets a peak through.
     */
    private fun smooth(gain: Double): Double {
        val oldest = held[heldPos]
        if (oldest == 1.0) heldOnes--
        if (gain == 1.0) heldOnes++
        heldSum += gain - oldest
        held[heldPos] = gain
        heldPos = if (heldPos + 1 == window) 0 else heldPos + 1
        if (heldOnes == window) {
            // back to rest: start the sum clean so rounding never builds up
            heldSum = window.toDouble()
            return 1.0
        }
        return heldSum / window
    }

    companion object {
        private const val LookaheadSeconds = 0.005
        /** one cycle of a 40 Hz bass note */
        private const val HoldSeconds = 0.025
        private const val ReleaseSeconds = 0.15
        private const val Ceiling = 1.0
        private const val Settled = 1e-6

        /** how much of a bass or mid boost the limiter takes without the song sounding squashed */
        private const val BoostRoomDb = 4.0

        /**
         * How many dB of boost at [frequency] the limiter can take on its own, so the auto preamp
         * only has to turn down the rest. Songs carry much less treble than bass, so a treble
         * boost barely raises their peaks, and the room grows by 3 dB an octave above 1 kHz.
         */
        fun boostRoomDb(frequency: Double): Double =
            BoostRoomDb + 3.0 * max(0.0, log2(frequency / 1000.0))
    }
}
