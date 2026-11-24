package org.lineageos.pcdiagnostics.core

internal val WHITESPACE = Regex("\\s+")

/** "key: value" lines, as in /proc/meminfo and /proc/cpuinfo. */
internal fun parseColonFields(text: String): List<Pair<String, String>> = text.lineSequence()
    .mapNotNull { line -> line.split(':', limit = 2).takeIf { it.size == 2 } }
    .map { (key, value) -> key.trim() to value.trim() }
    .toList()

/** Machine CPU time in clock ticks, from the aggregate line of /proc/stat. */
data class CpuTimes(val user: Long, val system: Long, val idle: Long) {
    val totalTicks get() = user + system + idle

    companion object {
        fun parse(stat: String): CpuTimes? {
            val line = stat.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return null
            val ticks = line.split(WHITESPACE).drop(1).map { it.toLongOrNull() ?: 0L }
            val at = { index: Int -> ticks.getOrElse(index) { 0L } }
            // user nice system idle iowait irq softirq steal
            return CpuTimes(user = at(0) + at(1), system = at(2) + at(5) + at(6) + at(7), idle = at(3) + at(4))
        }
    }
}

data class CpuUsage(val userPercent: Double, val systemPercent: Double) {
    val totalPercent get() = userPercent + systemPercent

    companion object {
        val ZERO = CpuUsage(0.0, 0.0)

        fun between(before: CpuTimes, after: CpuTimes): CpuUsage {
            val elapsed = after.totalTicks - before.totalTicks
            if (elapsed <= 0) return ZERO
            val percentOf = { ticks: Long -> 100.0 * ticks.coerceAtLeast(0) / elapsed }
            return CpuUsage(percentOf(after.user - before.user), percentOf(after.system - before.system))
        }
    }
}

data class CpuModel(val name: String, val threads: Int) {
    companion object {
        fun parse(cpuinfo: String): CpuModel {
            val fields = parseColonFields(cpuinfo)
            val name = fields.firstOrNull { it.first == "model name" }?.second.orEmpty()
            return CpuModel(name.replace(WHITESPACE, " "), fields.count { it.first == "processor" }.coerceAtLeast(1))
        }
    }
}

object CpuSpeed {
    /** Average of the cores' scaling_cur_freq files, in kHz; null when none can be read. */
    fun averageKhz(files: List<String>): Long? {
        val values = files.mapNotNull { it.trim().toLongOrNull() }
        if (values.isEmpty()) return null
        return values.sum() / values.size
    }
}

/** Memory in use is what the kernel cannot hand out right away: total minus available. */
data class MemorySummary(val totalBytes: Long, val availableBytes: Long) {
    val usedBytes get() = totalBytes - availableBytes
    val usedFraction get() = usedBytes.toFloat() / totalBytes.coerceAtLeast(1)

    companion object {
        fun parse(meminfo: String): MemorySummary {
            val fields = parseColonFields(meminfo).toMap()
            val kilobytes = { key: String -> fields[key]?.split(WHITESPACE)?.first()?.toLongOrNull() ?: 0L }
            return MemorySummary(kilobytes("MemTotal") * 1024, kilobytes("MemAvailable") * 1024)
        }
    }
}

/** A temperature sensor: its name (thermal zone type or hwmon name) and its reading in m°C. */
data class SensorReading(val name: String, val milliCelsius: Int)

object Temperature {
    /** Processor package sensors, best first: Intel, then AMD, then generic SoC zones. */
    private val CPU_SENSORS = listOf("x86_pkg_temp", "coretemp", "k10temp", "zenpower", "cpu_thermal", "cpu-thermal", "soc_thermal")

    fun pickCpuMilliCelsius(sensors: List<SensorReading>): Int? {
        val preferred = CPU_SENSORS.firstNotNullOfOrNull { name -> sensors.firstOrNull { it.name == name } }
        return preferred?.milliCelsius ?: sensors.maxOfOrNull { it.milliCelsius }
    }
}
