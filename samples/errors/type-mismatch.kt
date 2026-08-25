import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.JvmScriptContext

class Main : AutoJsJvmEntry {
    private val message: String = 42 // 故意把 Int 赋给 String
    override fun run(context: JvmScriptContext): Any = message
}
