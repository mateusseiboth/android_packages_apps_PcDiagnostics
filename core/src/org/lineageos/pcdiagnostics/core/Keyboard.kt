package org.lineageos.pcdiagnostics.core

/**
 * A key of the on-screen keyboard: its legend, the Linux scan code it sends (its place on the keyboard)
 * and the Android key code of that place on a US keyboard (used when a device sends no scan code).
 */
data class KeyCap(val label: String, val scanCode: Int, val keyCode: Int)

/** A key's rectangle on the drawing, in key units: a letter key is 1 by 1. */
data class KeyCell(val key: KeyCap, val x: Float, val y: Float, val width: Float, val height: Float)

/** A place in a row: one key, or two half-height keys stacked (the up and down arrows). */
sealed interface KeySlot {
    val widthUnits: Float

    fun buildCells(x: Float, y: Float): List<KeyCell>
}

data class SingleKey(val key: KeyCap, override val widthUnits: Float = 1f) : KeySlot {
    override fun buildCells(x: Float, y: Float) = listOf(KeyCell(key, x, y, widthUnits, 1f))
}

data class StackedKeys(val top: KeyCap, val bottom: KeyCap, override val widthUnits: Float = 1f) : KeySlot {
    override fun buildCells(x: Float, y: Float) = listOf(KeyCell(top, x, y, widthUnits, HALF), KeyCell(bottom, x, y + HALF, widthUnits, HALF))

    private companion object {
        const val HALF = 0.5f
    }
}

/** A keyboard drawn as rows of slots, left to right; a key may fill more than one cell (ISO Enter). */
class KeyboardLayout(val rows: List<List<KeySlot>>) {
    val widthUnits: Float = rows.maxOf { row -> row.sumOf { it.widthUnits.toDouble() } }.toFloat()
    val heightUnits: Int = rows.size

    val cells: List<KeyCell> = rows.flatMapIndexed { y, row ->
        row.zip(row.runningFold(0f) { x, slot -> x + slot.widthUnits }).flatMap { (slot, x) -> slot.buildCells(x, y.toFloat()) }
    }

    val keys: List<KeyCap> = cells.map { it.key }.distinct()

    private val byScanCode = keys.associateBy { it.scanCode }
    private val byKeyCode = keys.reversed().associateBy { it.keyCode }

    /** The key at the place [scanCode] names; [keyCode] when the device sends no known scan code. */
    fun findKey(scanCode: Int, keyCode: Int): KeyCap? = byScanCode[scanCode] ?: byKeyCode[keyCode]

    companion object {
        val US_ANSI = KeyboardLayout(
            listOf(
                Keys.TOP_ROW,
                Keys.NUMBER_ROW,
                listOf(SingleKey(Keys.TAB, 1.5f)) + Keys.TOP_LETTERS + listOf(SingleKey(Keys.BACKSLASH, 1.5f)),
                listOf(SingleKey(Keys.CAPS_LOCK, 1.75f)) + Keys.HOME_LETTERS + listOf(SingleKey(Keys.ENTER, 2.25f)),
                listOf(SingleKey(Keys.LEFT_SHIFT, 2.25f)) + Keys.BOTTOM_LETTERS + Keys.SLASH_KEY + listOf(SingleKey(Keys.RIGHT_SHIFT, 2.75f)),
                Keys.SPACE_ROW,
            ),
        )

        /** Enter takes two rows, backslash moves next to it and one more key sits beside the left Shift. */
        val ISO = KeyboardLayout(
            listOf(
                Keys.TOP_ROW,
                Keys.NUMBER_ROW,
                Keys.ISO_TOP_ROW,
                Keys.ISO_HOME_ROW,
                listOf(SingleKey(Keys.LEFT_SHIFT, 1.25f), SingleKey(Keys.EXTRA_102ND)) + Keys.BOTTOM_LETTERS + Keys.SLASH_KEY +
                    listOf(SingleKey(Keys.RIGHT_SHIFT, 2.75f)),
                Keys.SPACE_ROW,
            ),
        )

        /** ISO with one more key before the right Shift (the Brazilian slash key). */
        val ABNT2 = KeyboardLayout(
            listOf(
                Keys.TOP_ROW,
                Keys.NUMBER_ROW,
                Keys.ISO_TOP_ROW,
                Keys.ISO_HOME_ROW,
                listOf(SingleKey(Keys.LEFT_SHIFT, 1.25f), SingleKey(Keys.EXTRA_102ND)) + Keys.BOTTOM_LETTERS + Keys.SLASH_KEY +
                    listOf(SingleKey(Keys.RO), SingleKey(Keys.RIGHT_SHIFT, 1.75f)),
                Keys.SPACE_ROW,
            ),
        )

        val ALL = listOf(US_ANSI, ISO, ABNT2)

        /** Countries whose keyboards are usually ISO; Brazil has its own. */
        private val BY_COUNTRY = mapOf("BR" to ABNT2) +
            listOf("GB", "IE", "PT", "ES", "FR", "BE", "DE", "AT", "CH", "IT", "SE", "NO", "DK", "FI", "IS").associateWith { ISO }

        /** The layout most keyboards sold in [country] (ISO 3166 code) have; US otherwise. */
        fun chooseFor(country: String): KeyboardLayout = BY_COUNTRY[country.uppercase()] ?: US_ANSI

        private const val KEYCODE_ESCAPE = 111

        /** Alt + Esc closes the keyboard tester; Esc alone is a key to test. */
        fun isExitChord(keyCode: Int, isAltPressed: Boolean) = isAltPressed && keyCode == KEYCODE_ESCAPE
    }
}

/** The keys of the layouts, with Linux scan codes (input-event-codes.h) and Android key codes (KeyEvent). */
private object Keys {
    val ESCAPE = KeyCap("Esc", 1, 111)
    val DELETE = KeyCap("Del", 111, 112)
    val GRAVE = KeyCap("`", 41, 68)
    val MINUS = KeyCap("-", 12, 69)
    val EQUALS = KeyCap("=", 13, 70)
    val BACKSPACE = KeyCap("⌫", 14, 67)
    val TAB = KeyCap("Tab", 15, 61)
    val LEFT_BRACKET = KeyCap("[", 26, 71)
    val RIGHT_BRACKET = KeyCap("]", 27, 72)
    val BACKSLASH = KeyCap("\\", 43, 73)
    val CAPS_LOCK = KeyCap("Caps", 58, 115)
    val SEMICOLON = KeyCap(";", 39, 74)
    val APOSTROPHE = KeyCap("'", 40, 75)
    val ENTER = KeyCap("↵", 28, 66)
    val LEFT_SHIFT = KeyCap("Shift", 42, 59)
    val RIGHT_SHIFT = KeyCap("Shift", 54, 60)
    val COMMA = KeyCap(",", 51, 55)
    val PERIOD = KeyCap(".", 52, 56)
    val SLASH = KeyCap("/", 53, 76)
    val EXTRA_102ND = KeyCap("\\", 86, 73)
    val RO = KeyCap("/", 89, 217)
    val LEFT_CTRL = KeyCap("Ctrl", 29, 113)
    val RIGHT_CTRL = KeyCap("Ctrl", 97, 114)
    val META = KeyCap("Meta", 125, 117)
    val LEFT_ALT = KeyCap("Alt", 56, 57)
    val RIGHT_ALT = KeyCap("Alt", 100, 58)
    val SPACE = KeyCap(" ", 57, 62)
    val LEFT = KeyCap("←", 105, 21)
    val UP = KeyCap("↑", 103, 19)
    val DOWN = KeyCap("↓", 108, 20)
    val RIGHT = KeyCap("→", 106, 22)

    private const val KEYCODE_A = 29
    private const val KEYCODE_0 = 7
    private const val KEYCODE_F1 = 131
    private val FUNCTION_SCAN_CODES = (59..68) + listOf(87, 88)

    /** Letters in a row, from the scan code of the first one; Android numbers A to Z in order. */
    private fun buildLetters(letters: String, firstScanCode: Int) =
        letters.mapIndexed { index, letter -> SingleKey(KeyCap("$letter", firstScanCode + index, KEYCODE_A + (letter - 'A'))) }

    private val FUNCTION_KEYS = FUNCTION_SCAN_CODES.mapIndexed { index, scanCode -> SingleKey(KeyCap("F${index + 1}", scanCode, KEYCODE_F1 + index)) }

    // Digits 1 to 9 then 0 sit on scan codes 2 to 11.
    private val DIGITS = "1234567890".mapIndexed { index, digit -> SingleKey(KeyCap("$digit", 2 + index, KEYCODE_0 + (digit - '0'))) }

    val TOP_LETTERS = buildLetters("QWERTYUIOP", 16) + listOf(SingleKey(LEFT_BRACKET), SingleKey(RIGHT_BRACKET))
    val HOME_LETTERS = buildLetters("ASDFGHJKL", 30) + listOf(SingleKey(SEMICOLON), SingleKey(APOSTROPHE))
    val BOTTOM_LETTERS = buildLetters("ZXCVBNM", 44) + listOf(SingleKey(COMMA), SingleKey(PERIOD))
    val SLASH_KEY = listOf(SingleKey(SLASH))

    val TOP_ROW = listOf(SingleKey(ESCAPE, 1.25f)) + FUNCTION_KEYS + listOf(SingleKey(DELETE, 1.75f))
    val NUMBER_ROW = listOf(SingleKey(GRAVE)) + DIGITS + listOf(SingleKey(MINUS), SingleKey(EQUALS), SingleKey(BACKSPACE, 2f))
    val ISO_TOP_ROW = listOf(SingleKey(TAB, 1.5f)) + TOP_LETTERS + listOf(SingleKey(ENTER, 1.5f))
    val ISO_HOME_ROW = listOf(SingleKey(CAPS_LOCK, 1.75f)) + HOME_LETTERS + listOf(SingleKey(BACKSLASH), SingleKey(ENTER, 1.25f))
    val SPACE_ROW = listOf(
        SingleKey(LEFT_CTRL, 1.25f),
        SingleKey(META, 1.25f),
        SingleKey(LEFT_ALT, 1.25f),
        SingleKey(SPACE, 5.75f),
        SingleKey(RIGHT_ALT, 1.25f),
        SingleKey(RIGHT_CTRL, 1.25f),
        SingleKey(LEFT),
        StackedKeys(UP, DOWN),
        SingleKey(RIGHT),
    )
}

enum class KeyStatus { UNTESTED, TESTED, PRESSED }

/** What the keyboard tester shows: the keys held now, every key pressed so far and the last key code. */
data class KeyboardState(val pressed: Set<KeyCap> = emptySet(), val tested: Set<KeyCap> = emptySet(), val lastKeyCode: Int? = null) {
    fun onKeyDown(layout: KeyboardLayout, scanCode: Int, keyCode: Int): KeyboardState {
        val key = layout.findKey(scanCode, keyCode) ?: return copy(lastKeyCode = keyCode)
        return copy(pressed = pressed + key, tested = tested + key, lastKeyCode = keyCode)
    }

    fun onKeyUp(layout: KeyboardLayout, scanCode: Int, keyCode: Int): KeyboardState {
        val key = layout.findKey(scanCode, keyCode) ?: return this
        return copy(pressed = pressed - key)
    }

    fun findMissing(layout: KeyboardLayout): List<KeyCap> = layout.keys.filterNot { it in tested }

    fun statusOf(key: KeyCap): KeyStatus = KeyStatus.PRESSED.takeIf { key in pressed } ?: KeyStatus.TESTED.takeIf { key in tested } ?: KeyStatus.UNTESTED
}
