package org.lineageos.pcdiagnostics.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SuiteRunnerTest {
    private class FixedCheck(override val name: TestName, private val outcome: TestOutcome) : DiagnosticTest {
        var runs = 0
        override fun run(cancel: CancelSignal): TestOutcome {
            runs++
            return outcome
        }
    }

    private val notCanceled = CancelSignal { false }

    @Test
    fun `a suite that never ran is not run`() {
        val state = SuiteState.of(listOf(TestName.CPU_STRESS, TestName.CPU_CACHE))

        assertEquals(SuiteStatus.NOT_RUN, state.overall)
        assertEquals(listOf(TestStatus.NotRun, TestStatus.NotRun), state.tests.map { it.status })
    }

    @Test
    fun `runs the tests in order and reports every step`() {
        val seen = mutableListOf<SuiteState>()
        val runner = SuiteRunner(listOf(FixedCheck(TestName.CPU_STRESS, Passed()), FixedCheck(TestName.CPU_CACHE, Passed())))

        val final = runner.run(notCanceled) { seen += it }

        assertEquals(listOf(TestStatus.Running, TestStatus.Waiting), seen.first().tests.map { it.status })
        assertEquals(SuiteStatus.RUNNING, seen.first().overall)
        assertEquals(final, seen.last())
        assertEquals(SuiteStatus.PASSED, final.overall)
    }

    @Test
    fun `the worst outcome decides the suite`() {
        val warned = SuiteRunner(listOf(FixedCheck(TestName.CPU_STRESS, Passed()), FixedCheck(TestName.CPU_CACHE, Warning(Finding(FindingKey.STORAGE_LOW_SPACE)))))
        val failed = SuiteRunner(listOf(FixedCheck(TestName.CPU_STRESS, Failed(Finding(FindingKey.CPU_STRESS_MISMATCH))), FixedCheck(TestName.CPU_CACHE, Warning(Finding(FindingKey.STORAGE_LOW_SPACE)))))

        assertEquals(SuiteStatus.WARNING, warned.run(notCanceled) {}.overall)
        assertEquals(SuiteStatus.FAILED, failed.run(notCanceled) {}.overall)
    }

    @Test
    fun `a canceled suite stops and marks the rest canceled`() {
        val second = FixedCheck(TestName.CPU_CACHE, Passed())
        var canceled = false
        val first = object : DiagnosticTest {
            override val name = TestName.CPU_STRESS
            override fun run(cancel: CancelSignal): TestOutcome {
                canceled = true
                cancel.throwIfCanceled()
                return Passed()
            }
        }

        val final = SuiteRunner(listOf(first, second)).run({ canceled }) {}

        assertEquals(SuiteStatus.CANCELED, final.overall)
        assertEquals(listOf(TestStatus.Canceled, TestStatus.Canceled), final.tests.map { it.status })
        assertEquals(0, second.runs)
    }

    @Test
    fun `a test that crashes fails with the crash finding`() {
        val crashing = object : DiagnosticTest {
            override val name = TestName.MEMORY
            override fun run(cancel: CancelSignal): TestOutcome = throw OutOfMemoryError()
        }

        val final = SuiteRunner(listOf(crashing)).run(notCanceled) {}

        assertEquals(TestStatus.Done(Failed(Finding(FindingKey.TEST_CRASHED))), final.tests.single().status)
    }

    @Test
    fun `the report lists only finished tests`() {
        val final = SuiteRunner(listOf(FixedCheck(TestName.CPU_STRESS, Passed()))).run(notCanceled) {}

        assertEquals(listOf(TestName.CPU_STRESS), final.finished().map { it.name })
    }
}
