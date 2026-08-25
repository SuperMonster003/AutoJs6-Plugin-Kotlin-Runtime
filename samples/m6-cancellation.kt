package samples.m6

import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.JvmScriptContext

/** Device-smoke fixture: stop this request after M6_CANCELLATION_STARTED is streamed. */
class Main : AutoJsJvmEntry {
    override fun run(context: JvmScriptContext): Any {
        context.console().log("M6_CANCELLATION_STARTED")
        context.sleep(60_000L)
        context.console().error("M6_CANCELLATION_UNEXPECTED_COMPLETION")
        return false
    }
}
