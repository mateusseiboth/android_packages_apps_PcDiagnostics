package org.lineageos.pcdiagnostics.core

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/** Counts the primes up to [limit] with a sieve and compares with the known count. */
class PrimeSearchTest(
    private val limit: Int = 10_000_000,
    private val rounds: Int = 10,
    private val knownCounts: Map<Int, Int> = KNOWN_PRIME_COUNTS,
) : DiagnosticTest {
    override val name = TestName.CPU_PRIME_SEARCH

    override fun run(cancel: CancelSignal): TestOutcome {
        val expected = knownCounts[limit] ?: countByTrialDivision(cancel)
        val ok = (1..rounds).all { countWithSieve(cancel) == expected }
        return buildOutcome(ok, FindingKey.CPU_PRIME_MISMATCH)
    }

    private fun countWithSieve(cancel: CancelSignal): Int {
        val composite = BooleanArray(limit + 1)
        var candidate = 2
        while (candidate.toLong() * candidate <= limit) {
            cancel.throwIfCanceled()
            (candidate * candidate..limit step candidate).takeIf { !composite[candidate] }?.forEach { composite[it] = true }
            candidate++
        }
        return (2..limit).count { !composite[it] }
    }

    private fun countByTrialDivision(cancel: CancelSignal) = (2..limit).count { number ->
        cancel.throwIfCanceled()
        (2..sqrt(number.toDouble()).toInt()).none { number % it == 0 }
    }

    companion object {
        val KNOWN_PRIME_COUNTS = mapOf(
            100 to 25,
            1_000 to 168,
            10_000 to 1_229,
            100_000 to 9_592,
            1_000_000 to 78_498,
            10_000_000 to 664_579,
        )
    }
}

/** Identities the floating point unit must get right, and the same bits every round. */
class FloatingPointAccuracyTest(private val rounds: Int = 40) : DiagnosticTest {
    override val name = TestName.CPU_FLOATING_POINT

    private val checks: List<() -> Boolean> = listOf(::isBaselSumRight, ::isPythagoreanRight, ::isSquareRootRight, ::isExpLogRight)

    override fun run(cancel: CancelSignal): TestOutcome {
        val reference = computeFingerprint()
        val ok = (1..rounds).all {
            cancel.throwIfCanceled()
            checks.all { check -> check() } && computeFingerprint() == reference
        }
        return buildOutcome(ok, FindingKey.CPU_FLOAT_MISMATCH)
    }

    /** Sum of 1/n² from the smallest term up, plus the tail, is π²/6. */
    private fun isBaselSumRight(): Boolean {
        val terms = 200_000
        val sum = (terms downTo 1).sumOf { 1.0 / (it.toDouble() * it) } + 1.0 / terms
        return abs(sum - Math.PI * Math.PI / 6) < 1e-9
    }

    private fun isPythagoreanRight() = (0 until 20_000).all { val x = it * 0.37; abs(sin(x) * sin(x) + cos(x) * cos(x) - 1.0) < 1e-12 }

    private fun isSquareRootRight() = (1..100_000).all { val root = sqrt(it.toDouble()); abs(root * root - it) <= it * 1e-14 }

    private fun isExpLogRight() = (1..20_000).all { abs(exp(ln(it.toDouble())) - it) <= it * 1e-12 }

    private fun computeFingerprint(): Long = (1..50_000).fold(0L) { bits, n -> bits xor (sqrt(n.toDouble()) * sin(n.toDouble())).toRawBits() }
}

/** Fills buffers sized like the cache levels and reads them back in several orders. */
class CacheTest(
    private val bufferSizes: List<Int> = listOf(16 shl 10, 256 shl 10, 2 shl 20, 16 shl 20),
    private val passes: Int = 6,
) : DiagnosticTest {
    override val name = TestName.CPU_CACHE

    override fun run(cancel: CancelSignal): TestOutcome {
        val ok = bufferSizes.all { bytes -> (1..passes).all { pass -> isBufferRight(LongArray(bytes / Long.SIZE_BYTES), pass.toLong(), cancel) } }
        return buildOutcome(ok, FindingKey.CPU_CACHE_MISMATCH)
    }

    private fun isBufferRight(buffer: LongArray, seed: Long, cancel: CancelSignal): Boolean {
        cancel.throwIfCanceled()
        buffer.indices.forEach { buffer[it] = mix(it, seed) }
        val lineOrder = (0 until LONGS_PER_LINE).flatMap { offset -> (offset until buffer.size step LONGS_PER_LINE).toList() }
        val sequential = buffer.indices.all { buffer[it] == mix(it, seed) }
        return sequential && lineOrder.all { buffer[it] == mix(it, seed) }
    }

    private fun mix(index: Int, seed: Long): Long = (index.toLong() * GOLDEN) xor (seed shl 32) xor index.toLong().rotateLeft(17)

    private companion object {
        const val LONGS_PER_LINE = 8
        const val GOLDEN = -7046029254386353131L
    }
}

/** Runs the same computation on every thread for [durationMillis]; every result must match. */
class StressTest(
    private val durationMillis: Long = 20_000,
    private val threads: Int = Runtime.getRuntime().availableProcessors(),
    private val workload: () -> Long = ::computeWorkload,
) : DiagnosticTest {
    override val name = TestName.CPU_STRESS

    override fun run(cancel: CancelSignal): TestOutcome {
        val expected = workload()
        val mismatch = AtomicBoolean(false)
        val deadline = System.nanoTime() + durationMillis * 1_000_000
        val isWorking = { !mismatch.get() && !cancel.isCanceled() && System.nanoTime() < deadline }
        val workers = (1..threads).map {
            thread(name = "cpu-stress-$it") {
                while (isWorking()) {
                    mismatch.compareAndSet(false, workload() != expected)
                }
            }
        }
        workers.forEach(Thread::join)
        cancel.throwIfCanceled()
        return buildOutcome(!mismatch.get(), FindingKey.CPU_STRESS_MISMATCH)
    }

    companion object {
        /** Integer and floating point work with a single, always equal result. */
        fun computeWorkload(): Long {
            var state = 0x2545F4914F6CDD1DL
            var sum = 0.0
            repeat(200_000) { step ->
                state = state xor (state shl 13)
                state = state xor (state ushr 7)
                state = state xor (state shl 17)
                sum += sqrt((state and 0xFFFF).toDouble() + step)
            }
            return state xor sum.toRawBits()
        }
    }
}
