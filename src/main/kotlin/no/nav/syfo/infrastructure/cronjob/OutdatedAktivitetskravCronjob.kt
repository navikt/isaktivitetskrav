package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.AktivitetskravService
import org.slf4j.LoggerFactory
import java.time.LocalDate

class OutdatedAktivitetskravCronjob(
    private val outdatedCutoffMonths: Int,
    private val aktivitetskravService: AktivitetskravService,
) : Cronjob {

    override val initialDelayMinutes: Long = 4
    override val intervalDelayMinutes: Long = 30 // Change to once every 24 hours after initial clean up

    override suspend fun run() {
        runJob()
    }

    internal fun runJob(): CronjobResult {
        val result = CronjobResult()

        val cutoff = LocalDate.now()
            .minusMonths(outdatedCutoffMonths.toLong())

        val outdatedAktivitetskrav = aktivitetskravService.getOutdatedAktivitetskrav(
            outdatedCutoff = cutoff
        )

        outdatedAktivitetskrav.forEach {
            try {
                aktivitetskravService.lukkAktivitetskrav(aktivitetskrav = it)
                result.updated++
                log.info("Closed aktivitetskrav for ${it.uuid}")
            } catch (e: Exception) {
                result.failed++
                log.error("Got exception when creating aktivitetskrav LUKKET for ${it.uuid}", e)
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
