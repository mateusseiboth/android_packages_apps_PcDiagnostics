package org.lineageos.pcdiagnostics.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CpuTestsTest {
    private val notCanceled = CancelSignal { false }

    @Test
    fun `prime search finds the known number of primes`() {
        assertEquals(Passed(), PrimeSearchTest(limit = 100_000).run(notCanceled))
    }

    @Test
    fun `prime search fails when the count is wrong`() {
        val outcome = PrimeSearchTest(limit = 100, knownCounts = mapOf(100 to 26)).run(notCanceled)

        assertEquals(Failed(Finding(FindingKey.CPU_PRIME_MISMATCH)), outcome)
    }

    @Test
    fun `floating point checks pass on a working cpu`() {
        assertEquals(Passed(), FloatingPointAccuracyTest(rounds = 2).run(notCanceled))
    }

    @Test
    fun `cache test passes on a working cpu`() {
        assertEquals(Passed(), CacheTest(bufferSizes = listOf(4 * 1024, 64 * 1024), passes = 2).run(notCanceled))
    }

    @Test
    fun `stress test passes when every thread gets the same result`() {
        val outcome = StressTest(durationMillis = 30, threads = 2, workload = { 42L }).run(notCanceled)

        assertEquals(Passed(), outcome)
    }

    @Test
    fun `stress test fails when a thread gets a different result`() {
        val counter = java.util.concurrent.atomic.AtomicLong()
        val outcome = StressTest(durationMillis = 30, threads = 2, workload = { counter.incrementAndGet() }).run(notCanceled)

        assertEquals(Failed(Finding(FindingKey.CPU_STRESS_MISMATCH)), outcome)
    }

    @Test
    fun `the default stress workload is deterministic`() {
        assertEquals(StressTest.computeWorkload(), StressTest.computeWorkload())
    }

    @Test(expected = TestCanceledException::class)
    fun `a canceled cpu test stops`() {
        StressTest(durationMillis = 60_000, threads = 1).run { true }
    }
}
