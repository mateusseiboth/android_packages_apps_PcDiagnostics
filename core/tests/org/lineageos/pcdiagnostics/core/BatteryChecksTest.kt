package org.lineageos.pcdiagnostics.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryChecksTest {
    private val notCanceled = CancelSignal { false }

    private val chargeUevent = """
        POWER_SUPPLY_NAME=BAT0
        POWER_SUPPLY_TYPE=Battery
        POWER_SUPPLY_STATUS=Discharging
        POWER_SUPPLY_CYCLE_COUNT=123
        POWER_SUPPLY_CURRENT_NOW=1500000
        POWER_SUPPLY_CHARGE_FULL_DESIGN=4000000
        POWER_SUPPLY_CHARGE_FULL=3600000
        POWER_SUPPLY_CHARGE_NOW=1800000
        POWER_SUPPLY_CAPACITY=50
    """.trimIndent()

    private val energyUevent = """
        POWER_SUPPLY_TYPE=Battery
        POWER_SUPPLY_STATUS=Charging
        POWER_SUPPLY_POWER_NOW=15000000
        POWER_SUPPLY_VOLTAGE_NOW=12000000
        POWER_SUPPLY_VOLTAGE_MIN_DESIGN=11400000
        POWER_SUPPLY_ENERGY_FULL_DESIGN=57000000
        POWER_SUPPLY_ENERGY_FULL=51300000
        POWER_SUPPLY_ENERGY_NOW=25650000
    """.trimIndent()

    @Test
    fun `reads a battery that reports charge in microamp hours`() {
        val battery = BatteryInfo.parse(chargeUevent)!!

        assertEquals(BatteryStatus.DISCHARGING, battery.status)
        assertEquals(50.0, battery.levelPercent, 0.001)
        assertEquals(90, battery.healthPercent)
        assertEquals(123, battery.cycleCount)
        assertEquals(1500, battery.currentMa)
        assertEquals(4000, battery.designCapacityMah)
        assertEquals(72, battery.minutesLeft)
    }

    @Test
    fun `reads a battery that reports energy in microwatt hours`() {
        val battery = BatteryInfo.parse(energyUevent)!!

        assertEquals(BatteryStatus.CHARGING, battery.status)
        assertEquals(50.0, battery.levelPercent, 0.001)
        assertEquals(90, battery.healthPercent)
        assertEquals(5000, battery.designCapacityMah)
        assertEquals(1250, battery.currentMa)
        assertEquals(102, battery.minutesLeft)
        assertNull(battery.cycleCount)
    }

    @Test
    fun `a power supply that is not a battery is ignored`() {
        assertNull(BatteryInfo.parse("POWER_SUPPLY_TYPE=Mains\nPOWER_SUPPLY_ONLINE=1\n"))
    }

    @Test
    fun `falls back to the capacity percentage`() {
        val battery = BatteryInfo.parse("POWER_SUPPLY_TYPE=Battery\nPOWER_SUPPLY_STATUS=Full\nPOWER_SUPPLY_CAPACITY=100\n")!!

        assertEquals(BatteryStatus.FULL, battery.status)
        assertEquals(100.0, battery.levelPercent, 0.0)
        assertNull(battery.minutesLeft)
        assertNull(battery.healthPercent)
    }

    private fun battery(status: BatteryStatus, level: Double) = BatteryInfo(status, level, null, null, null, null, null)

    private fun rateTest(kind: RateKind, vararg readings: BatteryInfo?): BatteryRateTest {
        val queue = ArrayDeque(readings.toList())
        return BatteryRateTest(kind, readBattery = { queue.removeFirst() }, durationMillis = 1_000, sleep = {})
    }

    @Test
    fun `charge test passes with how much the level went up`() {
        val outcome = rateTest(RateKind.CHARGE, battery(BatteryStatus.CHARGING, 50.0), battery(BatteryStatus.CHARGING, 50.75)).run(notCanceled)

        assertEquals(Passed(Finding(FindingKey.BATTERY_CHARGE_INCREASED, listOf(0.75))), outcome)
    }

    @Test
    fun `charge test fails when the level went down`() {
        val outcome = rateTest(RateKind.CHARGE, battery(BatteryStatus.CHARGING, 50.0), battery(BatteryStatus.CHARGING, 49.0)).run(notCanceled)

        assertEquals(Failed(Finding(FindingKey.BATTERY_CHARGE_DECREASED)), outcome)
    }

    @Test
    fun `charge test cannot run on a full battery`() {
        val outcome = rateTest(RateKind.CHARGE, battery(BatteryStatus.FULL, 100.0)).run(notCanceled)

        assertEquals(Failed(Finding(FindingKey.BATTERY_FULL)), outcome)
    }

    @Test
    fun `discharge test passes with how much the level went down`() {
        val outcome = rateTest(RateKind.DISCHARGE, battery(BatteryStatus.DISCHARGING, 80.0), battery(BatteryStatus.DISCHARGING, 79.5)).run(notCanceled)

        assertEquals(Passed(Finding(FindingKey.BATTERY_DISCHARGE_DECREASED, listOf(0.5))), outcome)
    }

    @Test
    fun `discharge test fails when the level went up`() {
        val outcome = rateTest(RateKind.DISCHARGE, battery(BatteryStatus.DISCHARGING, 80.0), battery(BatteryStatus.DISCHARGING, 81.0)).run(notCanceled)

        assertEquals(Failed(Finding(FindingKey.BATTERY_DISCHARGE_INCREASED)), outcome)
    }

    @Test
    fun `a status change during the test fails it`() {
        val outcome = rateTest(RateKind.DISCHARGE, battery(BatteryStatus.DISCHARGING, 80.0), battery(BatteryStatus.CHARGING, 80.0)).run(notCanceled)

        assertEquals(Failed(Finding(FindingKey.BATTERY_STATUS_CHANGED)), outcome)
    }

    @Test
    fun `no battery fails the test`() {
        assertEquals(Failed(Finding(FindingKey.BATTERY_UNAVAILABLE)), rateTest(RateKind.CHARGE, null).run(notCanceled))
    }

    @Test(expected = TestCanceledException::class)
    fun `the wait stops when canceled`() {
        rateTest(RateKind.DISCHARGE, battery(BatteryStatus.DISCHARGING, 80.0), battery(BatteryStatus.DISCHARGING, 79.0)).run { true }
    }

    @Test
    fun `plugged in batteries get the charge test, others the discharge test`() {
        assertEquals(TestName.BATTERY_CHARGE, BatterySuite.chooseKind(battery(BatteryStatus.CHARGING, 1.0)).testName)
        assertEquals(TestName.BATTERY_CHARGE, BatterySuite.chooseKind(battery(BatteryStatus.FULL, 100.0)).testName)
        assertEquals(TestName.BATTERY_DISCHARGE, BatterySuite.chooseKind(battery(BatteryStatus.DISCHARGING, 1.0)).testName)
    }
}
