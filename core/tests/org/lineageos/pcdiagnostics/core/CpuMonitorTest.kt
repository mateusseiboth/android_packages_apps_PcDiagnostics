package org.lineageos.pcdiagnostics.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CpuMonitorTest {
    @Test
    fun `the first sample has no usage yet`() {
        val monitor = CpuMonitor(historySize = 3)

        assertEquals(CpuUsage.ZERO, monitor.onSample(CpuTimes(10, 10, 80)))
        assertEquals(listOf(CpuUsage.ZERO), monitor.history)
    }

    @Test
    fun `keeps the last samples only`() {
        val monitor = CpuMonitor(historySize = 2)
        listOf(0L, 100L, 200L, 300L).forEach { monitor.onSample(CpuTimes(it / 2, it / 4, it / 4)) }

        assertEquals(2, monitor.history.size)
        assertEquals(75.0, monitor.history.last().totalPercent, 0.001)
    }

    @Test
    fun `chart points put the newest on the right edge, clamped to the top`() {
        val points = ChartScale(capacity = 3, maxValue = 100.0).buildPoints(listOf(50.0, 150.0))

        assertEquals(listOf(ChartPoint(0.5f, 0.5f), ChartPoint(1f, 0f)), points)
    }
}
