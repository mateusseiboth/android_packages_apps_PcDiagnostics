package org.lineageos.pcdiagnostics.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputChecksTest {
    private val notCanceled = CancelSignal { false }

    private fun device(name: String, sources: Int, alphabetic: Boolean = false, external: Boolean = false, virtual: Boolean = false) =
        InputDeviceInfo(id = name.hashCode(), name = name, sources = sources, hasAlphabeticKeys = alphabetic, vendorId = 0x046d, productId = 0xc52b, isExternal = external, isVirtual = virtual)

    private val keyboard = device("AT Translated Set 2 keyboard", InputSources.KEYBOARD, alphabetic = true)
    private val touchpad = device("ELAN Touchpad", InputSources.MOUSE or InputSources.TOUCHPAD)
    private val touchscreen = device("Goodix Touchscreen", InputSources.TOUCHSCREEN or InputSources.STYLUS)
    private val mouse = device("USB Optical Mouse", InputSources.MOUSE, external = true)
    private val powerButton = device("Power Button", InputSources.KEYBOARD)

    @Test
    fun `devices are classified by their sources`() {
        assertEquals(InputKind.KEYBOARD, keyboard.kind)
        assertEquals(InputKind.TOUCHPAD, touchpad.kind)
        assertEquals(InputKind.TOUCHSCREEN, touchscreen.kind)
        assertEquals(InputKind.MOUSE, mouse.kind)
        assertEquals(InputKind.MOUSE, device("Captured mouse", InputSources.MOUSE_RELATIVE).kind)
        assertEquals(InputKind.OTHER, powerButton.kind)
    }

    @Test
    fun `a receiver with keys and a mouse is a keyboard that also points`() {
        val combo = device("Unifying Receiver", InputSources.KEYBOARD or InputSources.MOUSE, alphabetic = true, external = true)

        assertEquals(InputKind.KEYBOARD, combo.kind)
        assertTrue(combo.isKeyboard)
        assertTrue(combo.isPointer)
    }

    @Test
    fun `a touchpad points but is not a keyboard`() {
        assertTrue(touchpad.isPointer)
        assertFalse(touchpad.isKeyboard)
        assertFalse(powerButton.isKeyboard)
    }

    @Test
    fun `vendor and product ids read as four hex digits`() {
        assertEquals("046d:c52b", keyboard.usbId)
        assertNull(keyboard.copy(vendorId = 0, productId = 0).usbId)
    }

    @Test
    fun `the list leaves virtual devices out and groups by kind`() {
        val virtual = device("Virtual", InputSources.KEYBOARD, alphabetic = true, virtual = true)

        assertEquals(listOf(keyboard, touchpad, touchscreen, mouse, powerButton), InputDevices.buildList(listOf(powerButton, mouse, virtual, touchscreen, touchpad, keyboard)))
    }

    @Test
    fun `positions are scaled to the device range`() {
        assertEquals(0.5f, InputDevices.normalize(1500f, 0f, 3000f), 0.0001f)
        assertEquals(0f, InputDevices.normalize(-10f, 0f, 3000f), 0f)
        assertEquals(1f, InputDevices.normalize(4000f, 0f, 3000f), 0f)
        assertEquals(0f, InputDevices.normalize(5f, 7f, 7f), 0f)
    }

    @Test
    fun `devices test needs a keyboard and something to point with`() {
        assertEquals(Passed(Finding(FindingKey.INPUT_DEVICES_FOUND, listOf(2))), InputDevicesTest { listOf(keyboard, touchpad) }.run(notCanceled))
        assertEquals(Passed(Finding(FindingKey.INPUT_DEVICES_FOUND, listOf(2))), InputDevicesTest { listOf(keyboard, touchscreen) }.run(notCanceled))
        assertEquals(Failed(Finding(FindingKey.INPUT_NO_KEYBOARD)), InputDevicesTest { listOf(touchpad, powerButton) }.run(notCanceled))
        assertEquals(Warning(Finding(FindingKey.INPUT_NO_POINTER)), InputDevicesTest { listOf(keyboard, powerButton) }.run(notCanceled))
    }

    @Test
    fun `keys test reports the keys not pressed in the tester`() {
        val layout = KeyboardLayout.US_ANSI
        val all = layout.keys.fold(KeyboardState()) { state, key -> state.onKeyDown(layout, key.scanCode, key.keyCode) }
        val one = KeyboardState().onKeyDown(layout, 30, 29)

        assertEquals(Warning(Finding(FindingKey.INPUT_KEYS_NOT_TESTED)), KeyCoverageTest(layout) { null }.run(notCanceled))
        assertEquals(Warning(Finding(FindingKey.INPUT_KEYS_NOT_TESTED)), KeyCoverageTest(layout) { KeyboardState() }.run(notCanceled))
        assertEquals(Warning(Finding(FindingKey.INPUT_KEYS_MISSED, listOf(layout.keys.size - 1))), KeyCoverageTest(layout) { one }.run(notCanceled))
        assertEquals(Passed(Finding(FindingKey.INPUT_KEYS_ALL_PRESSED, listOf(layout.keys.size))), KeyCoverageTest(layout) { all }.run(notCanceled))
    }

    @Test
    fun `the input suite checks the devices, then the keys`() {
        assertEquals(
            listOf(TestName.INPUT_DEVICES, TestName.INPUT_KEYBOARD_KEYS),
            InputSuite.buildTests({ listOf(keyboard) }, KeyboardLayout.US_ANSI) { null }.map { it.name },
        )
    }
}
