package samples.m5

import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.JvmScriptContext

class Main : AutoJsJvmEntry {
    override fun run(context: JvmScriptContext): Any {
        val startedAt = System.currentTimeMillis()
        context.console().log("M6_CAPABILITIES_START")
        val appLaunchSucceeded = context.app().launch("org.autojs.autojs6")
        context.console().log("M6 Kotlin log: 你好; appLaunch=$appLaunchSucceeded")
        context.toast("M6 Kotlin toast")
        context.sleep(500L)
        context.console().error(
            "M6_CAPABILITIES_STDERR after ${System.currentTimeMillis() - startedAt} ms",
        )
        context.console().log("M6_CAPABILITIES_OK appLaunch=$appLaunchSucceeded")
        return appLaunchSucceeded
    }
}
