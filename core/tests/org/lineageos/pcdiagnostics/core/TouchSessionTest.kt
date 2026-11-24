package org.lineageos.pcdiagnostics.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TouchSessionTest {
    private fun point(id: Int, x: Float, y: Float = 0.5f) = TouchPoint(id, x, y)

    @Test
    fun `a finger draws one trace from down to up`() {
        val session = TouchSession()
            .onPointers(listOf(point(0, 0.1f)))
            .onPointers(listOf(point(0, 0.2f)))
            .onPointers(listOf(point(0, 0.3f)))
            .onPointers(emptyList())

        assertEquals(1, session.traces.size)
        assertEquals(listOf(0.1f, 0.2f, 0.3f), session.traces.single().points.map { it.x })
        assertEquals(emptyMap<Int, Int>(), session.active)
    }

    @Test
    fun `fingers down together get different colors`() {
        val session = TouchSession(colorCount = 5).onPointers(listOf(point(0, 0.1f), point(1, 0.5f), point(7, 0.9f)))

        assertEquals(listOf(0, 1, 2), session.traces.map { it.colorSlot })
        assertEquals(3, session.maxFingers)
    }

    @Test
    fun `a lifted finger frees its color for the next one`() {
        val session = TouchSession()
            .onPointers(listOf(point(0, 0.1f), point(1, 0.5f)))
            .onPointers(listOf(point(1, 0.6f)))
            .onPointers(listOf(point(1, 0.7f), point(2, 0.2f)))

        assertEquals(0, session.traces.last().colorSlot)
        assertNotEquals(session.traces[1].colorSlot, session.traces.last().colorSlot)
        assertEquals(2, session.maxFingers)
    }

    @Test
    fun `more fingers than colors reuse colors`() {
        val session = TouchSession(colorCount = 2).onPointers((0..3).map { point(it, it / 4f) })

        assertEquals(listOf(0, 1, 0, 1), session.traces.map { it.colorSlot })
    }

    @Test
    fun `old traces and long traces are trimmed`() {
        val many = (0 until 5).fold(TouchSession(maxTraces = 3)) { session, id -> session.onPointers(listOf(point(id, 0f))).onPointers(emptyList()) }
        val long = (0 until 10).fold(TouchSession(maxPoints = 4)) { session, step -> session.onPointers(listOf(point(0, step / 10f))) }

        assertEquals(3, many.traces.size)
        assertEquals(listOf(0.6f, 0.7f, 0.8f, 0.9f), long.traces.single().points.map { it.x })
    }

    @Test
    fun `button presses are counted once per click and shown while held`() {
        val session = TouchSession()
            .onButtons(PointerButton.PRIMARY.bit)
            .onButtons(PointerButton.PRIMARY.bit)
            .onButtons(0)
            .onButtons(PointerButton.SECONDARY.bit or PointerButton.TERTIARY.bit)

        assertEquals(setOf(PointerButton.SECONDARY, PointerButton.TERTIARY), session.held)
        assertEquals(mapOf(PointerButton.PRIMARY to 1, PointerButton.SECONDARY to 1, PointerButton.TERTIARY to 1), session.clicks)
    }

    @Test
    fun `clearing keeps nothing`() {
        val session = TouchSession().onPointers(listOf(point(0, 0.1f))).onButtons(1).clear()

        assertEquals(TouchSession(), session)
    }
}
