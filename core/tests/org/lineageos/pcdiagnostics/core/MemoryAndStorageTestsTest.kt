package org.lineageos.pcdiagnostics.core

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryAndStorageTestsTest {
    private val notCanceled = CancelSignal { false }

    /** Memory whose bit 3 of cell [badCell] never changes. */
    private class StuckBitCells(size: Int, private val badCell: Int) : MemoryCells {
        private val cells = LongArray(size)
        override val size = size
        override fun get(index: Int) = cells[index]
        override fun set(index: Int, value: Long) {
            cells[index] = value and (STUCK_BIT_CLEARED.takeIf { index == badCell } ?: ALL_BITS)
        }

        private companion object {
            const val STUCK_BIT_CLEARED = 8L.inv()
            const val ALL_BITS = -1L
        }
    }

    @Test
    fun `memory test passes on working memory`() {
        assertEquals(Passed(), MemoryTest(chunks = { listOf(ArrayCells(4096), ArrayCells(4096)) }).run(notCanceled))
    }

    @Test
    fun `memory test finds a stuck bit`() {
        val outcome = MemoryTest(chunks = { listOf(StuckBitCells(4096, badCell = 1000)) }).run(notCanceled)

        assertEquals(Failed(Finding(FindingKey.MEMORY_MISMATCH)), outcome)
    }

    @Test
    fun `memory test size is a quarter of the available memory, within the app's heap`() {
        assertEquals(256L shl 20, MemoryTest.chooseBytes(availableBytes = 4L shl 30, heapBytes = 512L shl 20))
        assertEquals(100L shl 20, MemoryTest.chooseBytes(availableBytes = 400L shl 20, heapBytes = 512L shl 20))
    }

    @Test
    fun `storage test writes, reads back and removes its file`() {
        val dir = Files.createTempDirectory("storage").toFile()

        val outcome = StorageTest(dir, sizeBytes = 3L shl 20, readSpace = { StorageSpace(100L shl 30, 50L shl 30) }).run(notCanceled)

        assertEquals(Passed::class, outcome::class)
        assertEquals(FindingKey.STORAGE_SPEED, outcome.finding?.key)
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test
    fun `storage test fails without room for its file`() {
        val outcome = StorageTest(File("/nonexistent"), sizeBytes = 64L shl 20, readSpace = { StorageSpace(100L shl 30, 10L shl 20) }).run(notCanceled)

        assertEquals(Failed(Finding(FindingKey.STORAGE_NO_SPACE)), outcome)
    }

    @Test
    fun `storage test warns when the disk is almost full`() {
        val dir = Files.createTempDirectory("storage").toFile()

        val outcome = StorageTest(dir, sizeBytes = 1L shl 20, readSpace = { StorageSpace(100L shl 30, 5L shl 30) }).run(notCanceled)

        assertEquals(Warning(Finding(FindingKey.STORAGE_LOW_SPACE, listOf(5))), outcome)
    }

    @Test
    fun `used storage is total minus free`() {
        val space = StorageSpace(totalBytes = 100, freeBytes = 25)

        assertEquals(75L, space.usedBytes)
        assertEquals(0.75f, space.usedFraction)
    }
}
