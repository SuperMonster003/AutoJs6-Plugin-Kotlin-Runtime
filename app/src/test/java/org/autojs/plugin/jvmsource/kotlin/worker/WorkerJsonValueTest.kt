package org.autojs.plugin.jvmsource.kotlin.worker

import org.autojs.plugin.jvmsource.kotlin.JavaProviderFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkerJsonValueTest {
    @Test
    fun encodesTheBoundedR1JsonProfile() {
        assertEquals("true", WorkerJsonValue.encode(true))
        assertEquals("[1,\"x\",null]", WorkerJsonValue.encode(listOf(1, "x", null)))
        assertEquals("{\"ok\":false}", WorkerJsonValue.encode(linkedMapOf("ok" to false)))
    }

    @Test
    fun rejectsCyclesNonFiniteNumbersUnsupportedObjectsAndOversizeStrings() {
        val cycle = arrayListOf<Any?>()
        cycle.add(cycle)
        listOf<Any?>(
            cycle,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Any(),
            "x".repeat(70_000),
        ).forEach { value ->
            assertThrows(JavaProviderFailure::class.java) { WorkerJsonValue.encode(value) }
        }
    }
}
