package org.autojs.plugin.jvmsource.kotlin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleActiveSessionGateTest {
    @Test
    fun rejectsConcurrentSessionWithoutQueueAndAdmitsNextAfterExactTerminalRelease() {
        val gate = SingleActiveSessionGate<Any>()
        val active = Any()
        val concurrent = Any()
        val recovered = Any()

        assertTrue(gate.tryAcquire(active))
        assertFalse(gate.tryAcquire(concurrent))
        assertFalse(gate.release(concurrent))
        assertTrue(gate.release(active))
        assertTrue(gate.tryAcquire(recovered))
    }
}
