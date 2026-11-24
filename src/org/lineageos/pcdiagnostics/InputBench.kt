package org.lineageos.pcdiagnostics

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.lineageos.pcdiagnostics.core.InputDeviceInfo
import org.lineageos.pcdiagnostics.core.InputDevices
import org.lineageos.pcdiagnostics.core.InputSuite
import org.lineageos.pcdiagnostics.core.KeyboardLayout
import org.lineageos.pcdiagnostics.core.KeyboardState

/** The input page's state: the devices, the keyboard tester's session and layout, and the suite. */
class InputBench(application: Application, scope: CoroutineScope) {
    private val reader = InputReader(application)

    val devices: StateFlow<List<InputDeviceInfo>> = reader.watchDevices().map(InputDevices::buildList)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _layout = MutableStateFlow(KeyboardLayout.chooseFor(application.resources.configuration.locales[0].country))
    val layout: StateFlow<KeyboardLayout> = _layout.asStateFlow()

    /** The last keyboard tester session; null until the tester opens. */
    private val _keyboard = MutableStateFlow<KeyboardState?>(null)
    val keyboard: StateFlow<KeyboardState?> = _keyboard.asStateFlow()

    val suite = SuiteController(scope) { InputSuite.buildTests(reader::readDevices, layout.value) { keyboard.value } }

    fun onLayoutSelected(layout: KeyboardLayout) {
        _layout.value = layout
    }

    fun startKeyboardSession() {
        _keyboard.value = KeyboardState()
    }

    fun onKeyDown(scanCode: Int, keyCode: Int) = _keyboard.update { (it ?: KeyboardState()).onKeyDown(layout.value, scanCode, keyCode) }

    fun onKeyUp(scanCode: Int, keyCode: Int) = _keyboard.update { (it ?: KeyboardState()).onKeyUp(layout.value, scanCode, keyCode) }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
