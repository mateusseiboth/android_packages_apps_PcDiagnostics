package org.lineageos.pcdiagnostics.core

/** A finger on a touchpad or touchscreen, at x and y from 0 to 1. */
data class TouchPoint(val id: Int, val x: Float, val y: Float)

/** The path of one finger, drawn in the color of its [colorSlot]. */
data class TouchTrace(val pointerId: Int, val colorSlot: Int, val points: List<TouchPoint>)

/** MotionEvent button bits. */
enum class PointerButton(val bit: Int) {
    PRIMARY(1),
    SECONDARY(2),
    TERTIARY(4),
    ;

    companion object {
        fun fromState(buttonState: Int): Set<PointerButton> = entries.filter { buttonState and it.bit != 0 }.toSet()
    }
}

/**
 * What a touch tester draws. Each event gives the fingers down now: a new finger starts a trace in the
 * first free color, a lifted one frees its color. Old traces and old points are dropped past the limits.
 */
data class TouchSession(
    val colorCount: Int = 10,
    val maxTraces: Int = 40,
    val maxPoints: Int = 2_000,
    val traces: List<TouchTrace> = emptyList(),
    /** Fingers down now: pointer id to color slot. */
    val active: Map<Int, Int> = emptyMap(),
    val maxFingers: Int = 0,
    val held: Set<PointerButton> = emptySet(),
    val clicks: Map<PointerButton, Int> = emptyMap(),
) {
    fun onPointers(points: List<TouchPoint>): TouchSession {
        val ids = points.map { it.id }.toSet()
        return points.fold(copy(active = active.filterKeys { it in ids })) { session, point -> session.place(point) }
            .copy(maxFingers = maxOf(maxFingers, points.size))
    }

    /** Counts a click when a button goes down; [held] shows the buttons down now. */
    fun onButtons(buttonState: Int): TouchSession {
        val now = PointerButton.fromState(buttonState)
        val counted = (now - held).fold(clicks) { counts, button -> counts + (button to (counts[button] ?: 0) + 1) }
        return copy(held = now, clicks = counted)
    }

    fun clear() = TouchSession(colorCount, maxTraces, maxPoints)

    private fun place(point: TouchPoint): TouchSession = active[point.id]?.let { extend(point) } ?: start(point)

    private fun start(point: TouchPoint): TouchSession {
        val slot = findFreeSlot()
        return copy(traces = (traces + TouchTrace(point.id, slot, listOf(point))).takeLast(maxTraces), active = active + (point.id to slot))
    }

    private fun extend(point: TouchPoint): TouchSession {
        val index = traces.indexOfLast { it.pointerId == point.id }
        if (index < 0) return start(point)
        val trace = traces[index]
        val extended = trace.copy(points = (trace.points + point).takeLast(maxPoints))
        return copy(traces = traces.toMutableList().apply { set(index, extended) })
    }

    /** The first color no finger down is using; with more fingers than colors, colors repeat in turn. */
    private fun findFreeSlot(): Int = (0 until colorCount).firstOrNull { it !in active.values } ?: (active.size % colorCount)
}
