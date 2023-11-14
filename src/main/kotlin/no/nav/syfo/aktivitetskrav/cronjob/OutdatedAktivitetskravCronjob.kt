package no.nav.syfo.aktivitetskrav.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.application.cronjob.Cronjob
import no.nav.syfo.application.cronjob.CronjobResult
import org.slf4j.LoggerFactory
import java.time.LocalDate

class OutdatedAktivitetskravCronjob(
    private val outdatedCutoff: LocalDate,
    private val aktivitetskravService: AktivitetskravService
) : Cronjob {

    override val initialDelayMinutes: Long = 4
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        runJob()
    }

    internal fun runJob(): CronjobResult {
        val result = CronjobResult()
        val outdatedAktivitetskrav = aktivitetskravService.getOutdatedAktivitetskrav(
            outdatedCutoff = outdatedCutoff
        )

        outdatedAktivitetskrav.forEach {
            try {
                aktivitetskravService.lukkAktivitetskrav(aktivitetskrav = it)
                result.updated++
            } catch (e: Exception) {
                result.failed++
                log.error("Got exception when creating aktivitetskrav LUKKET", e)
            }
        }

        log.info(
            "Completed aktivitetskrav-outdated job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(OutdatedAktivitetskravCronjob::class.java)
    }
}
