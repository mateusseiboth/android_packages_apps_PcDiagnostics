package org.lineageos.pcdiagnostics.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.lineageos.pcdiagnostics.InputBench
import org.lineageos.pcdiagnostics.R
import org.lineageos.pcdiagnostics.core.InputDeviceInfo
import org.lineageos.pcdiagnostics.core.KeyCap
import org.lineageos.pcdiagnostics.core.KeyCell
import org.lineageos.pcdiagnostics.core.KeyStatus
import org.lineageos.pcdiagnostics.core.KeyboardLayout
import org.lineageos.pcdiagnostics.core.KeyboardState
import org.lineageos.pcdiagnostics.core.PointerButton
import org.lineageos.pcdiagnostics.core.TouchSession
import org.lineageos.pcdiagnostics.readAxisRange
import org.lineageos.pcdiagnostics.readKeyLabels
import org.lineageos.pcdiagnostics.readPointers
import org.lineageos.pcdiagnostics.readTouchpadAspect

/** Finger colors, one per color slot of a [TouchSession]. */
private val TRACE_COLORS = listOf(
    Color(0xFF3B6FE0), Color(0xFFE0483B), Color(0xFF2E9E5B), Color(0xFFE5A50A), Color(0xFF9348D6),
    Color(0xFF1FA8BE), Color(0xFFE8771A), Color(0xFFD63A8C), Color(0xFF7A5C3E), Color(0xFF5A8F2B),
)

private class KeyLook(val container: Color, val content: Color)

private val KEY_LOOKS: Map<KeyStatus, (ColorScheme) -> KeyLook> = mapOf(
    KeyStatus.UNTESTED to { colors -> KeyLook(colors.surfaceContainerHighest, colors.onSurfaceVariant) },
    KeyStatus.TESTED to { colors -> KeyLook(colors.primaryContainer, colors.onPrimaryContainer) },
    KeyStatus.PRESSED to { colors -> KeyLook(colors.primary, colors.onPrimary) },
)

private val KEY_ACTIONS: Map<Int, InputBench.(KeyEvent) -> Unit> = mapOf(
    KeyEvent.ACTION_DOWN to { event -> onKeyDown(event.scanCode, event.keyCode) },
    KeyEvent.ACTION_UP to { event -> onKeyUp(event.scanCode, event.keyCode) },
)

/** The keyboard drawn key by key: held keys light up, pressed ones stay marked. Alt + Esc closes it. */
@Composable
fun KeyboardTester(device: InputDeviceInfo, bench: InputBench, onClose: () -> Unit) {
    val layout by bench.layout.collectAsStateWithLifecycle()
    val session by bench.keyboard.collectAsStateWithLifecycle()
    val state = session ?: KeyboardState()
    val labels = remember(device.id, layout) { readKeyLabels(device.id, layout) }
    LaunchedEffect(device.id) { bench.startKeyboardSession() }
    TesterDialog(
        title = R.string.keyboard_tester_title,
        hint = stringResource(R.string.keyboard_tester_hint),
        onKey = { event -> handleKey(event, device, bench, onClose) },
        onClose = onClose,
    ) {
        Text(device.name, style = MaterialTheme.typography.titleMedium)
        LayoutPicker(layout, bench::onLayoutSelected)
        KeyboardDrawing(layout, state, labels)
        Text(
            stringResource(R.string.keyboard_keys_tested, layout.keys.count { it in state.tested }, layout.keys.size),
            style = MaterialTheme.typography.bodyMedium,
        )
        state.lastKeyCode?.let { code ->
            Text(stringResource(R.string.keyboard_last_key, KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Every key goes to the tester; keys from other devices are swallowed so they do nothing else. */
private fun handleKey(event: KeyEvent, device: InputDeviceInfo, bench: InputBench, onClose: () -> Unit): Boolean {
    if (KeyboardLayout.isExitChord(event.keyCode, event.isAltPressed)) {
        onClose()
        return true
    }
    if (event.deviceId != device.id) return true
    KEY_ACTIONS[event.action]?.invoke(bench, event)
    return true
}

@Composable
private fun LayoutPicker(selected: KeyboardLayout, onSelected: (KeyboardLayout) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.keyboard_layout), style = MaterialTheme.typography.labelLarge)
        KeyboardLayout.ALL.forEach { layout ->
            FilterChip(selected = layout == selected, onClick = { onSelected(layout) }, label = { Text(stringResource(Texts.KEYBOARD_LAYOUTS.getValue(layout))) })
        }
    }
}

@Composable
private fun KeyboardDrawing(layout: KeyboardLayout, state: KeyboardState, labels: Map<KeyCap, String>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val unit = maxWidth / layout.widthUnits
        Box(Modifier.fillMaxWidth().height(unit * layout.heightUnits)) {
            layout.cells.forEach { cell -> KeyFace(cell, labels[cell.key] ?: cell.key.label, state.statusOf(cell.key), unit) }
        }
    }
}

@Composable
private fun KeyFace(cell: KeyCell, label: String, status: KeyStatus, unit: Dp) {
    val look = KEY_LOOKS.getValue(status)(MaterialTheme.colorScheme)
    Box(
        Modifier.offset(unit * cell.x, unit * cell.y).size(unit * cell.width, unit * cell.height).padding(2.dp)
            .clip(RoundedCornerShape(6.dp)).background(look.container),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = look.content, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/**
 * Fingers on the touchpad, drawn as traces, and its button clicks. A click on the area captures the
 * pointer, so the touchpad sends raw fingers instead of moving the cursor; Esc lets it go and closes.
 */
@Composable
fun TouchpadTester(device: InputDeviceInfo, onClose: () -> Unit) {
    var session by remember { mutableStateOf(TouchSession()) }
    var captured by remember { mutableStateOf(false) }
    val aspect = remember(device.id) { readTouchpadAspect(device.id) }
    TesterDialog(
        title = R.string.touchpad_tester_title,
        hint = stringResource(R.string.touchpad_tester_hint.takeIf { captured } ?: R.string.touchpad_tester_start),
        onKey = { event -> closeOnEscape(event, onClose) },
        onClose = onClose,
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            view.setOnCapturedPointerListener { _, event ->
                session = session.onTouchpadEvent(event, device.id)
                true
            }
            onDispose {
                view.setOnCapturedPointerListener(null)
                view.releasePointerCapture()
            }
        }
        TraceCanvas(
            session,
            Modifier.fillMaxWidth().aspectRatio(aspect).clickable {
                view.requestPointerCapture()
                captured = true
            },
        )
        TouchSummary(session)
    }
}

private fun TouchSession.onTouchpadEvent(event: MotionEvent, deviceId: Int): TouchSession {
    if (event.deviceId != deviceId) return this
    val clicked = onButtons(event.buttonState)
    // A captured mouse sends relative moves: only its buttons count.
    if (!event.isFromSource(InputDevice.SOURCE_TOUCHPAD)) return clicked
    return clicked.onPointers(readPointers(event, readAxisRange(event, MotionEvent.AXIS_X), readAxisRange(event, MotionEvent.AXIS_Y)))
}

/** The whole window is the drawing area; only touches of the chosen screen draw. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouchscreenTester(device: InputDeviceInfo, onClose: () -> Unit) {
    var session by remember { mutableStateOf(TouchSession()) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    TesterDialog(
        title = R.string.touchscreen_tester_title,
        hint = stringResource(R.string.touchscreen_tester_hint),
        onKey = { event -> closeOnEscape(event, onClose) },
        onClose = onClose,
        frame = Modifier.fillMaxSize(),
    ) {
        TouchSummary(session)
        TraceCanvas(
            session,
            Modifier.fillMaxWidth().weight(1f).onSizeChanged { area = it }.pointerInteropFilter { event ->
                session = session.onTouchscreenEvent(event, device.id, area)
                true
            },
        )
    }
}

private fun TouchSession.onTouchscreenEvent(event: MotionEvent, deviceId: Int, area: IntSize): TouchSession {
    if (event.deviceId != deviceId || !event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) return this
    return onPointers(readPointers(event, 0f..area.width.toFloat(), 0f..area.height.toFloat()))
}

private fun closeOnEscape(event: KeyEvent, onClose: () -> Unit): Boolean {
    if (event.keyCode != KeyEvent.KEYCODE_ESCAPE) return false
    if (event.action == KeyEvent.ACTION_UP) onClose()
    return true
}

@Composable
private fun TraceCanvas(session: TouchSession, modifier: Modifier) {
    val background = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.clip(RoundedCornerShape(16.dp)).background(background)) {
        session.traces.forEach { trace -> drawTrace(trace.points.map { Offset(it.x * size.width, it.y * size.height) }, TRACE_COLORS[trace.colorSlot % TRACE_COLORS.size]) }
        // A finger down now shows a dot at the end of its newest trace.
        session.active.keys.mapNotNull { id -> session.traces.lastOrNull { it.pointerId == id } }.forEach { trace ->
            trace.points.lastOrNull()?.let { drawCircle(TRACE_COLORS[trace.colorSlot % TRACE_COLORS.size], 12.dp.toPx(), Offset(it.x * size.width, it.y * size.height)) }
        }
    }
}

private fun DrawScope.drawTrace(points: List<Offset>, color: Color) {
    val first = points.firstOrNull() ?: return
    val path = Path().apply {
        moveTo(first.x, first.y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, color, style = Stroke(width = 4.dp.toPx()))
    drawCircle(color, 4.dp.toPx(), first)
}

/** Most fingers at once, and each button's clicks, lit while held. */
@Composable
private fun TouchSummary(session: TouchSession) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.touch_max_fingers, session.maxFingers), Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodyMedium)
        PointerButton.entries.forEach { button -> ButtonChip(button, session) }
    }
}

@Composable
private fun ButtonChip(button: PointerButton, session: TouchSession) {
    val colors = MaterialTheme.colorScheme
    val held = button in session.held
    Surface(color = colors.primary.takeIf { held } ?: colors.surfaceContainerHighest, contentColor = colors.onPrimary.takeIf { held } ?: colors.onSurface, shape = RoundedCornerShape(8.dp)) {
        Text(
            stringResource(R.string.click_count, stringResource(Texts.POINTER_BUTTONS.getValue(button)), session.clicks[button] ?: 0),
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** A tester's dialog: title, hint, the tester and a close button. It takes the keys while open. */
@Composable
private fun TesterDialog(
    title: Int,
    hint: String,
    onKey: (KeyEvent) -> Boolean,
    onClose: () -> Unit,
    frame: Modifier = Modifier.fillMaxWidth(0.9f),
    content: @Composable ColumnScope.() -> Unit,
) {
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Surface(
            frame.focusRequester(focus).focusable().onPreviewKeyEvent { onKey(it.nativeKeyEvent) },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                content()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text(stringResource(R.string.tester_close)) }
                }
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}
