package samples.m5

import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.JvmScriptContext

class Main : AutoJsJvmEntry {
    override fun run(context: JvmScriptContext): Any {
        val startedAt = System.currentTimeMillis()
        context.console().log("M5 Kotlin log: 你好")
        context.toast("M5 Kotlin toast")
        context.sleep(500L)
        context.console().error(
            "M5 Kotlin error after ${System.currentTimeMillis() - startedAt} ms",
        )
        return 5
    }
}
