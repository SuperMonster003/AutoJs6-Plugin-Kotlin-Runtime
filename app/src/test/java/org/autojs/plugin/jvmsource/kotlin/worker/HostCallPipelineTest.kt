package org.autojs.plugin.jvmsource.kotlin.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HostCallPipelineTest {
    @Test
    fun executesAuthorizationPayloadDispatchAndResponseInExactOrder() {
        val stages = mutableListOf<String>()

        val result = HostCallPipeline.execute(
            authorize = { stages += AUTHORIZATION },
            validatePayload = {
                stages += PAYLOAD
                "request"
            },
            dispatch = { payload ->
                assertEquals("request", payload)
                stages += DISPATCH
                "response"
            },
            validateResponse = { response ->
                assertEquals("response", response)
                stages += RESPONSE
                42
            },
        )

        assertEquals(42, result)
        assertEquals(listOf(AUTHORIZATION, PAYLOAD, DISPATCH, RESPONSE), stages)
    }

    @Test
    fun authorizationFailureStopsBeforePayloadAndDispatch() {
        assertStopsAt(AUTHORIZATION)
    }

    @Test
    fun payloadFailureStopsBeforeDispatch() {
        assertStopsAt(PAYLOAD)
    }

    @Test
    fun dispatchFailureStopsBeforeResponseValidation() {
        assertStopsAt(DISPATCH)
    }

    @Test
    fun responseFailureOccursOnlyAfterOneDispatch() {
        assertStopsAt(RESPONSE)
    }

    private fun assertStopsAt(failingStage: String) {
        val stages = mutableListOf<String>()

        assertThrows(IllegalStateException::class.java) {
            HostCallPipeline.execute(
                authorize = {
                    stages += AUTHORIZATION
                    failAt(AUTHORIZATION, failingStage)
                },
                validatePayload = {
                    stages += PAYLOAD
                    failAt(PAYLOAD, failingStage)
                    "request"
                },
                dispatch = {
                    stages += DISPATCH
                    failAt(DISPATCH, failingStage)
                    "response"
                },
                validateResponse = {
                    stages += RESPONSE
                    failAt(RESPONSE, failingStage)
                    true
                },
            )
        }

        val expected = listOf(AUTHORIZATION, PAYLOAD, DISPATCH, RESPONSE)
            .take(expectedStageIndex.getValue(failingStage) + 1)
        assertEquals(expected, stages)
    }

    private fun failAt(stage: String, failingStage: String) {
        if (stage == failingStage) throw IllegalStateException("$stage failed")
    }

    private companion object {
        const val AUTHORIZATION = "authorization"
        const val PAYLOAD = "payload"
        const val DISPATCH = "dispatch"
        const val RESPONSE = "response"
        val expectedStageIndex = listOf(AUTHORIZATION, PAYLOAD, DISPATCH, RESPONSE)
            .withIndex()
            .associate { it.value to it.index }
    }
}

