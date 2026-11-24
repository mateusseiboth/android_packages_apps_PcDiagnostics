package org.lineageos.pcdiagnostics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.lineageos.pcdiagnostics.DiagnosticsViewModel
import org.lineageos.pcdiagnostics.InputBench
import org.lineageos.pcdiagnostics.R
import org.lineageos.pcdiagnostics.SuiteKind
import org.lineageos.pcdiagnostics.core.InputDeviceInfo
import org.lineageos.pcdiagnostics.core.InputKind

/** Devices one tester checks: the card's title, the text when there is none, and the tester. */
private class TesterGroup(
    val title: Int,
    val empty: Int,
    val accepts: (InputDeviceInfo) -> Boolean,
    val tester: @Composable (InputDeviceInfo, InputBench, onClose: () -> Unit) -> Unit,
)

private val TESTER_GROUPS = listOf(
    TesterGroup(R.string.input_keyboard, R.string.input_no_keyboard, InputDeviceInfo::isKeyboard) { device, bench, onClose -> KeyboardTester(device, bench, onClose) },
    TesterGroup(R.string.input_touchpad, R.string.input_no_touchpad, { it.kind == InputKind.TOUCHPAD }) { device, _, onClose -> TouchpadTester(device, onClose) },
    TesterGroup(R.string.input_touchscreen, R.string.input_no_touchscreen, { it.kind == InputKind.TOUCHSCREEN }) { device, _, onClose ->
        TouchscreenTester(device, onClose)
    },
)

/** The tester on screen and the device it listens to. */
private class OpenTester(val group: TesterGroup, val device: InputDeviceInfo)

/** Keyboards, touchpads and touchscreens with their testers, then every input device and the suite. */
@Composable
fun InputPage(viewModel: DiagnosticsViewModel) {
    val bench = viewModel.input
    val devices by bench.devices.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<OpenTester?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TESTER_GROUPS.forEach { group -> TesterCard(group, devices.filter(group.accepts)) { open = OpenTester(group, it) } }
        DevicesCard(devices, viewModel)
    }
    open?.let { tester -> tester.group.tester(tester.device, bench) { open = null } }
}

@Composable
private fun TesterCard(group: TesterGroup, devices: List<InputDeviceInfo>, onTest: (InputDeviceInfo) -> Unit) {
    val rows: List<@Composable ColumnScope.() -> Unit> = devices.map { device -> { DeviceRow(device) { onTest(device) } } }
    val empty: @Composable ColumnScope.() -> Unit = { MessageRow(stringResource(group.empty)) }
    InformationGroup(title = stringResource(group.title), rows = rows.ifEmpty { listOf(empty) })
}

@Composable
private fun DeviceRow(device: InputDeviceInfo, onTest: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        InformationCell(device.name, stringResource(Texts.CONNECTIONS.getValue(device.isExternal)), Modifier.weight(1f))
        FilledTonalButton(onClick = onTest) { Text(stringResource(R.string.input_test)) }
    }
}

@Composable
private fun DevicesCard(devices: List<InputDeviceInfo>, viewModel: DiagnosticsViewModel) {
    val rows: List<@Composable ColumnScope.() -> Unit> = devices.map { device -> { InformationCell(device.name, describeDevice(device)) } }
    val suite: @Composable ColumnScope.() -> Unit = { SuiteRowFor(viewModel.suites.getValue(SuiteKind.INPUT), stringResource(R.string.run_input)) }
    InformationGroup(title = stringResource(R.string.input_devices), rows = rows + listOf(suite))
}

/** "Keyboard, External, ID 046d:c52b". */
@Composable
private fun describeDevice(device: InputDeviceInfo): String = listOfNotNull(
    stringResource(Texts.INPUT_KINDS.getValue(device.kind)),
    stringResource(Texts.CONNECTIONS.getValue(device.isExternal)),
    device.usbId?.let { stringResource(R.string.input_ids, it) },
).joinToString(", ")

/** A row with one sentence, for cards with nothing to list. */
@Composable
fun MessageRow(text: String) {
    Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
