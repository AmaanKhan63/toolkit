package com.amaankhan.toolkit.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.VisualTransformation

private class InputTarget {
    var coordinates: LayoutCoordinates? = null
    var focused = false
    var dismiss: () -> Unit = {}
}
private class InputTargets { val fields = mutableSetOf<InputTarget>() }
private val LocalInputTargets = staticCompositionLocalOf<InputTargets?> { null }

/** Observes taps without consuming clicks, scrolling, selection or preview gestures. */
@Composable fun InputFocusArea(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val targets = remember { InputTargets() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    CompositionLocalProvider(LocalInputTargets provides targets) {
        Box(modifier.onGloballyPositioned { coordinates = it }.pointerInput(targets) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val focused = targets.fields.firstOrNull { it.focused }
                var isTap = true
                var position = down.position
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (event.changes.size != 1 || change == null) isTap = false
                    if (change != null) {
                        position = change.position
                        if ((position - down.position).getDistance() > viewConfiguration.touchSlop ||
                            change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) isTap = false
                    }
                } while (event.changes.any { it.pressed })
                val windowPosition = coordinates?.takeIf { it.isAttached }?.localToWindow(position)
                if (isTap && focused?.focused == true && windowPosition != null) {
                    val insideInput = targets.fields.any { target ->
                        target.coordinates?.takeIf { it.isAttached }?.boundsInWindow()?.contains(windowPosition) == true
                    }
                    if (!insideInput) focused.dismiss()
                }
            }
        }) { content() }
    }
}

@Composable fun ToolkitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    minLines: Int = 1,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    val targets = LocalInputTargets.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val target = remember { InputTarget() }
    SideEffect { target.dismiss = { focus.clearFocus(); keyboard?.hide() } }
    DisposableEffect(targets, target) {
        targets?.fields?.add(target)
        onDispose { targets?.fields?.remove(target) }
    }
    OutlinedTextField(value, onValueChange,
        modifier = modifier.onGloballyPositioned { target.coordinates = it }.onFocusChanged { target.focused = it.hasFocus },
        enabled = enabled, label = label, placeholder = placeholder, leadingIcon = leadingIcon,
        trailingIcon = trailingIcon, suffix = suffix, supportingText = supportingText, isError = isError,
        visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions, singleLine = singleLine, minLines = minLines,
        shape = shape, colors = colors)
}
