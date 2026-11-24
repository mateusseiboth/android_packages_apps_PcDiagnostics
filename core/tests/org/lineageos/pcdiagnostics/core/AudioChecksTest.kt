package org.lineageos.pcdiagnostics.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioChecksTest {
    private val notCanceled = CancelSignal { false }

    private val stereo = listOf(SpeakerChannel.FRONT_LEFT, SpeakerChannel.FRONT_RIGHT)

    private class FakePlayer(val muted: Boolean = false, val works: Boolean = true) : TonePlayer {
        val played = mutableListOf<ShortArray>()

        override fun isMuted() = muted

        override fun play(samples: ShortArray, sampleRate: Int, channels: List<SpeakerChannel>): Boolean {
            played += samples
            return works
        }
    }

    private class FakeRecorder(val samples: ShortArray?) : SoundRecorder {
        override fun record(millis: Int, cancel: CancelSignal) = samples
    }

    private fun device(type: Int, name: String = "Device $type") = AudioDeviceDescription(id = type, name = name, type = type, isOutput = true, channelMasks = emptyList())

    /** A sine of [amplitude] (0 to 1 of full scale): 100 periods of 48 samples. */
    private fun sine(amplitude: Double) = ShortArray(4_800) { (amplitude * Short.MAX_VALUE * sin(2 * PI * it / 48)).toInt().toShort() }

    @Test
    fun `devices are grouped by their type`() {
        assertEquals(AudioDeviceKind.BUILT_IN, device(AudioDeviceTypes.BUILTIN_SPEAKER).kind)
        assertEquals(AudioDeviceKind.BUILT_IN, device(AudioDeviceTypes.BUILTIN_MIC).kind)
        assertEquals(AudioDeviceKind.HEADPHONES, device(AudioDeviceTypes.WIRED_HEADPHONES).kind)
        assertEquals(AudioDeviceKind.USB, device(AudioDeviceTypes.USB_HEADSET).kind)
        assertEquals(AudioDeviceKind.HDMI, device(AudioDeviceTypes.HDMI).kind)
        assertEquals(AudioDeviceKind.BLUETOOTH, device(AudioDeviceTypes.BLUETOOTH_A2DP).kind)
        assertEquals(AudioDeviceKind.LINE, device(AudioDeviceTypes.AUX_LINE).kind)
        assertEquals(AudioDeviceKind.OTHER, device(13).kind)
    }

    @Test
    fun `the list hides the system's own endpoints and shows built-in devices first`() {
        val usb = device(AudioDeviceTypes.USB_HEADSET, "USB Audio")
        val speaker = device(AudioDeviceTypes.BUILTIN_SPEAKER, "Speaker")
        val submix = device(AudioDeviceTypes.REMOTE_SUBMIX)
        val telephony = device(AudioDeviceTypes.TELEPHONY)

        assertEquals(listOf(speaker, usb), AudioDevices.buildList(listOf(submix, usb, telephony, speaker)))
    }

    @Test
    fun `the chosen device of a direction, or the first one`() {
        val speaker = device(AudioDeviceTypes.BUILTIN_SPEAKER)
        val hdmi = device(AudioDeviceTypes.HDMI)
        val microphone = device(AudioDeviceTypes.BUILTIN_MIC).copy(isOutput = false)
        val devices = listOf(microphone, speaker, hdmi)

        assertEquals(hdmi, AudioDevices.findSelected(devices, isOutput = true, id = hdmi.id))
        assertEquals(speaker, AudioDevices.findSelected(devices, isOutput = true, id = null))
        assertEquals(speaker, AudioDevices.findSelected(devices, isOutput = true, id = microphone.id))
        assertEquals(microphone, AudioDevices.findSelected(devices, isOutput = false, id = 999))
        assertEquals(null, AudioDevices.findSelected(listOf(speaker), isOutput = false, id = null))
    }

    @Test
    fun `channels come from the mask in bit order`() {
        assertEquals(stereo, SpeakerChannel.fromMask(0xC))
        assertEquals(
            listOf(SpeakerChannel.FRONT_LEFT, SpeakerChannel.FRONT_RIGHT, SpeakerChannel.FRONT_CENTER, SpeakerChannel.LOW_FREQUENCY, SpeakerChannel.BACK_LEFT, SpeakerChannel.BACK_RIGHT),
            SpeakerChannel.fromMask(0xFC),
        )
        assertEquals(0xC, SpeakerChannel.maskOf(stereo))
    }

    @Test
    fun `left and right are always tested, plus every channel of the widest mask`() {
        assertEquals(stereo, SpeakerChannel.chooseChannels(emptyList()))
        assertEquals(stereo, SpeakerChannel.chooseChannels(listOf(0x4)))
        assertEquals(8, SpeakerChannel.chooseChannels(listOf(0xC, 0x18FC, 0xFC)).size)
    }

    @Test
    fun `a tone sounds only in its channel`() {
        val tone = TestTone.build(frequencyHz = 1_000.0, sampleRate = 48_000, durationMillis = 100, channelCount = 2, channelIndex = 1)
        val left = tone.filterIndexed { index, _ -> index % 2 == 0 }
        val right = tone.filterIndexed { index, _ -> index % 2 == 1 }

        assertEquals(9_600, tone.size)
        assertTrue(left.all { it == 0.toShort() })
        assertEquals(TestTone.DEFAULT_AMPLITUDE * Short.MAX_VALUE, right.maxOf { it.toDouble() }, Short.MAX_VALUE * 0.01)
    }

    @Test
    fun `a tone has its frequency and fades in and out`() {
        val tone = TestTone.build(frequencyHz = 1_000.0, sampleRate = 48_000, durationMillis = 100, channelCount = 1, channelIndex = 0)
        val risingCrossings = tone.toList().zipWithNext().count { (before, after) -> before < 0 && after >= 0 }

        assertEquals(100.0, risingCrossings.toDouble(), 2.0)
        assertEquals(0, tone.first().toInt())
        assertTrue(abs(tone.last().toInt()) < 200)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a tone needs a channel of the layout`() {
        TestTone.build(frequencyHz = 440.0, sampleRate = 48_000, durationMillis = 10, channelCount = 2, channelIndex = 2)
    }

    @Test
    fun `levels are in decibels of full scale`() {
        val square = ShortArray(1_000) { (Short.MAX_VALUE.takeIf { _ -> it % 2 == 0 } ?: Short.MIN_VALUE) }

        assertEquals(0.0, AudioLevel.measure(square).rmsDbfs, 0.01)
        assertEquals(-3.01, AudioLevel.measure(sine(1.0)).rmsDbfs, 0.05)
        assertEquals(-6.02, AudioLevel.measure(sine(0.5)).peakDbfs, 0.05)
        assertEquals(AudioLevel.FLOOR_DBFS, AudioLevel.measure(ShortArray(100)).rmsDbfs, 0.0)
        assertEquals(AudioLevel.FLOOR_DBFS, AudioLevel.measure(ShortArray(0)).peakDbfs, 0.0)
    }

    @Test
    fun `only the first samples count when a count is given`() {
        val samples = ShortArray(100) { 1000.toShort().takeIf { _ -> it < 50 } ?: 0 }

        assertEquals(AudioLevel.measure(ShortArray(50) { 1000 }), AudioLevel.measure(samples, count = 50))
    }

    @Test
    fun `the meter fills from the floor to full scale`() {
        assertEquals(0.5f, AudioLevel(rmsDbfs = -30.0, peakDbfs = -20.0).meterFraction(floorDb = -60.0), 0.001f)
        assertEquals(0f, AudioLevel(AudioLevel.FLOOR_DBFS, AudioLevel.FLOOR_DBFS).meterFraction(), 0f)
        assertEquals(1f, AudioLevel(0.0, 0.0).meterFraction(), 0f)
    }

    @Test
    fun `speaker test plays one tone per channel`() {
        val player = FakePlayer()
        val outcome = SpeakerTest(player, stereo, toneMillis = 10).run(notCanceled)

        assertEquals(Passed(Finding(FindingKey.AUDIO_CHANNELS_PLAYED, listOf(2))), outcome)
        assertEquals(2, player.played.size)
        assertFalse(player.played[0].contentEquals(player.played[1]))
    }

    @Test
    fun `speaker test needs an output that plays and is not muted`() {
        assertEquals(Failed(Finding(FindingKey.AUDIO_NO_OUTPUT)), SpeakerTest(null, stereo).run(notCanceled))
        assertEquals(Warning(Finding(FindingKey.AUDIO_MUTED)), SpeakerTest(FakePlayer(muted = true), stereo).run(notCanceled))
        assertEquals(Failed(Finding(FindingKey.AUDIO_PLAYBACK_FAILED)), SpeakerTest(FakePlayer(works = false), stereo, toneMillis = 10).run(notCanceled))
    }

    @Test(expected = TestCanceledException::class)
    fun `speaker test stops between tones when canceled`() {
        SpeakerTest(FakePlayer(), stereo, toneMillis = 10).run { true }
    }

    @Test
    fun `microphone test reports the loudest sound it heard`() {
        assertEquals(Passed(Finding(FindingKey.AUDIO_RECORDED_LEVEL, listOf(-6))), MicrophoneTest(FakeRecorder(sine(0.5)), hasPermission = { true }).run(notCanceled))
    }

    @Test
    fun `microphone test needs permission, a microphone and sound`() {
        assertEquals(Failed(Finding(FindingKey.AUDIO_NO_PERMISSION)), MicrophoneTest(FakeRecorder(sine(0.5)), hasPermission = { false }).run(notCanceled))
        assertEquals(Failed(Finding(FindingKey.AUDIO_NO_INPUT)), MicrophoneTest(null, hasPermission = { true }).run(notCanceled))
        assertEquals(Failed(Finding(FindingKey.AUDIO_RECORD_FAILED)), MicrophoneTest(FakeRecorder(null), hasPermission = { true }).run(notCanceled))
        assertEquals(Warning(Finding(FindingKey.AUDIO_SILENT)), MicrophoneTest(FakeRecorder(ShortArray(4_800)), hasPermission = { true }).run(notCanceled))
    }
}
