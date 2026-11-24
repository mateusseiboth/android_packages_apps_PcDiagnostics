package org.lineageos.pcdiagnostics

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.lineageos.pcdiagnostics.core.InputDeviceInfo
import org.lineageos.pcdiagnostics.core.InputDevices
import org.lineageos.pcdiagnostics.core.KeyCap
import org.lineageos.pcdiagnostics.core.KeyboardLayout
import org.lineageos.pcdiagnostics.core.TouchPoint

/** The input devices InputManager knows, now and as they come and go. */
class InputReader(context: Context) {
    private val inputManager = context.getSystemService(InputManager::class.java)

    fun readDevices(): List<InputDeviceInfo> = inputManager.inputDeviceIds.toList().mapNotNull(inputManager::getInputDevice).map(::describe)

    fun watchDevices(): Flow<List<InputDeviceInfo>> = callbackFlow {
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                trySend(readDevices())
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                trySend(readDevices())
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                trySend(readDevices())
            }
        }
        send(readDevices())
        inputManager.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        awaitClose { inputManager.unregisterInputDeviceListener(listener) }
    }

    private fun describe(device: InputDevice) = InputDeviceInfo(
        id = device.id,
        name = device.name,
        sources = device.sources,
        hasAlphabeticKeys = device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC,
        vendorId = device.vendorId,
        productId = device.productId,
        isExternal = device.isExternal,
        isVirtual = device.isVirtual,
    )
}

/**
 * The legends of the device's own layout (an ABNT2 keyboard shows Ç where a US one has a semicolon), for
 * the keys that print one character.
 */
fun readKeyLabels(deviceId: Int, layout: KeyboardLayout): Map<KeyCap, String> {
    val device = InputDevice.getDevice(deviceId) ?: return emptyMap()
    val characters = device.keyCharacterMap
    return layout.keys.filter { it.label.isPrintable() }
        .associateWith { key -> characters.getDisplayLabel(device.getKeyCodeForKeyLocation(key.keyCode)) }
        .filterValues { it != NO_LABEL }
        .mapValues { (_, label) -> label.toString() }
}

private const val NO_LABEL = 0.toChar()

private fun String.isPrintable() = length == 1 && isNotBlank()

/** The fingers down in [event], at 0 to 1 within [rangeX] and [rangeY]; a finger going up is left out. */
fun readPointers(event: MotionEvent, rangeX: ClosedFloatingPointRange<Float>, rangeY: ClosedFloatingPointRange<Float>): List<TouchPoint> {
    val lifted = findLiftedIndexes(event)
    return (0 until event.pointerCount).filterNot { it in lifted }.map { index ->
        TouchPoint(
            id = event.getPointerId(index),
            x = InputDevices.normalize(event.getX(index), rangeX.start, rangeX.endInclusive),
            y = InputDevices.normalize(event.getY(index), rangeY.start, rangeY.endInclusive),
        )
    }
}

private fun findLiftedIndexes(event: MotionEvent): Set<Int> = when (event.actionMasked) {
    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> (0 until event.pointerCount).toSet()
    MotionEvent.ACTION_POINTER_UP -> setOf(event.actionIndex)
    else -> emptySet()
}

/** The range an axis of the event's device reports in, for normalizing its positions. */
fun readAxisRange(event: MotionEvent, axis: Int): ClosedFloatingPointRange<Float> {
    val range = event.device?.getMotionRange(axis, event.source) ?: return 0f..1f
    return range.min..range.max
}

/** Width over height of a touchpad's surface, from its axis ranges; a usual laptop shape when unknown. */
fun readTouchpadAspect(deviceId: Int): Float {
    val device = InputDevice.getDevice(deviceId) ?: return DEFAULT_TOUCHPAD_ASPECT
    val width = device.getMotionRange(MotionEvent.AXIS_X, InputDevice.SOURCE_TOUCHPAD)?.range ?: return DEFAULT_TOUCHPAD_ASPECT
    val height = device.getMotionRange(MotionEvent.AXIS_Y, InputDevice.SOURCE_TOUCHPAD)?.range?.takeIf { it > 0 } ?: return DEFAULT_TOUCHPAD_ASPECT
    return (width / height).coerceIn(1f, 3f)
}

private const val DEFAULT_TOUCHPAD_ASPECT = 1.6f
