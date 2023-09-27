package no.nav.syfo.aktivitetskrav.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.application.cronjob.Cronjob
import no.nav.syfo.application.cronjob.CronjobResult
import org.slf4j.LoggerFactory

class PublishExpiredVarslerCronJob(
    private val aktivtetskravService: AktivitetskravService
) : Cronjob {
    // What should we set here?
    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        val result = CronjobResult()
        try {
            val expiredVarslerPublished = aktivtetskravService.publishExpiredVarsler()
            result.updated += expiredVarslerPublished.size
        } catch (e: Exception) {
            log.error(
                "Exception caught while attempting publisering of expired varsler",
                e
            )
            result.failed++
        }
        log.info(
            "Completed publishing of expired varsel with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(PublishExpiredVarslerCronJob::class.java)
    }
}
