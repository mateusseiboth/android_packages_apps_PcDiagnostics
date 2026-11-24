package org.lineageos.pcdiagnostics

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.lineageos.pcdiagnostics.core.AudioDeviceDescription
import org.lineageos.pcdiagnostics.core.CancelSignal
import org.lineageos.pcdiagnostics.core.SoundRecorder
import org.lineageos.pcdiagnostics.core.SpeakerChannel
import org.lineageos.pcdiagnostics.core.TonePlayer

/** The audio devices AudioManager knows, now and as they come and go. */
class AudioReader(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun readDevices(): List<AudioDeviceDescription> = audioManager.getDevices(AudioManager.GET_DEVICES_ALL).map(::describe)

    fun findDevice(id: Int): AudioDeviceInfo? = audioManager.getDevices(AudioManager.GET_DEVICES_ALL).firstOrNull { it.id == id }

    fun isMuted(): Boolean = audioManager.isStreamMute(AudioManager.STREAM_MUSIC) || audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0

    fun watchDevices(): Flow<List<AudioDeviceDescription>> = callbackFlow {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                trySend(readDevices())
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                trySend(readDevices())
            }
        }
        send(readDevices())
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    private fun describe(device: AudioDeviceInfo) = AudioDeviceDescription(
        id = device.id,
        name = device.productName.toString(),
        type = device.type,
        isOutput = device.isSink,
        channelMasks = device.channelMasks.toList(),
    )
}

/** Plays 16-bit samples through [device] (the system's choice when null), blocking until they end. */
class AndroidTonePlayer(private val reader: AudioReader, private val device: AudioDeviceInfo?) : TonePlayer {
    override fun isMuted() = reader.isMuted()

    override fun play(samples: ShortArray, sampleRate: Int, channels: List<SpeakerChannel>): Boolean = runCatching {
        val track = buildTrack(samples.size, sampleRate, SpeakerChannel.maskOf(channels))
        try {
            playOn(track, samples, sampleRate, channels.size)
        } finally {
            track.release()
        }
    }.getOrDefault(false)

    private fun playOn(track: AudioTrack, samples: ShortArray, sampleRate: Int, channelCount: Int): Boolean {
        if (track.write(samples, 0, samples.size) != samples.size) return false
        track.setPreferredDevice(device)
        track.play()
        Thread.sleep(samples.size / channelCount * MILLIS_PER_SECOND / sampleRate + TAIL_MILLIS)
        return true
    }

    private fun buildTrack(sampleCount: Int, sampleRate: Int, channelMask: Int): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(channelMask).build())
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(sampleCount * Short.SIZE_BYTES)
        .build()

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val TAIL_MILLIS = 100L
    }
}

/** Records 16-bit mono sound from [device] (the system's choice when null). Needs RECORD_AUDIO. */
class AndroidSoundRecorder(private val device: AudioDeviceInfo?) : SoundRecorder {
    override fun record(millis: Int, cancel: CancelSignal): ShortArray? = recordUntil(
        maxMillis = millis,
        isDone = {
            cancel.throwIfCanceled()
            false
        },
        onChunk = { _, _ -> },
    )

    /** Records up to [maxMillis] or until [isDone]; [onChunk] gets each chunk as it arrives. Null when it could not record. */
    fun recordUntil(maxMillis: Int, isDone: () -> Boolean, onChunk: (ShortArray, Int) -> Unit): ShortArray? {
        val record = openRecord() ?: return null
        try {
            record.startRecording()
            return readInto(record, ShortArray(SAMPLE_RATE * maxMillis / 1_000), isDone, onChunk)
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    private fun readInto(record: AudioRecord, samples: ShortArray, isDone: () -> Boolean, onChunk: (ShortArray, Int) -> Unit): ShortArray? {
        val chunk = ShortArray(CHUNK_SAMPLES)
        var total = 0
        while (total < samples.size && !isDone()) {
            val read = record.read(chunk, 0, minOf(CHUNK_SAMPLES, samples.size - total))
            if (read <= 0) return null
            chunk.copyInto(samples, total, 0, read)
            onChunk(chunk, read)
            total += read
        }
        return samples.copyOf(total)
    }

    private fun openRecord(): AudioRecord? {
        val record = runCatching(::buildRecord).getOrNull() ?: return null
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }
        record.setPreferredDevice(device)
        return record
    }

    @SuppressLint("MissingPermission") // The callers check RECORD_AUDIO; without it the build throws and openRecord gives null.
    private fun buildRecord(): AudioRecord = AudioRecord.Builder()
        .setAudioSource(MediaRecorder.AudioSource.MIC)
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
        .setBufferSizeInBytes(CHUNK_SAMPLES * Short.SIZE_BYTES * 4)
        .build()

    companion object {
        const val SAMPLE_RATE = 48_000

        /** 50 ms: the level meter moves this often. */
        private const val CHUNK_SAMPLES = SAMPLE_RATE / 20
    }
}
