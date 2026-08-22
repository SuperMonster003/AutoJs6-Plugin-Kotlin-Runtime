import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;
import org.autojs.plugin.jvmsource.api.JvmScriptContext;

public final class Main implements AutoJsJvmEntry {
    static {
        System.setProperty("autojs.jvm.source.test.main.initialized", "true");
    }

    public Main() {
    }

    @Override
    public Object run(JvmScriptContext context) {
        return Boolean.TRUE;
    }
}
