package org.lineageos.pcdiagnostics.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {
    private val layout = KeyboardLayout.US_ANSI

    /** The leftmost key with [label] (Shift, Ctrl and Alt come in pairs). */
    private fun key(label: String) = layout.keys.first { it.label == label }

    @Test
    fun `every row fills the keyboard width`() {
        KeyboardLayout.ALL.forEach { layout ->
            layout.rows.forEach { row -> assertEquals(layout.widthUnits, row.sumOf { it.widthUnits.toDouble() }.toFloat(), 0.001f) }
            assertEquals(15f, layout.widthUnits, 0.001f)
            assertEquals(6, layout.heightUnits)
        }
    }

    @Test
    fun `cells sit side by side without overlapping`() {
        KeyboardLayout.ALL.flatMap { layout -> layout.cells.groupBy { it.y }.values }.forEach { row ->
            row.sortedBy { it.x }.zipWithNext().forEach { (left, right) -> assertTrue("${left.key.label} overlaps ${right.key.label}", left.x + left.width <= right.x + 0.001f) }
        }
        KeyboardLayout.ALL.forEach { layout -> layout.cells.forEach { assertTrue(it.x + it.width <= layout.widthUnits + 0.001f) } }
    }

    @Test
    fun `iso enter covers two rows and counts as one key`() {
        val enter = KeyboardLayout.ISO.cells.filter { it.key.label == "↵" }

        assertEquals(listOf(2f, 3f), enter.map { it.y })
        assertEquals(1, enter.map { it.key }.distinct().size)
        assertEquals(KeyboardLayout.ISO.keys.size, KeyboardLayout.ISO.keys.distinct().size)
    }

    @Test
    fun `iso and abnt2 have the extra keys next to shift`() {
        assertNull(KeyboardLayout.US_ANSI.findKey(scanCode = 86, keyCode = 0))
        assertEquals(86, KeyboardLayout.ISO.findKey(scanCode = 86, keyCode = 0)?.scanCode)
        assertNull(KeyboardLayout.ISO.findKey(scanCode = 89, keyCode = 0))
        assertEquals(89, KeyboardLayout.ABNT2.findKey(scanCode = 89, keyCode = 0)?.scanCode)
        assertEquals(KeyboardLayout.US_ANSI.keys.size + 2, KeyboardLayout.ABNT2.keys.size)
    }

    @Test
    fun `the up and down arrows share one place, half height each`() {
        val up = layout.cells.single { it.key.label == "↑" }
        val down = layout.cells.single { it.key.label == "↓" }

        assertEquals(up.x, down.x, 0f)
        assertEquals(0.5f, up.height, 0f)
        assertEquals(up.y + 0.5f, down.y, 0f)
    }

    @Test
    fun `keys are found by scan code first, then by key code`() {
        assertEquals(key("A"), layout.findKey(scanCode = 30, keyCode = 0))
        assertEquals(key("A"), layout.findKey(scanCode = 0, keyCode = 29))
        assertEquals(key("Esc"), layout.findKey(scanCode = 1, keyCode = 111))
        // A remapped key keeps its physical place.
        assertEquals(key("Q"), layout.findKey(scanCode = 16, keyCode = 29))
        assertNull(layout.findKey(scanCode = 0, keyCode = 24))
    }

    @Test
    fun `every key has its own scan code`() {
        KeyboardLayout.ALL.forEach { layout -> assertEquals(layout.keys.size, layout.keys.map { it.scanCode }.toSet().size) }
        // On ANSI the key codes are unique too; ISO sends backslash from two keys.
        assertEquals(layout.keys.size, layout.keys.map { it.keyCode }.toSet().size)
    }

    @Test
    fun `a pressed key lights up and stays tested after release`() {
        val down = KeyboardState().onKeyDown(layout, scanCode = 30, keyCode = 29)
        val up = down.onKeyUp(layout, scanCode = 30, keyCode = 29)

        assertEquals(setOf(key("A")), down.pressed)
        assertEquals(emptySet<KeyCap>(), up.pressed)
        assertEquals(setOf(key("A")), up.tested)
        assertEquals(29, up.lastKeyCode)
    }

    @Test
    fun `several keys can be held at once`() {
        val state = KeyboardState()
            .onKeyDown(layout, 42, 59)
            .onKeyDown(layout, 30, 29)
            .onKeyDown(layout, 31, 47)
            .onKeyUp(layout, 30, 29)

        assertEquals(setOf(key("Shift"), key("S")), state.pressed)
        assertEquals(3, state.tested.size)
    }

    @Test
    fun `a key outside the layout is remembered but tests nothing`() {
        val state = KeyboardState().onKeyDown(layout, scanCode = 115, keyCode = 24)

        assertEquals(emptySet<KeyCap>(), state.tested)
        assertEquals(24, state.lastKeyCode)
    }

    @Test
    fun `the missing keys are the ones never pressed`() {
        val state = KeyboardState().onKeyDown(layout, 30, 29)

        assertEquals(layout.keys.size - 1, state.findMissing(layout).size)
        assertFalse(key("A") in state.findMissing(layout))
    }

    @Test
    fun `a key is pressed while held, tested after, untested before`() {
        val state = KeyboardState().onKeyDown(layout, 30, 29).onKeyUp(layout, 30, 29).onKeyDown(layout, 31, 47)

        assertEquals(KeyStatus.TESTED, state.statusOf(key("A")))
        assertEquals(KeyStatus.PRESSED, state.statusOf(key("S")))
        assertEquals(KeyStatus.UNTESTED, state.statusOf(key("D")))
    }

    @Test
    fun `the layout follows the country`() {
        assertEquals(KeyboardLayout.ABNT2, KeyboardLayout.chooseFor("BR"))
        assertEquals(KeyboardLayout.ISO, KeyboardLayout.chooseFor("PT"))
        assertEquals(KeyboardLayout.ISO, KeyboardLayout.chooseFor("gb"))
        assertEquals(KeyboardLayout.US_ANSI, KeyboardLayout.chooseFor("US"))
        assertEquals(KeyboardLayout.US_ANSI, KeyboardLayout.chooseFor(""))
    }

    @Test
    fun `alt and escape close the tester, escape alone is a key`() {
        assertTrue(KeyboardLayout.isExitChord(keyCode = 111, isAltPressed = true))
        assertFalse(KeyboardLayout.isExitChord(keyCode = 111, isAltPressed = false))
        assertFalse(KeyboardLayout.isExitChord(keyCode = 29, isAltPressed = true))
    }
}
