package org.lineageos.pcdiagnostics.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Random

data class StorageSpace(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes get() = totalBytes - freeBytes
    val usedFraction get() = usedBytes.toFloat() / totalBytes.coerceAtLeast(1)
    val freePercent get() = (freeBytes * 100 / totalBytes.coerceAtLeast(1)).toInt()
}

/**
 * Writes a file of [sizeBytes] in [dir], syncs it to the disk, reads it back and compares, then removes it.
 * Passes with the write and read speeds in MB/s; warns when less than [LOW_SPACE_PERCENT] of the disk is free.
 */
class StorageTest(
    private val dir: File,
    private val sizeBytes: Long = 256L shl 20,
    private val readSpace: () -> StorageSpace,
) : DiagnosticTest {
    override val name = TestName.STORAGE

    override fun run(cancel: CancelSignal): TestOutcome {
        val space = readSpace()
        if (space.freeBytes < sizeBytes * 2) return Failed(Finding(FindingKey.STORAGE_NO_SPACE))
        val file = File.createTempFile("diagnostics", ".bin", dir)
        try {
            val (_, writeNanos) = timed { writeChunks(file, cancel) }
            val (same, readNanos) = timed { isFileRight(file, cancel) }
            if (!same) return Failed(Finding(FindingKey.STORAGE_MISMATCH))
            return judgeSpace(space) ?: Passed(Finding(FindingKey.STORAGE_SPEED, listOf(toMegabytesPerSecond(writeNanos), toMegabytesPerSecond(readNanos))))
        } finally {
            file.delete()
        }
    }

    private fun judgeSpace(space: StorageSpace): TestOutcome? =
        Warning(Finding(FindingKey.STORAGE_LOW_SPACE, listOf(space.freePercent))).takeIf { space.freePercent < LOW_SPACE_PERCENT }

    private val chunkCount get() = ((sizeBytes + CHUNK_BYTES - 1) / CHUNK_BYTES).toInt()

    private fun buildChunk(index: Int) = ByteArray(CHUNK_BYTES).also { Random(index * 31L + 7).nextBytes(it) }

    private fun writeChunks(file: File, cancel: CancelSignal) = FileOutputStream(file).use { out ->
        repeat(chunkCount) { index ->
            cancel.throwIfCanceled()
            out.write(buildChunk(index))
        }
        out.fd.sync()
    }

    private fun isFileRight(file: File, cancel: CancelSignal): Boolean = FileInputStream(file).use { input ->
        val read = ByteArray(CHUNK_BYTES)
        (0 until chunkCount).all { index ->
            cancel.throwIfCanceled()
            input.readNBytes(read, 0, CHUNK_BYTES) == CHUNK_BYTES && read.contentEquals(buildChunk(index))
        }
    }

    /** The block's result and how long it took, in nanoseconds. */
    private fun <T> timed(block: () -> T): Pair<T, Long> {
        val start = System.nanoTime()
        val result = block()
        return result to (System.nanoTime() - start).coerceAtLeast(1)
    }

    private fun toMegabytesPerSecond(nanos: Long): Int = (chunkCount.toLong() * CHUNK_BYTES * 1_000_000_000L / nanos / (1L shl 20)).toInt()

    companion object {
        const val LOW_SPACE_PERCENT = 10
        private const val CHUNK_BYTES = 1 shl 20
    }
}
