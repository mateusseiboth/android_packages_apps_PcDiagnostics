package org.lineageos.pcdiagnostics.core

/** A block of memory cells, 64 bits each. */
interface MemoryCells {
    val size: Int

    operator fun get(index: Int): Long

    operator fun set(index: Int, value: Long)
}

class ArrayCells(override val size: Int) : MemoryCells {
    private val cells = LongArray(size)

    override fun get(index: Int) = cells[index]

    override fun set(index: Int, value: Long) {
        cells[index] = value
    }
}

/**
 * Writes patterns over the blocks and reads them back: all zeros, all ones, alternating bits both ways,
 * walking ones, each cell's own address and a pseudo-random sequence.
 */
class MemoryTest(private val chunks: () -> List<MemoryCells>) : DiagnosticTest {
    override val name = TestName.MEMORY

    override fun run(cancel: CancelSignal): TestOutcome {
        val blocks = chunks()
        val ok = PATTERNS.all { pattern ->
            blocks.withIndex().all { (block, cells) ->
                cancel.throwIfCanceled()
                isPatternKept(cells) { index -> pattern(block, index) }
            }
        }
        return buildOutcome(ok, FindingKey.MEMORY_MISMATCH)
    }

    private fun isPatternKept(cells: MemoryCells, valueAt: (Int) -> Long): Boolean {
        (0 until cells.size).forEach { cells[it] = valueAt(it) }
        return (0 until cells.size).all { cells[it] == valueAt(it) }
    }

    companion object {
        private val PATTERNS: List<(Int, Int) -> Long> = listOf(
            { _, _ -> 0L },
            { _, _ -> -1L },
            { _, _ -> 0x5555555555555555L },
            { _, _ -> -0x5555555555555556L },
            { _, index -> 1L shl (index % Long.SIZE_BITS) },
            { block, index -> (block.toLong() shl 32) or index.toLong() },
            { block, index -> ((block.toLong() shl 32) or index.toLong()).inv() },
            { block, index -> scramble(block.toLong() * 0x100000000L + index) },
        )

        private fun scramble(value: Long): Long {
            var bits = value * -0x61c8864680b583ebL
            bits = bits xor (bits ushr 31)
            return bits * -0x4b47d5b1c7d4d4dfL
        }

        /** A quarter of the free memory, and never more than half of what the app's heap allows. */
        fun chooseBytes(availableBytes: Long, heapBytes: Long): Long = minOf(availableBytes / 4, heapBytes / 2)
    }
}
