package org.lineageos.pcdiagnostics.core

/** CPU usage between successive /proc/stat samples, with the last [historySize] values for the chart. */
class CpuMonitor(private val historySize: Int = 60) {
    private var previous: CpuTimes? = null

    var history: List<CpuUsage> = emptyList()
        private set

    fun onSample(times: CpuTimes): CpuUsage {
        val usage = previous?.let { CpuUsage.between(it, times) } ?: CpuUsage.ZERO
        previous = times
        history = (history + usage).takeLast(historySize)
        return usage
    }
}

/** A chart point with x and y from 0 to 1; y grows downwards, as on the screen. */
data class ChartPoint(val x: Float, val y: Float)

/** Places values on a chart that holds [capacity] of them, newest on the right. */
class ChartScale(private val capacity: Int, private val maxValue: Double) {
    fun buildPoints(values: List<Double>): List<ChartPoint> {
        val visible = values.takeLast(capacity)
        val firstSlot = capacity - visible.size
        val lastSlot = (capacity - 1).coerceAtLeast(1).toFloat()
        return visible.mapIndexed { index, value ->
            ChartPoint(x = (firstSlot + index) / lastSlot, y = 1f - (value / maxValue).coerceIn(0.0, 1.0).toFloat())
        }
    }
}
