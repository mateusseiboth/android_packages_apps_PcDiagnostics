package org.lineageos.pcdiagnostics.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HardwareRowTest {
    @Test
    fun `settings asks for each row by the method in its summary uri`() {
        assertEquals(HardwareRow.CPU, HardwareRow.fromMethod("cpu"))
        assertEquals(HardwareRow.MEMORY, HardwareRow.fromMethod("memory"))
        assertEquals(HardwareRow.STORAGE, HardwareRow.fromMethod("storage"))
    }

    @Test
    fun `unknown methods have no row`() {
        assertNull(HardwareRow.fromMethod("battery"))
        assertNull(HardwareRow.fromMethod(null))
    }
}
