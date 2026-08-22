package org.autojs.plugin.jvmsource.kotlin.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerServiceBindingLifecycleTest {
    @Test
    fun synchronousConnectionCleanupDefersUnbindUntilSuccessfulBindReturns() {
        val lifecycle = WorkerServiceBindingLifecycle()

        assertTrue(lifecycle.beginBinding())
        assertEquals(WorkerServiceBindingLifecycle.Action.NONE, lifecycle.requestUnbind())
        assertEquals(WorkerServiceBindingLifecycle.State.UNBIND_PENDING, lifecycle.snapshot())
        assertEquals(
            WorkerServiceBindingLifecycle.Action.UNBIND_NOW,
            lifecycle.bindingReturned(bound = true),
        )
        assertEquals(WorkerServiceBindingLifecycle.State.UNBOUND, lifecycle.snapshot())
        assertEquals(WorkerServiceBindingLifecycle.Action.NONE, lifecycle.requestUnbind())
    }

    @Test
    fun failedBindRollsBackWithoutAnUnbindOrReusableBindingAttempt() {
        val lifecycle = WorkerServiceBindingLifecycle()

        assertTrue(lifecycle.beginBinding())
        assertEquals(
            WorkerServiceBindingLifecycle.Action.NONE,
            lifecycle.bindingReturned(bound = false),
        )
        assertEquals(WorkerServiceBindingLifecycle.State.UNBOUND, lifecycle.snapshot())
        assertEquals(WorkerServiceBindingLifecycle.Action.NONE, lifecycle.requestUnbind())
        assertFalse(lifecycle.beginBinding())
    }

    @Test
    fun ordinarySuccessfulBindingUnbindsExactlyOnce() {
        val lifecycle = WorkerServiceBindingLifecycle()

        assertTrue(lifecycle.beginBinding())
        assertEquals(
            WorkerServiceBindingLifecycle.Action.NONE,
            lifecycle.bindingReturned(bound = true),
        )
        assertEquals(WorkerServiceBindingLifecycle.Action.UNBIND_NOW, lifecycle.requestUnbind())
        assertEquals(WorkerServiceBindingLifecycle.Action.NONE, lifecycle.requestUnbind())
    }
}
