package org.lineageos.pcdiagnostics

import android.os.StatFs
import java.io.File
import org.lineageos.pcdiagnostics.core.BatteryInfo
import org.lineageos.pcdiagnostics.core.CpuModel
import org.lineageos.pcdiagnostics.core.CpuSpeed
import org.lineageos.pcdiagnostics.core.CpuTimes
import org.lineageos.pcdiagnostics.core.MemorySummary
import org.lineageos.pcdiagnostics.core.SensorReading
import org.lineageos.pcdiagnostics.core.StorageSpace
import org.lineageos.pcdiagnostics.core.Temperature

/** Reads the machine from /proc and /sys; the parsing is in core. */
class SystemSource(private val dataDir: File) {
    private val cpuDir = Regex("cpu\\d+")

    fun readCpuTimes(): CpuTimes? = readText("/proc/stat")?.let(CpuTimes::parse)

    fun readCpuModel() = CpuModel.parse(readText("/proc/cpuinfo").orEmpty())

    fun readCpuSpeedKhz(): Long? = CpuSpeed.averageKhz(
        listDir("/sys/devices/system/cpu").filter { cpuDir.matches(it.name) }.mapNotNull { readText("${it.path}/cpufreq/scaling_cur_freq") },
    )

    fun readCpuMilliCelsius(): Int? = Temperature.pickCpuMilliCelsius(
        listDir("/sys/class/thermal").filter { it.name.startsWith("thermal_zone") }.mapNotNull { readSensor(it, "type", "temp") } +
            listDir("/sys/class/hwmon").mapNotNull { readSensor(it, "name", "temp1_input") },
    )

    fun readMemory() = MemorySummary.parse(readText("/proc/meminfo").orEmpty())

    fun readStorage(): StorageSpace = StatFs(dataDir.path).let { StorageSpace(it.totalBytes, it.availableBytes) }

    /** The first power supply that is a battery; PCs on mains power have none. */
    fun readBattery(): BatteryInfo? =
        listDir("/sys/class/power_supply").firstNotNullOfOrNull { supply -> readText("${supply.path}/uevent")?.let(BatteryInfo::parse) }

    private fun readSensor(dir: File, nameFile: String, tempFile: String): SensorReading? {
        val name = readText("${dir.path}/$nameFile")?.trim() ?: return null
        val milliCelsius = readText("${dir.path}/$tempFile")?.trim()?.toIntOrNull() ?: return null
        return SensorReading(name, milliCelsius)
    }

    private fun listDir(path: String): List<File> = File(path).listFiles().orEmpty().sortedBy { it.name }

    private fun readText(path: String): String? = runCatching { File(path).readText() }.getOrNull()
}
