package org.autojs.plugin.jvmsource.kotlin

import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase

internal class JavaProviderFailure(
    val code: JvmSourceErrorCode,
    val phase: JvmSourceFailurePhase,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
