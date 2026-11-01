package com.example.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Coordinates outside-tap dismissal between [CreationVirtualKeyboard], [VirtualTextField],
 * and the hosting screen container.
 */
class KeyboardDismissController {
    private val keyboardBoundsByKey = mutableMapOf<Any, Rect>()
    var fieldInteractedInCurrentGesture: Boolean = false

    fun updateKeyboardBounds(key: Any, boundsInRoot: Rect?) {
        if (boundsInRoot != null) {
            keyboardBoundsByKey[key] = boundsInRoot
        } else {
            keyboardBoundsByKey.remove(key)
        }
    }

    fun isInsideAnyKeyboard(pointInRoot: Offset): Boolean =
        keyboardBoundsByKey.values.any { it.contains(pointInRoot) }

    fun markFieldInteraction() {
        fieldInteractedInCurrentGesture = true
    }
}

val LocalKeyboardDismissController = compositionLocalOf<KeyboardDismissController?> { null }

/**
 * Wraps a screen or sheet that hosts [CreationVirtualKeyboard] and automatically hides the
 * keyboard when the user taps anywhere outside both the keyboard tray and any [VirtualTextField].
 */
@Composable
fun KeyboardDismissSurface(
    isKeyboardVisible: Boolean,
    onDismissKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val controller = remember { KeyboardDismissController() }
    val currentIsVisible by rememberUpdatedState(isKeyboardVisible)
    val currentOnDismiss by rememberUpdatedState(onDismissKeyboard)
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    CompositionLocalProvider(LocalKeyboardDismissController provides controller) {
        Box(
            modifier = modifier
                .onGloballyPositioned { rootCoordinates = it }
                .pointerInput(controller) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        controller.fieldInteractedInCurrentGesture = false
                        val wasVisibleAtDown = currentIsVisible

                        val downInRoot = rootCoordinates
                            ?.takeIf { it.isAttached }
                            ?.localToRoot(down.position)
                        if (downInRoot != null && controller.isInsideAnyKeyboard(downInRoot)) {
                            return@awaitEachGesture
                        }

                        val pointerId = down.id
                        val downTime = down.uptimeMillis
                        var exceededSlop = false

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if ((change.position - down.position).getDistance() >
                                viewConfiguration.touchSlop
                            ) {
                                exceededSlop = true
                            }
                            if (!change.pressed) {
                                val heldDuration = change.uptimeMillis - downTime
                                val isLongPress =
                                    heldDuration >= viewConfiguration.longPressTimeoutMillis
                                if (wasVisibleAtDown &&
                                    currentIsVisible &&
                                    !exceededSlop &&
                                    !isLongPress &&
                                    !controller.fieldInteractedInCurrentGesture
                                ) {
                                    currentOnDismiss()
                                }
                                break
                            }
                        }
                    }
                },
            content = content
        )
    }
}

/**
 * Marks a padded container around a [VirtualTextField] as part of the editable field so that
 * tapping inside the box's padding activates the field instead of dismissing the keyboard.
 */
fun Modifier.virtualFieldContainer(onActivate: () -> Unit): Modifier = composed {
    val dismissController = LocalKeyboardDismissController.current
    val currentOnActivate by rememberUpdatedState(onActivate)
    this
        .pointerInput(dismissController) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                dismissController?.markFieldInteraction()
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { currentOnActivate() }
            )
        }
}
