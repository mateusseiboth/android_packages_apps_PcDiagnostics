package org.lineageos.pcdiagnostics.ui

import android.Manifest
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import org.lineageos.pcdiagnostics.AudioBench
import org.lineageos.pcdiagnostics.DiagnosticsViewModel
import org.lineageos.pcdiagnostics.R
import org.lineageos.pcdiagnostics.RecorderState
import org.lineageos.pcdiagnostics.SuiteKind
import org.lineageos.pcdiagnostics.core.AudioDeviceDescription
import org.lineageos.pcdiagnostics.core.AudioDevices
import org.lineageos.pcdiagnostics.core.SpeakerChannel

/** A row of a card. */
private typealias GroupRow = @Composable ColumnScope.() -> Unit

/** The recorder's main button: record while idle, stop while recording. */
private class RecordAction(val label: Int, val act: (AudioBench) -> Unit)

private val RECORD_ACTIONS = mapOf(
    false to RecordAction(R.string.audio_record, AudioBench::startRecording),
    true to RecordAction(R.string.audio_stop, AudioBench::stopRecording),
)

/** Speakers (outputs, a tone per channel) and microphone (inputs, recorder with a level meter), each with its test. */
@Composable
fun AudioPage(viewModel: DiagnosticsViewModel, onOpen: (Intent) -> Unit) {
    val bench = viewModel.audio
    val devices by bench.devices.collectAsStateWithLifecycle()
    val selected by bench.selected.collectAsStateWithLifecycle()
    val output = AudioDevices.findSelected(devices, isOutput = true, id = selected[true])
    val input = AudioDevices.findSelected(devices, isOutput = false, id = selected[false])
    val tones: List<GroupRow> = listOfNotNull(output).map { device -> { ToneRow(device, bench) } }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InformationGroup(
            title = stringResource(R.string.audio_speakers),
            rows = buildDeviceRows(devices.filter { it.isOutput }, output, R.string.audio_no_output, bench) + tones +
                listOf<GroupRow>({ SuiteRowFor(viewModel.suites.getValue(SuiteKind.SPEAKERS), stringResource(R.string.run_speakers)) }),
        )
        MicrophoneCard(devices.filter { !it.isOutput }, input, viewModel, onOpen)
    }
}

@Composable
private fun MicrophoneCard(inputs: List<AudioDeviceDescription>, input: AudioDeviceDescription?, viewModel: DiagnosticsViewModel, onOpen: (Intent) -> Unit) {
    val bench = viewModel.audio
    val permission = rememberPermission(Manifest.permission.RECORD_AUDIO)
    val recorder by bench.recorder.collectAsStateWithLifecycle()
    val access: GroupRow = { PermissionRow(permission, R.string.audio_permission_needed, R.string.audio_permission_denied, onOpen) }
    val record: GroupRow = { RecorderRow(recorder, bench) }
    InformationGroup(
        title = stringResource(R.string.audio_microphone),
        rows = buildDeviceRows(inputs, input, R.string.audio_no_input, bench) +
            listOfNotNull(input).map { record.takeIf { permission.isGranted } ?: access } +
            listOf<GroupRow>({ SuiteRowFor(viewModel.suites.getValue(SuiteKind.MICROPHONE), stringResource(R.string.run_microphone)) }),
    )
}

/** One row per device of a direction, the chosen one marked; a sentence when there is none. */
@Composable
private fun buildDeviceRows(devices: List<AudioDeviceDescription>, chosen: AudioDeviceDescription?, empty: Int, bench: AudioBench): List<GroupRow> {
    val rows = devices.map<AudioDeviceDescription, GroupRow> { device -> { DeviceChoice(device, device == chosen) { bench.onSelected(device) } } }
    val none: GroupRow = { MessageRow(stringResource(empty)) }
    return rows.ifEmpty { listOf(none) }
}

@Composable
private fun DeviceChoice(device: AudioDeviceDescription, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        InformationCell(device.name, stringResource(Texts.AUDIO_KINDS.getValue(device.kind)))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToneRow(output: AudioDeviceDescription, bench: AudioBench) {
    val channels = remember(output) { SpeakerChannel.chooseChannels(output.channelMasks) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.audio_test_tone), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            channels.forEach { channel ->
                OutlinedButton(onClick = { bench.playTone(channel) }) { Text(stringResource(Texts.SPEAKER_CHANNELS.getValue(channel))) }
            }
        }
    }
}

@Composable
private fun RecorderRow(state: RecorderState, bench: AudioBench) {
    val action = RECORD_ACTIONS.getValue(state.isRecording)
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.audio_record_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.audio_input_level), style = MaterialTheme.typography.labelLarge)
            UsageBar(state.level.meterFraction(), Modifier.weight(1f))
            Text(stringResource(R.string.audio_level, state.level.rmsDbfs.roundToInt()), style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { action.act(bench) }) { Text(stringResource(action.label)) }
            OutlinedButton(onClick = bench::playRecording, enabled = state.hasRecording && !state.isRecording) { Text(stringResource(R.string.audio_play_recording)) }
        }
    }
}
