package org.lineageos.pcdiagnostics.core

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraChecksTest {
    private val notCanceled = CancelSignal { false }

    private val front = CameraDescription("1", CameraFacing.FRONT, listOf(PixelSize(640, 480), PixelSize(1920, 1080), PixelSize(1280, 720)))
    private val usb = CameraDescription("100", CameraFacing.EXTERNAL, listOf(PixelSize(1280, 720)))
    private val back = CameraDescription("0", CameraFacing.BACK, emptyList())

    private class FixedFrames(val luma: Double?) : FrameSource {
        override fun awaitLuma(timeoutMillis: Long, cancel: CancelSignal) = luma
    }

    @Test
    fun `facing comes from the lens facing value`() {
        assertEquals(CameraFacing.FRONT, CameraFacing.fromLensFacing(0))
        assertEquals(CameraFacing.BACK, CameraFacing.fromLensFacing(1))
        assertEquals(CameraFacing.EXTERNAL, CameraFacing.fromLensFacing(2))
        assertEquals(CameraFacing.EXTERNAL, CameraFacing.fromLensFacing(null))
    }

    @Test
    fun `the largest size and its megapixels`() {
        assertEquals(PixelSize(1920, 1080), front.largest)
        assertEquals(2.07, front.largest!!.megapixels, 0.01)
        assertNull(back.largest)
    }

    @Test
    fun `usb cameras are external`() {
        assertTrue(usb.isExternal)
        assertFalse(front.isExternal)
    }

    @Test
    fun `cameras are listed front, back, then external`() {
        assertEquals(listOf(front, back, usb), Cameras.buildList(listOf(usb, back, front)))
    }

    @Test
    fun `the chosen camera, or the first one`() {
        val cameras = listOf(front, usb)

        assertEquals(usb, Cameras.findSelected(cameras, "100"))
        assertEquals(front, Cameras.findSelected(cameras, null))
        assertEquals(front, Cameras.findSelected(cameras, "gone"))
        assertNull(Cameras.findSelected(emptyList(), "1"))
    }

    @Test
    fun `luma is the average of the y plane, row padding left out`() {
        // Two rows of four pixels, each row padded to six bytes; 200 reads as unsigned.
        val plane = ByteBuffer.wrap(byteArrayOf(10, 20, 30, 40, -1, -1, 200.toByte(), 0, 0, 100, -1, -1))

        assertEquals(50.0, Luma.average(plane, width = 4, height = 2, rowStride = 6, pixelStride = 1), 0.001)
    }

    @Test
    fun `luma can skip pixels to stay quick`() {
        val plane = ByteBuffer.wrap(ByteArray(16) { (it * 10).toByte() })

        assertEquals(50.0, Luma.average(plane, width = 4, height = 4, rowStride = 4, pixelStride = 1, step = 2), 0.001)
    }

    @Test
    fun `camera test needs a camera, permission and a frame that is not dark`() {
        assertEquals(Passed(), CameraTest(listOf(front), { true }, FixedFrames(120.0)).run(notCanceled))
        assertEquals(Failed(Finding(FindingKey.CAMERA_NONE)), CameraTest(emptyList(), { true }, FixedFrames(120.0)).run(notCanceled))
        assertEquals(Failed(Finding(FindingKey.CAMERA_NO_PERMISSION)), CameraTest(listOf(front), { false }, FixedFrames(120.0)).run(notCanceled))
        assertEquals(Failed(Finding(FindingKey.CAMERA_NO_FRAMES)), CameraTest(listOf(front), { true }, FixedFrames(null)).run(notCanceled))
        assertEquals(Warning(Finding(FindingKey.CAMERA_DARK)), CameraTest(listOf(front), { true }, FixedFrames(8.0)).run(notCanceled))
    }
}
