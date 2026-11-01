package com.example.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * A slow series of light taps for as long as voice audio is being recorded, so the
 * feedback is noticeable without a constant buzz (shared across Notepad and Conversation modes).
 */
@Composable
fun RecordingVibration(isRecording: Boolean) {
    val context = LocalContext.current
    val vibrator = remember(context) { context.findVibrator() }

    LaunchedEffect(isRecording) {
        if (!isRecording) {
            vibrator?.cancel()
            return@LaunchedEffect
        }
        try {
            while (isActive) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(HAPTIC_TAP_MS, VIBRATION_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(HAPTIC_TAP_MS)
                }
                delay(HAPTIC_TAP_INTERVAL_MS)
            }
        } finally {
            vibrator?.cancel()
        }
    }

    DisposableEffect(Unit) {
        onDispose { vibrator?.cancel() }
    }
}

/** Length of one tap: short enough to read as a tap, not a buzz. */
private const val HAPTIC_TAP_MS = 70L

/** Silence between taps, so each one stays clearly separated. */
private const val HAPTIC_TAP_INTERVAL_MS = 1200L

/**
 * Gentle amplitude for the taps.
 *
 * Range is 1..255. Devices without amplitude control ignore this and always buzz at
 * full strength.
 */
private const val VIBRATION_AMPLITUDE = 45

private fun Context.findVibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
