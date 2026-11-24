package org.lineageos.pcdiagnostics.core

/** Every test the app can run. The UI gives each one its name. */
enum class TestName {
    CPU_STRESS,
    CPU_CACHE,
    CPU_FLOATING_POINT,
    CPU_PRIME_SEARCH,
    MEMORY,
    STORAGE,
    BATTERY_CHARGE,
    BATTERY_DISCHARGE,
    NETWORK_LOCAL,
    NETWORK_NAME_RESOLUTION,
    NETWORK_INTERNET,
    NETWORK_WIFI,
    INPUT_DEVICES,
    INPUT_KEYBOARD_KEYS,
}

/** What a test found. The UI turns each key into a sentence, with [Finding.args] in it. */
enum class FindingKey {
    TEST_CRASHED,
    CPU_STRESS_MISMATCH,
    CPU_CACHE_MISMATCH,
    CPU_FLOAT_MISMATCH,
    CPU_PRIME_MISMATCH,
    MEMORY_MISMATCH,
    STORAGE_NO_SPACE,
    STORAGE_LOW_SPACE,
    STORAGE_MISMATCH,
    STORAGE_SPEED,
    BATTERY_UNAVAILABLE,
    BATTERY_FULL,
    BATTERY_STATUS_CHANGED,
    BATTERY_CHARGE_DECREASED,
    BATTERY_CHARGE_INCREASED,
    BATTERY_DISCHARGE_INCREASED,
    BATTERY_DISCHARGE_DECREASED,
    NETWORK_NO_GATEWAY,
    NETWORK_GATEWAY_UNREACHABLE,
    NETWORK_NO_DNS,
    NETWORK_DNS_FAILED,
    NETWORK_DNS_SLOW,
    NETWORK_NO_INTERNET,
    NETWORK_HTTPS_SLOW,
    NETWORK_CAPTIVE_PORTAL,
    NETWORK_NO_WIFI_DETAILS,
    NETWORK_WEAK_SIGNAL,
    NETWORK_OPEN_WIFI,
    INPUT_NO_KEYBOARD,
    INPUT_NO_POINTER,
    INPUT_DEVICES_FOUND,
    INPUT_KEYS_NOT_TESTED,
    INPUT_KEYS_MISSED,
    INPUT_KEYS_ALL_PRESSED,
}

data class Finding(val key: FindingKey, val args: List<Any> = emptyList())

sealed interface TestOutcome {
    val finding: Finding?
}

data class Passed(override val finding: Finding? = null) : TestOutcome

data class Warning(override val finding: Finding) : TestOutcome

data class Failed(override val finding: Finding) : TestOutcome

/** Passed when [ok], failed with [key] otherwise. */
fun buildOutcome(ok: Boolean, key: FindingKey): TestOutcome = Passed().takeIf { ok } ?: Failed(Finding(key))

class TestCanceledException : RuntimeException()

/** Asked by long tests between steps; "Stop test" turns it on. */
fun interface CancelSignal {
    fun isCanceled(): Boolean

    fun throwIfCanceled() {
        if (isCanceled()) throw TestCanceledException()
    }
}

interface DiagnosticTest {
    val name: TestName

    /** Blocks until done. Throws [TestCanceledException] when [cancel] turns on. */
    fun run(cancel: CancelSignal): TestOutcome
}

sealed interface TestStatus {
    data object NotRun : TestStatus

    data object Waiting : TestStatus

    data object Running : TestStatus

    data object Canceled : TestStatus

    data class Done(val outcome: TestOutcome) : TestStatus
}

data class TestEntry(val name: TestName, val status: TestStatus)

enum class SuiteStatus { NOT_RUN, RUNNING, PASSED, WARNING, FAILED, CANCELED }

data class SuiteState(val tests: List<TestEntry>) {
    val overall: SuiteStatus get() = OVERALL_RULES.first { (matches, _) -> matches(this) }.second

    val isRunning get() = overall == SuiteStatus.RUNNING

    fun finished(): List<TestEntry> = tests.filter { it.status is TestStatus.Done }

    fun update(index: Int, status: TestStatus) = copy(tests = tests.mapIndexed { i, test -> test.copy(status = status).takeIf { i == index } ?: test })

    fun cancelWaiting() = copy(tests = tests.map { test -> test.copy(status = TestStatus.Canceled).takeIf { test.status == TestStatus.Waiting } ?: test })

    companion object {
        fun of(names: List<TestName>) = SuiteState(names.map { TestEntry(it, TestStatus.NotRun) })
    }
}

private fun SuiteState.hasStatus(vararg statuses: TestStatus) = tests.any { it.status in statuses }

private fun SuiteState.hasOutcome(type: Class<out TestOutcome>) = tests.any { type.isInstance((it.status as? TestStatus.Done)?.outcome) }

/** The first rule that matches gives the suite's status: the worst outcome wins. */
private val OVERALL_RULES: List<Pair<(SuiteState) -> Boolean, SuiteStatus>> = listOf(
    { state: SuiteState -> state.hasStatus(TestStatus.Running, TestStatus.Waiting) } to SuiteStatus.RUNNING,
    { state: SuiteState -> state.hasStatus(TestStatus.Canceled) } to SuiteStatus.CANCELED,
    { state: SuiteState -> state.hasOutcome(Failed::class.java) } to SuiteStatus.FAILED,
    { state: SuiteState -> state.hasOutcome(Warning::class.java) } to SuiteStatus.WARNING,
    { state: SuiteState -> state.tests.isNotEmpty() && state.tests.all { it.status is TestStatus.Done } } to SuiteStatus.PASSED,
    { _: SuiteState -> true } to SuiteStatus.NOT_RUN,
)

/** Runs a suite's tests one after the other, reporting each change. */
class SuiteRunner(private val tests: List<DiagnosticTest>) {
    val names get() = tests.map { it.name }

    fun run(cancel: CancelSignal, onChange: (SuiteState) -> Unit): SuiteState {
        var state = SuiteState(tests.map { TestEntry(it.name, TestStatus.Waiting) })
        for ((index, test) in tests.withIndex()) {
            state = state.update(index, TestStatus.Running).also(onChange)
            val status = runOne(test, cancel)
            state = state.update(index, status)
            if (status == TestStatus.Canceled) return state.cancelWaiting().also(onChange)
            onChange(state)
        }
        return state
    }

    private fun runOne(test: DiagnosticTest, cancel: CancelSignal): TestStatus = try {
        cancel.throwIfCanceled()
        TestStatus.Done(test.run(cancel))
    } catch (_: TestCanceledException) {
        TestStatus.Canceled
    } catch (_: Throwable) {
        // A test that runs out of memory or breaks in any other way is a failed check, not a crash of the app.
        TestStatus.Done(Failed(Finding(FindingKey.TEST_CRASHED)))
    }
}
