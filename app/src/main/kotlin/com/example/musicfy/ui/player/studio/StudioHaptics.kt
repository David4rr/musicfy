package com.example.musicfy.ui.player.studio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** how long the hold's rising buzz runs; the studio opens as it peaks */
internal const val HoldRampMs = 110L

/**
 * The hold that opens the studio, felt: a quick buzz rising from barely there to full strength
 * over [HoldRampMs], so the selector arrives on the peak. A motor without amplitude control gets
 * one short tick instead.
 */
internal fun Context.playHoldRamp() {
    val vibrator = vibrator() ?: return
    if (!vibrator.hasVibrator()) return
    runCatching {
        if (vibrator.hasAmplitudeControl()) {
            val steps = 8
            val timings = LongArray(steps) { HoldRampMs / steps }
            // eased, so most of the rise is felt near the end
            val amplitudes = IntArray(steps) { i ->
                val t = (i + 1f) / steps
                (12 + 243 * t * t).toInt().coerceIn(1, 255)
            }
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(28L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}

private fun Context.vibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= 31) managerVibrator() else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

@RequiresApi(31)
private fun Context.managerVibrator(): Vibrator? =
    (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator

/**
 * A hold anywhere under this, held still for the long-press time, calls [onHold] - over buttons,
 * the seek bar, empty space alike. It watches from the outside (the initial pass), so nothing under
 * it has to give way; once it fires, the rest of that touch is the hold's, so the button it began
 * on doesn't also get a tap when the finger lifts.
 */
fun androidx.compose.ui.Modifier.holdAnywhere(enabled: () -> Boolean, onHold: () -> Unit): androidx.compose.ui.Modifier =
    pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        val timeout = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!enabled()) return@awaitEachGesture
            // null: the time ran out with the finger still down and still
            val ended = withTimeoutOrNull(timeout) {
                var released = false
                while (!released) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    released = change == null || !change.pressed || event.changes.size > 1 ||
                        (change.position - down.position).getDistance() > slop || change.isConsumed
                }
                true
            }
            if (ended != null || !enabled()) return@awaitEachGesture
            onHold()
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
            }
        }
    }
