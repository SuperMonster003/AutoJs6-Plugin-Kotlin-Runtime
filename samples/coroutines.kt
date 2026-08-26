package samples.m9

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.JvmScriptContext

private const val WAIT_FOR_HOST_CANCELLATION = false

class Main : AutoJsJvmEntry {
    override fun run(context: JvmScriptContext): Any = runBlocking {
        context.console().log("M9_COROUTINES_START")

        // Set this sample constant to true, run it, then stop the AutoJs6 engine. Interrupting the
        // worker's runBlocking thread cancels this structured coroutine scope before hard-retire.
        while (WAIT_FOR_HOST_CANCELLATION && isActive) {
            context.cancellation().throwIfCancellationRequested()
            delay(100L)
        }

        val squares = (1..4).map { value ->
            async(Dispatchers.Default) {
                delay(25L)
                value * value
            }
        }.awaitAll()
        context.cancellation().throwIfCancellationRequested()
        val sum = squares.sum()
        context.console().log("M9_COROUTINES_OK sum=$sum")
        sum
    }
}
