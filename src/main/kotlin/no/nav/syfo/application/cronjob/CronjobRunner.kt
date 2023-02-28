package no.nav.syfo.application.cronjob

import kotlinx.coroutines.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.leaderelection.LeaderPodClient
import org.slf4j.LoggerFactory
import java.time.Duration

class CronjobRunner(
    private val applicationState: ApplicationState,
    private val leaderPodClient: LeaderPodClient,
) {

    private val log = LoggerFactory.getLogger(CronjobRunner::class.java)

    suspend fun start(cronjob: Cronjob) = coroutineScope {
        val cronjobName = cronjob.javaClass.simpleName
        val (initialDelay, intervalDelay) = delays(cronjob)
        log.info(
            "Scheduling start of $cronjobName: {} ms, {} ms",
            StructuredArguments.keyValue("initialDelay", initialDelay),
            StructuredArguments.keyValue("intervalDelay", intervalDelay),
        )
        delay(initialDelay)

        while (applicationState.ready) {
            val job = launch {
                try {
                    if (leaderPodClient.isLeader()) {
                        cronjob.run()
                    } else {
                        log.info("Pod is not leader and will not perform cronjob")
                    }
                } catch (ex: Exception) {
                    log.error("Exception in $cronjobName. Job will run again after delay.", ex)
                }
            }
            delay(intervalDelay)
            if (job.isActive) {
                log.info("Waiting for job to finish")
                job.join()
            }
        }
        log.info("Ending $cronjobName due to failed liveness check ")
    }

    private fun delays(cronjob: Cronjob): Pair<Long, Long> {
        val initialDelay = Duration.ofMinutes(cronjob.initialDelayMinutes).toMillis()
        val intervalDelay = Duration.ofMinutes(cronjob.intervalDelayMinutes).toMillis()
        return Pair(initialDelay, intervalDelay)
    }
}
