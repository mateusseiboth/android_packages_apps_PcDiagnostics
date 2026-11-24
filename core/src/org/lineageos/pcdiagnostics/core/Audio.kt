package org.lineageos.pcdiagnostics.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class AudioDeviceKind { BUILT_IN, HEADPHONES, USB, HDMI, BLUETOOTH, LINE, OTHER }

/** AudioDeviceInfo types, so the core can group devices without Android. */
object AudioDeviceTypes {
    const val UNKNOWN = 0
    const val BUILTIN_EARPIECE = 1
    const val BUILTIN_SPEAKER = 2
    const val WIRED_HEADSET = 3
    const val WIRED_HEADPHONES = 4
    const val LINE_ANALOG = 5
    const val LINE_DIGITAL = 6
    const val BLUETOOTH_SCO = 7
    const val BLUETOOTH_A2DP = 8
    const val HDMI = 9
    const val HDMI_ARC = 10
    const val USB_DEVICE = 11
    const val USB_ACCESSORY = 12
    const val FM = 14
    const val BUILTIN_MIC = 15
    const val FM_TUNER = 16
    const val TV_TUNER = 17
    const val TELEPHONY = 18
    const val AUX_LINE = 19
    const val IP = 20
    const val BUS = 21
    const val USB_HEADSET = 22
    const val HEARING_AID = 23
    const val BUILTIN_SPEAKER_SAFE = 24
    const val REMOTE_SUBMIX = 25
    const val BLE_HEADSET = 26
    const val BLE_SPEAKER = 27
    const val ECHO_REFERENCE = 28
    const val HDMI_EARC = 29
    const val BLE_BROADCAST = 30

    private val KINDS: Map<Int, AudioDeviceKind> =
        listOf(BUILTIN_EARPIECE, BUILTIN_SPEAKER, BUILTIN_MIC).associateWith { AudioDeviceKind.BUILT_IN } +
            listOf(WIRED_HEADSET, WIRED_HEADPHONES).associateWith { AudioDeviceKind.HEADPHONES } +
            listOf(USB_DEVICE, USB_ACCESSORY, USB_HEADSET).associateWith { AudioDeviceKind.USB } +
            listOf(HDMI, HDMI_ARC, HDMI_EARC).associateWith { AudioDeviceKind.HDMI } +
            listOf(BLUETOOTH_SCO, BLUETOOTH_A2DP, HEARING_AID, BLE_HEADSET, BLE_SPEAKER, BLE_BROADCAST).associateWith { AudioDeviceKind.BLUETOOTH } +
            listOf(LINE_ANALOG, LINE_DIGITAL, AUX_LINE).associateWith { AudioDeviceKind.LINE }

    /** Endpoints the system uses for itself (casting, calls, tuners, echo cancelling): nothing to test. */
    private val HIDDEN = setOf(UNKNOWN, FM, FM_TUNER, TV_TUNER, TELEPHONY, IP, BUS, BUILTIN_SPEAKER_SAFE, REMOTE_SUBMIX, ECHO_REFERENCE)

    fun classify(type: Int): AudioDeviceKind = KINDS[type] ?: AudioDeviceKind.OTHER

    fun isShown(type: Int) = type !in HIDDEN
}

/** An audio device as AudioManager describes it; [channelMasks] are its output channel masks. */
data class AudioDeviceDescription(val id: Int, val name: String, val type: Int, val isOutput: Boolean, val channelMasks: List<Int>) {
    val kind: AudioDeviceKind get() = AudioDeviceTypes.classify(type)
}

object AudioDevices {
    /** Devices a person can test, built-in ones first, then by kind and name. */
    fun buildList(devices: List<AudioDeviceDescription>): List<AudioDeviceDescription> =
        devices.filter { AudioDeviceTypes.isShown(it.type) }.sortedWith(compareBy({ it.kind.ordinal }, { it.name }))

    /** The device [id] names among those of one direction, or the first of them when it is gone. */
    fun findSelected(devices: List<AudioDeviceDescription>, isOutput: Boolean, id: Int?): AudioDeviceDescription? {
        val candidates = devices.filter { it.isOutput == isOutput }
        return candidates.firstOrNull { it.id == id } ?: candidates.firstOrNull()
    }
}

/** AudioFormat output channel bits, in the order their samples are interleaved. */
enum class SpeakerChannel(val bit: Int) {
    FRONT_LEFT(0x4),
    FRONT_RIGHT(0x8),
    FRONT_CENTER(0x10),
    LOW_FREQUENCY(0x20),
    BACK_LEFT(0x40),
    BACK_RIGHT(0x80),
    FRONT_LEFT_OF_CENTER(0x100),
    FRONT_RIGHT_OF_CENTER(0x200),
    BACK_CENTER(0x400),
    SIDE_LEFT(0x800),
    SIDE_RIGHT(0x1000),
    ;

    companion object {
        private val STEREO = listOf(FRONT_LEFT, FRONT_RIGHT)

        fun fromMask(mask: Int): List<SpeakerChannel> = entries.filter { mask and it.bit != 0 }

        fun maskOf(channels: List<SpeakerChannel>): Int = channels.fold(0) { mask, channel -> mask or channel.bit }

        /** Left and right, plus every channel of the device's widest mask. */
        fun chooseChannels(masks: List<Int>): List<SpeakerChannel> {
            val widest = masks.map(::fromMask).maxByOrNull { it.size }.orEmpty()
            return (STEREO + widest).distinct().sortedBy { it.bit }
        }
    }
}

/** A sine that sounds in one channel of an interleaved 16-bit layout, fading in and out to avoid clicks. */
object TestTone {
    const val DEFAULT_FREQUENCY_HZ = 440.0
    const val DEFAULT_AMPLITUDE = 0.5
    private const val FADE_MILLIS = 20

    fun build(
        frequencyHz: Double,
        sampleRate: Int,
        durationMillis: Int,
        channelCount: Int,
        channelIndex: Int,
        amplitude: Double = DEFAULT_AMPLITUDE,
    ): ShortArray {
        require(channelIndex in 0 until channelCount) { "channel $channelIndex of $channelCount" }
        val frames = sampleRate * durationMillis / 1_000
        val fadeFrames = (sampleRate * FADE_MILLIS / 1_000).coerceIn(1, (frames / 2).coerceAtLeast(1))
        val samples = ShortArray(frames * channelCount)
        (0 until frames).forEach { frame ->
            val envelope = minOf(1.0, frame.toDouble() / fadeFrames, (frames - 1 - frame).toDouble() / fadeFrames)
            val value = amplitude * envelope * sin(2 * PI * frequencyHz * frame / sampleRate)
            samples[frame * channelCount + channelIndex] = (value * Short.MAX_VALUE).roundToInt().toShort()
        }
        return samples
    }
}

/** How loud a buffer is, in decibels of full scale: 0 is the loudest a 16-bit sample goes. */
data class AudioLevel(val rmsDbfs: Double, val peakDbfs: Double) {
    /** The meter's fill, from [floorDb] (empty) to 0 dBFS (full). */
    fun meterFraction(floorDb: Double = METER_FLOOR_DB): Float = ((rmsDbfs - floorDb) / -floorDb).coerceIn(0.0, 1.0).toFloat()

    companion object {
        /** The quietest a 16-bit sample can be, where silence is shown. */
        const val FLOOR_DBFS = -96.0
        private const val METER_FLOOR_DB = -60.0
        private const val FULL_SCALE = 32_768.0

        fun measure(samples: ShortArray, count: Int = samples.size): AudioLevel {
            val used = samples.asList().subList(0, count.coerceIn(0, samples.size))
            val peak = used.maxOfOrNull { abs(it.toDouble()) } ?: 0.0
            val rms = sqrt(used.sumOf { it.toDouble() * it } / used.size.coerceAtLeast(1))
            return AudioLevel(toDbfs(rms), toDbfs(peak))
        }

        private fun toDbfs(value: Double): Double = (20 * log10(value / FULL_SCALE)).coerceAtLeast(FLOOR_DBFS)
    }
}

/** The output the speaker test plays through, on the device. */
interface TonePlayer {
    fun isMuted(): Boolean

    /** Plays interleaved 16-bit [samples] laid out as [channels] and returns when done; false when it could not play. */
    fun play(samples: ShortArray, sampleRate: Int, channels: List<SpeakerChannel>): Boolean
}

/** The microphone the microphone test records from, on the device. */
interface SoundRecorder {
    /** Records [millis] of 16-bit mono sound; null when the microphone could not be opened. */
    fun record(millis: Int, cancel: CancelSignal): ShortArray?
}

/** Plays a tone in each channel in turn. Whether it was heard is up to the person listening. */
class SpeakerTest(
    private val player: TonePlayer?,
    private val channels: List<SpeakerChannel>,
    private val toneMillis: Int = 700,
    private val sampleRate: Int = 48_000,
) : DiagnosticTest {
    override val name = TestName.AUDIO_SPEAKERS

    override fun run(cancel: CancelSignal): TestOutcome {
        val output = player ?: return Failed(Finding(FindingKey.AUDIO_NO_OUTPUT))
        if (output.isMuted()) return Warning(Finding(FindingKey.AUDIO_MUTED))
        for (index in channels.indices) {
            cancel.throwIfCanceled()
            val tone = TestTone.build(TestTone.DEFAULT_FREQUENCY_HZ, sampleRate, toneMillis, channels.size, index)
            if (!output.play(tone, sampleRate, channels)) return Failed(Finding(FindingKey.AUDIO_PLAYBACK_FAILED))
        }
        return Passed(Finding(FindingKey.AUDIO_CHANNELS_PLAYED, listOf(channels.size)))
    }
}

/** Records a moment of sound: the microphone must open and hear something above silence. */
class MicrophoneTest(
    private val recorder: SoundRecorder?,
    private val hasPermission: () -> Boolean,
    private val millis: Int = 2_000,
) : DiagnosticTest {
    override val name = TestName.AUDIO_MICROPHONE

    override fun run(cancel: CancelSignal): TestOutcome {
        if (!hasPermission()) return Failed(Finding(FindingKey.AUDIO_NO_PERMISSION))
        val microphone = recorder ?: return Failed(Finding(FindingKey.AUDIO_NO_INPUT))
        val samples = microphone.record(millis, cancel)?.takeIf { it.isNotEmpty() } ?: return Failed(Finding(FindingKey.AUDIO_RECORD_FAILED))
        val level = AudioLevel.measure(samples)
        if (level.peakDbfs < SILENT_DBFS) return Warning(Finding(FindingKey.AUDIO_SILENT))
        return Passed(Finding(FindingKey.AUDIO_RECORDED_LEVEL, listOf(level.peakDbfs.roundToInt())))
    }

    private companion object {
        /** A few steps of the smallest sample: what a muted or disconnected microphone gives. */
        const val SILENT_DBFS = -80.0
    }
}
