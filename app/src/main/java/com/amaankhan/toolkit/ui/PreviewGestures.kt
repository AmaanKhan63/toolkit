package com.amaankhan.toolkit.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.composed
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** One deliberate horizontal swipe advances one page. Pinches and vertical scrolling retain ownership. */
internal fun Modifier.previewPageSwipe(enabled: Boolean = true, onNavigate: (Int) -> Unit): Modifier = composed {
    val canNavigate = rememberUpdatedState(enabled)
    val navigate = rememberUpdatedState(onNavigate)
    pointerInput(Unit) {
        val threshold = 56.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var allowed = canNavigate.value
            var dragging = false
            var dx = 0f
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (event.changes.size != 1 || change == null || !canNavigate.value) allowed = false
                if (change != null && allowed) {
                    val movement = change.position - down.position
                    if (!dragging) {
                        if (change.isConsumed || change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis ||
                            abs(movement.y) > viewConfiguration.touchSlop && abs(movement.y) >= abs(movement.x)) allowed = false
                        else if (abs(movement.x) > viewConfiguration.touchSlop && abs(movement.x) > abs(movement.y) * 1.5f) dragging = true
                    }
                    if (dragging) { dx = movement.x; change.consume() }
                }
            } while (event.changes.any { it.pressed })
            if (allowed && dragging && abs(dx) >= threshold) navigate.value(if (dx < 0) 1 else -1)
        }
    }
}
