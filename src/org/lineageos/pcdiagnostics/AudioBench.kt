package org.lineageos.pcdiagnostics

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lineageos.pcdiagnostics.core.AudioDeviceDescription
import org.lineageos.pcdiagnostics.core.AudioDevices
import org.lineageos.pcdiagnostics.core.AudioLevel
import org.lineageos.pcdiagnostics.core.MicrophoneTest
import org.lineageos.pcdiagnostics.core.SpeakerChannel
import org.lineageos.pcdiagnostics.core.SpeakerTest
import org.lineageos.pcdiagnostics.core.TestTone

/** What the microphone card shows: recording or not, the live level, and whether there is a take to play. */
data class RecorderState(
    val isRecording: Boolean = false,
    val level: AudioLevel = AudioLevel(AudioLevel.FLOOR_DBFS, AudioLevel.FLOOR_DBFS),
    val hasRecording: Boolean = false,
)

/** The audio page's state: devices and the chosen ones, test tones, the recorder, and the two suites. */
class AudioBench(private val application: Application, private val scope: CoroutineScope) {
    private val reader = AudioReader(application)

    val devices: StateFlow<List<AudioDeviceDescription>> = reader.watchDevices().map(AudioDevices::buildList)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _selected = MutableStateFlow<Map<Boolean, Int>>(emptyMap())

    /** The chosen device id per direction: true for outputs, false for inputs. */
    val selected: StateFlow<Map<Boolean, Int>> = _selected.asStateFlow()

    private val _recorder = MutableStateFlow(RecorderState())
    val recorder: StateFlow<RecorderState> = _recorder.asStateFlow()

    private val stopRecording = AtomicBoolean(false)
    @Volatile
    private var recording = ShortArray(0)

    val speakerSuite = SuiteController(scope) { listOf(SpeakerTest(buildPlayer(), readChannels())) }
    val microphoneSuite = SuiteController(scope) { listOf(MicrophoneTest(buildRecorder(), ::hasRecordPermission)) }

    fun onSelected(device: AudioDeviceDescription) = _selected.update { it + (device.isOutput to device.id) }

    fun hasRecordPermission() = application.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** The channels of the chosen output: left, right and the rest of its widest mask. */
    fun readChannels(): List<SpeakerChannel> = SpeakerChannel.chooseChannels(findSelected(isOutput = true)?.channelMasks.orEmpty())

    fun playTone(channel: SpeakerChannel) {
        val channels = readChannels()
        scope.launch(Dispatchers.IO) {
            val tone = TestTone.build(TestTone.DEFAULT_FREQUENCY_HZ, TONE_SAMPLE_RATE, TONE_MILLIS, channels.size, channels.indexOf(channel))
            buildPlayer()?.play(tone, TONE_SAMPLE_RATE, channels)
        }
    }

    fun startRecording() {
        if (!hasRecordPermission() || _recorder.value.isRecording) return
        val microphone = buildRecorder() ?: return
        stopRecording.set(false)
        _recorder.value = RecorderState(isRecording = true)
        scope.launch(Dispatchers.IO) {
            val samples = microphone.recordUntil(MAX_RECORD_MILLIS, stopRecording::get) { chunk, count -> _recorder.update { it.copy(level = AudioLevel.measure(chunk, count)) } }
            recording = samples ?: ShortArray(0)
            _recorder.value = RecorderState(hasRecording = recording.isNotEmpty())
        }
    }

    fun stopRecording() = stopRecording.set(true)

    fun playRecording() {
        val take = recording.takeIf { it.isNotEmpty() } ?: return
        scope.launch(Dispatchers.IO) { buildPlayer()?.play(take, AndroidSoundRecorder.SAMPLE_RATE, listOf(SpeakerChannel.FRONT_LEFT)) }
    }

    private fun findSelected(isOutput: Boolean) = AudioDevices.findSelected(AudioDevices.buildList(reader.readDevices()), isOutput, selected.value[isOutput])

    private fun buildPlayer(): AndroidTonePlayer? = findSelected(isOutput = true)?.let { AndroidTonePlayer(reader, reader.findDevice(it.id)) }

    private fun buildRecorder(): AndroidSoundRecorder? = findSelected(isOutput = false)?.let { AndroidSoundRecorder(reader.findDevice(it.id)) }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TONE_SAMPLE_RATE = 48_000
        const val TONE_MILLIS = 700
        const val MAX_RECORD_MILLIS = 10_000
    }
}
