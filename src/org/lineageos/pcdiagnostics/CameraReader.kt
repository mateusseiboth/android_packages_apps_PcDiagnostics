package org.lineageos.pcdiagnostics

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.lineageos.pcdiagnostics.core.CameraDescription
import org.lineageos.pcdiagnostics.core.CameraFacing
import org.lineageos.pcdiagnostics.core.Cameras
import org.lineageos.pcdiagnostics.core.CancelSignal
import org.lineageos.pcdiagnostics.core.FrameSource
import org.lineageos.pcdiagnostics.core.Luma
import org.lineageos.pcdiagnostics.core.PixelSize

/** The cameras CameraManager knows, now and as USB cameras come and go. */
class CameraReader(context: Context) {
    private val manager = context.getSystemService(CameraManager::class.java)

    fun readCameras(): List<CameraDescription> = Cameras.buildList(runCatching { manager.cameraIdList.toList() }.getOrDefault(emptyList()).mapNotNull(::describe))

    // Opening a camera (ours included) makes it unavailable: the list is read again, and only changes count.
    fun watchCameras(): Flow<List<CameraDescription>> = callbackFlow {
        val callback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) {
                trySend(readCameras())
            }

            override fun onCameraUnavailable(cameraId: String) {
                trySend(readCameras())
            }
        }
        send(readCameras())
        manager.registerAvailabilityCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { manager.unregisterAvailabilityCallback(callback) }
    }.distinctUntilChanged()

    private fun describe(id: String): CameraDescription? = runCatching {
        val characteristics = manager.getCameraCharacteristics(id)
        val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.YUV_420_888)?.toList().orEmpty()
        CameraDescription(id, CameraFacing.fromLensFacing(characteristics.get(CameraCharacteristics.LENS_FACING)), sizes.map { PixelSize(it.width, it.height) })
    }.getOrNull()
}

/** The brightness of the frames the preview analyses, for the camera test to wait on. */
class CameraFrames : FrameSource {
    /** When the last frame arrived (System.nanoTime) and its luma. */
    private val latest = AtomicReference<Pair<Long, Double>?>(null)

    fun onImage(image: ImageProxy) {
        image.use {
            val plane = it.planes.first()
            latest.set(System.nanoTime() to Luma.average(plane.buffer, it.width, it.height, plane.rowStride, plane.pixelStride, step = SAMPLE_STEP))
        }
    }

    /** A frame from after the exposure had time to settle, or null when none came in time. */
    override fun awaitLuma(timeoutMillis: Long, cancel: CancelSignal): Double? {
        val settled = System.nanoTime() + SETTLE_NANOS
        val deadline = settled + timeoutMillis * NANOS_PER_MILLI
        while (System.nanoTime() < deadline) {
            cancel.throwIfCanceled()
            latest.get()?.takeIf { (time, _) -> time > settled }?.let { (_, luma) -> return luma }
            Thread.sleep(POLL_MILLIS)
        }
        return null
    }

    private companion object {
        const val SAMPLE_STEP = 8
        const val NANOS_PER_MILLI = 1_000_000L
        const val SETTLE_NANOS = 1_000 * NANOS_PER_MILLI
        const val POLL_MILLIS = 100L
    }
}
