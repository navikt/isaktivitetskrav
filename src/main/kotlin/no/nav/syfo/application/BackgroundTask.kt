package no.nav.syfo.application

import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

fun launchBackgroundTask(
    applicationState: ApplicationState,
    action: suspend CoroutineScope.() -> Unit,
): Job = GlobalScope.launch(Dispatchers.Unbounded) {
    try {
        action()
    } catch (ex: Exception) {
        log.error("Exception received while launching background task. Terminating application.", ex)
    } finally {
        applicationState.alive = false
        applicationState.ready = false
    }
}

/*
Use Dispatchers.Unbounded to allow unlimited number of coroutines to be dispatched. Without this
only a few will be allowed simultaneously (depending on the number of available cores) which may result
in cronjobs or Kafka-consumers not starting as intended.
*/
val Dispatchers.Unbounded get() = UnboundedDispatcher.unboundedDispatcher

class UnboundedDispatcher private constructor() : CoroutineDispatcher() {
    companion object {
        val unboundedDispatcher = UnboundedDispatcher()
    }

    private val threadPool = Executors.newCachedThreadPool()
    private val dispatcher = threadPool.asCoroutineDispatcher()
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatcher.dispatch(context, block)
    }
}
