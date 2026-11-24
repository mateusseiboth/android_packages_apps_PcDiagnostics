package org.lineageos.pcdiagnostics.core

import java.nio.ByteBuffer

/** CameraCharacteristics.LENS_FACING values. */
enum class CameraFacing(val lensFacing: Int) {
    FRONT(0),
    BACK(1),
    EXTERNAL(2),
    ;

    companion object {
        /** Cameras that do not say are taken as plugged in: built-in ones always say. */
        fun fromLensFacing(value: Int?): CameraFacing = entries.firstOrNull { it.lensFacing == value } ?: EXTERNAL
    }
}

data class PixelSize(val width: Int, val height: Int) {
    val pixels: Long get() = width.toLong() * height

    val megapixels: Double get() = pixels / 1_000_000.0
}

/** A camera as CameraManager describes it; [sizes] are the sizes it can stream. */
data class CameraDescription(val id: String, val facing: CameraFacing, val sizes: List<PixelSize>) {
    val largest: PixelSize? get() = sizes.maxByOrNull { it.pixels }

    /** USB webcams report an external lens. */
    val isExternal: Boolean get() = facing == CameraFacing.EXTERNAL
}

object Cameras {
    fun buildList(cameras: List<CameraDescription>): List<CameraDescription> = cameras.sortedWith(compareBy({ it.facing.ordinal }, { it.id }))

    /** The camera [id] names, or the first one when it is gone. */
    fun findSelected(cameras: List<CameraDescription>, id: String?): CameraDescription? = cameras.firstOrNull { it.id == id } ?: cameras.firstOrNull()
}

object Luma {
    /** The average brightness (0 to 255) of a Y plane, reading every [step]th pixel of every [step]th row. */
    fun average(plane: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int, step: Int = 1): Double {
        val rows = (0 until height step step)
        val columns = (0 until width step step)
        val total = rows.sumOf { row -> columns.sumOf { column -> plane.get(row * rowStride + column * pixelStride).toInt() and 0xFF }.toLong() }
        return total.toDouble() / (rows.count() * columns.count()).coerceAtLeast(1)
    }
}

/** Frames of the camera on screen, on the device. */
interface FrameSource {
    /** The luma of a frame that arrives within [timeoutMillis], or null when none does. */
    fun awaitLuma(timeoutMillis: Long, cancel: CancelSignal): Double?
}

/** The camera on screen must send frames, and they must not be dark (a covered lens or a closed shutter). */
class CameraTest(
    private val cameras: List<CameraDescription>,
    private val hasPermission: () -> Boolean,
    private val frames: FrameSource,
    private val timeoutMillis: Long = 5_000,
) : DiagnosticTest {
    override val name = TestName.CAMERA

    override fun run(cancel: CancelSignal): TestOutcome {
        if (cameras.isEmpty()) return Failed(Finding(FindingKey.CAMERA_NONE))
        if (!hasPermission()) return Failed(Finding(FindingKey.CAMERA_NO_PERMISSION))
        val luma = frames.awaitLuma(timeoutMillis, cancel) ?: return Failed(Finding(FindingKey.CAMERA_NO_FRAMES))
        if (luma < DARK_LUMA) return Warning(Finding(FindingKey.CAMERA_DARK))
        return Passed()
    }

    private companion object {
        /** About a tenth of full brightness: a dim room still reads above it, a covered lens below. */
        const val DARK_LUMA = 25.0
    }
}
