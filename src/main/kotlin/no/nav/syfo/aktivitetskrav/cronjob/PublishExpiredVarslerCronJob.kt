package no.nav.syfo.aktivitetskrav.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.aktivitetskrav.AktivitetskravVarselService
import no.nav.syfo.application.cronjob.Cronjob
import no.nav.syfo.application.cronjob.CronjobResult
import org.slf4j.LoggerFactory

class PublishExpiredVarslerCronJob(
    private val aktivitetskravVarselService: AktivitetskravVarselService,
    override val intervalDelayMinutes: Long
) : Cronjob {
    override val initialDelayMinutes: Long = 2

    override suspend fun run() {
        val result = runJob()
        log.info(
            "Completed publishing of aktivitetskrav-expired-varsel with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    suspend fun runJob(): CronjobResult {
        val result = CronjobResult()

        val expiredVarsler = aktivitetskravVarselService.getExpiredVarsler()
        expiredVarsler.forEach { varsel ->
            try {
                aktivitetskravVarselService.publishExpiredVarsel(varsel)
                result.updated++
            } catch (e: Exception) {
                log.error(
                    "Exception caught while attempting publishing of expired varsler",
                    e
                )
                result.failed++
            }
        }
        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(PublishExpiredVarslerCronJob::class.java)
    }
}
