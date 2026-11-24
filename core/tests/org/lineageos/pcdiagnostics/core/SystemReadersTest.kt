package org.lineageos.pcdiagnostics.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemReadersTest {
    @Test
    fun `cpu usage between two samples of proc stat`() {
        val before = CpuTimes.parse("cpu  100 0 50 850 0 0 0 0\n")!!
        val after = CpuTimes.parse("cpu  130 0 60 910 0 0 0 0\n")!!

        val usage = CpuUsage.between(before, after)

        assertEquals(30.0, usage.userPercent, 0.001)
        assertEquals(10.0, usage.systemPercent, 0.001)
    }

    @Test
    fun `memory used is total minus available`() {
        val memory = MemorySummary.parse("MemTotal: 8000 kB\nMemFree: 1000 kB\nMemAvailable: 6000 kB\n")

        assertEquals(8000L * 1024, memory.totalBytes)
        assertEquals(2000L * 1024, memory.usedBytes)
        assertEquals(0.25f, memory.usedFraction)
    }

    @Test
    fun `cpu model and threads`() {
        val model = CpuModel.parse("processor : 0\nmodel name : AMD  Ryzen 7\nprocessor : 1\nmodel name : AMD  Ryzen 7\n")

        assertEquals(CpuModel("AMD Ryzen 7", 2), model)
    }

    @Test
    fun `cpu cores are the distinct cores behind the threads`() {
        // A 4-core, 8-thread processor lists every core id twice.
        val cpuinfo = (0 until 8).joinToString("\n\n") { "processor : $it\nmodel name : Intel Core i5\nphysical id : 0\ncore id : ${it % 4}" }

        assertEquals(CpuModel("Intel Core i5", threads = 8, cores = 4), CpuModel.parse(cpuinfo))
    }

    @Test
    fun `cpu cores count every socket`() {
        val cpuinfo = listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1)
            .mapIndexed { index, (socket, core) -> "processor : $index\nphysical id : $socket\ncore id : $core" }
            .joinToString("\n\n")

        assertEquals(4, CpuModel.parse(cpuinfo).cores)
    }

    @Test
    fun `cpu cores are the threads when cpuinfo has no core ids`() {
        assertEquals(2, CpuModel.parse("processor : 0\n\nprocessor : 1\n").cores)
    }

    @Test
    fun `memory total rounds up to whole gigabytes`() {
        // The kernel keeps part of the installed memory for itself: 16 GB shows as about 15.5 GiB.
        assertEquals(16, MemorySummary(totalBytes = 16_252_928L * 1024, availableBytes = 0).totalGigabytesRoundedUp)
        assertEquals(8, MemorySummary(totalBytes = 8L shl 30, availableBytes = 0).totalGigabytesRoundedUp)
        assertEquals(0, MemorySummary(totalBytes = 0, availableBytes = 0).totalGigabytesRoundedUp)
    }

    @Test
    fun `current speed is the average of the cores`() {
        assertEquals(2_000_000L, CpuSpeed.averageKhz(listOf("1500000\n", "2500000\n", "garbage")))
        assertNull(CpuSpeed.averageKhz(emptyList()))
    }

    @Test
    fun `temperature prefers the package sensor over the board`() {
        val sensors = listOf(SensorReading("acpitz", 40_000), SensorReading("k10temp", 55_000), SensorReading("nvme", 70_000))

        assertEquals(55_000, Temperature.pickCpuMilliCelsius(sensors))
    }

    @Test
    fun `temperature falls back to the hottest sensor`() {
        assertEquals(70_000, Temperature.pickCpuMilliCelsius(listOf(SensorReading("foo", 40_000), SensorReading("bar", 70_000))))
        assertNull(Temperature.pickCpuMilliCelsius(emptyList()))
    }
}
