// AudioFxService.kt

package com.example.musicfy.eq

import com.example.musicfy.eq.audio.LoudnessParams
import com.example.musicfy.eq.audio.ReverbParams
import javax.inject.Inject
import javax.inject.Singleton

/** The Audio FX settings every player's effects read at the start of each buffer. */
@Singleton
class AudioFxService @Inject constructor() {
    @Volatile
    var reverb: ReverbParams = ReverbParams()

    @Volatile
    var loudness: LoudnessParams = LoudnessParams()

    /** how much the loudness stage is pulling peaks down right now, in dB (0 or less), for the meter */
    @Volatile
    var loudnessReductionDb: Float = 0f
}
