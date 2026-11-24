package org.lineageos.pcdiagnostics.core

import kotlin.math.roundToInt

enum class BatteryStatus { CHARGING, DISCHARGING, NOT_CHARGING, FULL, UNKNOWN }

private val STATUSES = mapOf(
    "Charging" to BatteryStatus.CHARGING,
    "Discharging" to BatteryStatus.DISCHARGING,
    "Not charging" to BatteryStatus.NOT_CHARGING,
    "Full" to BatteryStatus.FULL,
)

/**
 * Batteries report either charge (µAh, current in µA) or energy (µWh, power in µW). Ratios work the same
 * in both; capacity in mAh and current in mA need the voltage when the battery reports energy.
 */
private class BatteryUnits(
    val fullKey: String,
    val designKey: String,
    val nowKey: String,
    val rateKey: String,
    val toMilliamps: (value: Long, volts: Long?) -> Int?,
)

private val CHARGE_UNITS = BatteryUnits(
    "POWER_SUPPLY_CHARGE_FULL", "POWER_SUPPLY_CHARGE_FULL_DESIGN", "POWER_SUPPLY_CHARGE_NOW", "POWER_SUPPLY_CURRENT_NOW",
) { microamps, _ -> (microamps / 1000).toInt() }

private val ENERGY_UNITS = BatteryUnits(
    "POWER_SUPPLY_ENERGY_FULL", "POWER_SUPPLY_ENERGY_FULL_DESIGN", "POWER_SUPPLY_ENERGY_NOW", "POWER_SUPPLY_POWER_NOW",
) { microwatts, microvolts -> microvolts?.takeIf { it > 0 }?.let { (microwatts * 1000 / it).toInt() } }

data class BatteryInfo(
    val status: BatteryStatus,
    val levelPercent: Double,
    val healthPercent: Int?,
    val cycleCount: Int?,
    val currentMa: Int?,
    val designCapacityMah: Int?,
    val minutesLeft: Int?,
) {
    companion object {
        /** A power supply's uevent file; null when it is not a battery. */
        fun parse(uevent: String): BatteryInfo? {
            val fields = uevent.lineSequence().mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { (key, value) -> key.trim() to value.trim() }
            if (fields["POWER_SUPPLY_TYPE"] != "Battery") return null
            val number = { key: String -> fields[key]?.toLongOrNull() }
            val units = CHARGE_UNITS.takeIf { number(it.fullKey) != null } ?: ENERGY_UNITS
            val full = number(units.fullKey)
            val now = number(units.nowKey)
            val rate = number(units.rateKey)?.let(Math::abs)?.takeIf { it > 0 }
            val status = STATUSES[fields["POWER_SUPPLY_STATUS"]] ?: BatteryStatus.UNKNOWN
            return BatteryInfo(
                status = status,
                levelPercent = ratioPercent(now, full) ?: number("POWER_SUPPLY_CAPACITY")?.toDouble() ?: 0.0,
                healthPercent = ratioPercent(full, number(units.designKey))?.roundToInt(),
                cycleCount = number("POWER_SUPPLY_CYCLE_COUNT")?.toInt()?.takeIf { it > 0 },
                currentMa = rate?.let { units.toMilliamps(it, number("POWER_SUPPLY_VOLTAGE_NOW")) },
                designCapacityMah = number(units.designKey)?.let { units.toMilliamps(it, number("POWER_SUPPLY_VOLTAGE_MIN_DESIGN")) },
                minutesLeft = estimateMinutes(status, now, full, rate),
            )
        }

        private fun ratioPercent(part: Long?, whole: Long?): Double? {
            if (part == null || whole == null || whole <= 0) return null
            return 100.0 * part / whole
        }

        /** What is left to drain or to fill, at the current rate. */
        private val REMAINING: Map<BatteryStatus, (now: Long, full: Long) -> Long> = mapOf(
            BatteryStatus.DISCHARGING to { now, _ -> now },
            BatteryStatus.CHARGING to { now, full -> full - now },
        )

        private fun estimateMinutes(status: BatteryStatus, now: Long?, full: Long?, rate: Long?): Int? {
            val remaining = REMAINING[status] ?: return null
            if (now == null || full == null || rate == null) return null
            return (remaining(now, full) * 60 / rate).toInt()
        }
    }
}

/** Charge or discharge: which status the test needs and how the level change is judged. */
enum class RateKind(val testName: TestName) {
    CHARGE(TestName.BATTERY_CHARGE) {
        override fun refuse(before: BatteryInfo) = Failed(Finding(FindingKey.BATTERY_FULL)).takeIf { before.status == BatteryStatus.FULL }

        override fun judge(change: Double): TestOutcome {
            if (change < 0) return Failed(Finding(FindingKey.BATTERY_CHARGE_DECREASED))
            return Passed(Finding(FindingKey.BATTERY_CHARGE_INCREASED, listOf(change)))
        }
    },
    DISCHARGE(TestName.BATTERY_DISCHARGE) {
        override fun refuse(before: BatteryInfo): TestOutcome? = null

        override fun judge(change: Double): TestOutcome {
            if (change > 0) return Failed(Finding(FindingKey.BATTERY_DISCHARGE_INCREASED))
            return Passed(Finding(FindingKey.BATTERY_DISCHARGE_DECREASED, listOf(-change)))
        }
    };

    abstract fun refuse(before: BatteryInfo): TestOutcome?

    abstract fun judge(change: Double): TestOutcome
}

/** Reads the level, waits [durationMillis] and reads it again; the change must go the way [kind] expects. */
class BatteryRateTest(
    private val kind: RateKind,
    private val readBattery: () -> BatteryInfo?,
    private val durationMillis: Long = 30_000,
    private val sleep: (Long) -> Unit = Thread::sleep,
) : DiagnosticTest {
    override val name = kind.testName

    override fun run(cancel: CancelSignal): TestOutcome {
        val before = readBattery() ?: return unavailable()
        kind.refuse(before)?.let { return it }
        waitFor(cancel)
        val after = readBattery() ?: return unavailable()
        if (after.status != before.status) return Failed(Finding(FindingKey.BATTERY_STATUS_CHANGED))
        return kind.judge(roundHundredths(after.levelPercent - before.levelPercent))
    }

    private fun waitFor(cancel: CancelSignal) {
        repeat((durationMillis / STEP_MILLIS).toInt().coerceAtLeast(1)) {
            cancel.throwIfCanceled()
            sleep(STEP_MILLIS)
        }
        cancel.throwIfCanceled()
    }

    private fun unavailable() = Failed(Finding(FindingKey.BATTERY_UNAVAILABLE))

    private fun roundHundredths(value: Double) = Math.round(value * 100) / 100.0

    private companion object {
        const val STEP_MILLIS = 500L
    }
}

object BatterySuite {
    private val PLUGGED_IN = setOf(BatteryStatus.CHARGING, BatteryStatus.FULL, BatteryStatus.NOT_CHARGING)

    fun chooseKind(battery: BatteryInfo): RateKind = RateKind.CHARGE.takeIf { battery.status in PLUGGED_IN } ?: RateKind.DISCHARGE
}
