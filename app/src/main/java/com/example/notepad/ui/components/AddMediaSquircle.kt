package com.example.notepad.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.notepad.ui.theme.SquircleBg
import com.example.notepad.ui.theme.RecordAccent
import com.example.notepad.ui.theme.SquircleBorder
import com.example.notepad.ui.theme.TextOffWhite
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AddMediaSquircle(
    onTap: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp? = null
) {
    var isRecordingActive by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // The held button keeps its size and instead flashes its red frame.
    val frameAlpha = remember { Animatable(1f) }

    LaunchedEffect(isRecordingActive) {
        if (isRecordingActive) {
            frameAlpha.animateTo(
                targetValue = 0.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            frameAlpha.snapTo(1f)
        }
    }

    Box(
        modifier = modifier
            .then(if (size != null) Modifier.size(size) else Modifier.aspectRatio(1f))
            .testTag("add_media_squircle_button")
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isRecordingActive) RecordAccent.copy(alpha = 0.16f) else SquircleBg,
            border = BorderStroke(
                1.5.dp,
                if (isRecordingActive) RecordAccent.copy(alpha = frameAlpha.value) else SquircleBorder
            ),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var isRecordingStarted = false
                        val holdJob: Job = scope.launch {
                            delay(300)
                            isRecordingStarted = true
                            isRecordingActive = true
                            onStartRecording()
                        }

                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val anyPressed = event.changes.any { it.pressed }
                                if (!anyPressed) {
                                    event.changes.forEach { it.consume() }
                                    break
                                }
                            }
                        } finally {
                            holdJob.cancel()
                            if (isRecordingStarted) {
                                isRecordingActive = false
                                onStopRecording()
                            } else {
                                onTap()
                            }
                        }
                    }
                }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                if (isRecordingActive) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Recording voice",
                        tint = RecordAccent,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Tap for photo, hold for voice",
                        tint = TextOffWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
