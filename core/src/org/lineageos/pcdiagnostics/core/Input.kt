package org.lineageos.pcdiagnostics.core

enum class InputKind { KEYBOARD, TOUCHPAD, TOUCHSCREEN, MOUSE, OTHER }

/** InputDevice source masks (class bits included), so the core can classify without Android. */
object InputSources {
    const val KEYBOARD = 0x101
    const val TOUCHSCREEN = 0x1002
    const val MOUSE = 0x2002
    const val STYLUS = 0x4002
    const val MOUSE_RELATIVE = 0x20004
    const val TOUCHPAD = 0x100008

    fun hasAll(sources: Int, mask: Int) = sources and mask == mask

    fun hasAny(sources: Int, vararg masks: Int) = masks.any { hasAll(sources, it) }
}

/** An input device as InputManager describes it. */
data class InputDeviceInfo(
    val id: Int,
    val name: String,
    val sources: Int,
    val hasAlphabeticKeys: Boolean,
    val vendorId: Int,
    val productId: Int,
    val isExternal: Boolean,
    val isVirtual: Boolean = false,
) {
    val kind: InputKind get() = KIND_RULES.first { (matches, _) -> matches(this) }.second

    /** A keyboard with letters: power buttons and media keys send keys too, but are not keyboards. */
    val isKeyboard get() = hasAlphabeticKeys && InputSources.hasAll(sources, InputSources.KEYBOARD)

    val isPointer get() = InputSources.hasAny(sources, InputSources.TOUCHPAD, InputSources.MOUSE, InputSources.MOUSE_RELATIVE, InputSources.TOUCHSCREEN)

    /** Vendor and product ids as "046d:c52b", or null when the device has none. */
    val usbId: String? get() = "%04x:%04x".format(vendorId, productId).takeUnless { vendorId == 0 && productId == 0 }
}

/** The first rule that matches gives the kind: touchpads also report a mouse source, so they come first. */
private val KIND_RULES: List<Pair<(InputDeviceInfo) -> Boolean, InputKind>> = listOf(
    { device: InputDeviceInfo -> InputSources.hasAll(device.sources, InputSources.TOUCHSCREEN) } to InputKind.TOUCHSCREEN,
    { device: InputDeviceInfo -> InputSources.hasAll(device.sources, InputSources.TOUCHPAD) } to InputKind.TOUCHPAD,
    { device: InputDeviceInfo -> device.isKeyboard } to InputKind.KEYBOARD,
    { device: InputDeviceInfo -> InputSources.hasAny(device.sources, InputSources.MOUSE, InputSources.MOUSE_RELATIVE) } to InputKind.MOUSE,
    { _: InputDeviceInfo -> true } to InputKind.OTHER,
)

object InputDevices {
    /** Real devices only, keyboards first, then touchpads, touchscreens, mice and the rest, by name. */
    fun buildList(devices: List<InputDeviceInfo>): List<InputDeviceInfo> =
        devices.filterNot { it.isVirtual }.sortedWith(compareBy({ it.kind.ordinal }, { it.name }))

    /** A position within the device's [min] to [max] range, from 0 to 1. */
    fun normalize(value: Float, min: Float, max: Float): Float {
        if (max <= min) return 0f
        return ((value - min) / (max - min)).coerceIn(0f, 1f)
    }
}

/** A PC needs a keyboard and something to point with: a touchpad, a mouse or a touchscreen. */
class InputDevicesTest(private val readDevices: () -> List<InputDeviceInfo>) : DiagnosticTest {
    override val name = TestName.INPUT_DEVICES

    override fun run(cancel: CancelSignal): TestOutcome {
        val devices = InputDevices.buildList(readDevices())
        if (devices.none { it.isKeyboard }) return Failed(Finding(FindingKey.INPUT_NO_KEYBOARD))
        if (devices.none { it.isPointer }) return Warning(Finding(FindingKey.INPUT_NO_POINTER))
        return Passed(Finding(FindingKey.INPUT_DEVICES_FOUND, listOf(devices.size)))
    }
}

/** Judges the last keyboard tester session: every key of [layout] should have been pressed. */
class KeyCoverageTest(private val layout: KeyboardLayout, private val readState: () -> KeyboardState?) : DiagnosticTest {
    override val name = TestName.INPUT_KEYBOARD_KEYS

    override fun run(cancel: CancelSignal): TestOutcome {
        val state = readState()?.takeIf { it.tested.isNotEmpty() } ?: return Warning(Finding(FindingKey.INPUT_KEYS_NOT_TESTED))
        val missing = state.findMissing(layout)
        if (missing.isNotEmpty()) return Warning(Finding(FindingKey.INPUT_KEYS_MISSED, listOf(missing.size)))
        return Passed(Finding(FindingKey.INPUT_KEYS_ALL_PRESSED, listOf(layout.keys.size)))
    }
}

object InputSuite {
    fun buildTests(readDevices: () -> List<InputDeviceInfo>, layout: KeyboardLayout, readKeys: () -> KeyboardState?): List<DiagnosticTest> =
        listOf(InputDevicesTest(readDevices), KeyCoverageTest(layout, readKeys))
}
