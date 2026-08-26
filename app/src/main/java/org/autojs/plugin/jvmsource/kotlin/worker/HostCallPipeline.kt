package org.autojs.plugin.jvmsource.kotlin.worker

/**
 * Fixed four-stage template for every host-bridged script capability.
 *
 * A future capability must not dispatch until authorization and payload validation both complete,
 * and it must not return host data until the response-specific validator accepts it.
 */
internal object HostCallPipeline {
    fun <Payload, Response, Result> execute(
        authorize: () -> Unit,
        validatePayload: () -> Payload,
        dispatch: (Payload) -> Response,
        validateResponse: (Response) -> Result,
    ): Result {
        authorize()
        val payload = validatePayload()
        val response = dispatch(payload)
        return validateResponse(response)
    }
}

