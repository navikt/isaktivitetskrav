package no.nav.syfo.aktivitetskrav.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.application.cronjob.Cronjob
import no.nav.syfo.application.cronjob.CronjobResult
import no.nav.syfo.application.database.DatabaseInterface
import org.slf4j.LoggerFactory
import java.util.*

class AktivitetskravNyCronjob(
    private val database: DatabaseInterface,
    private val aktivitetskravService: AktivitetskravService,
) : Cronjob {
    override val initialDelayMinutes: Long = 10
    override val intervalDelayMinutes: Long = 240
    private val uuids = getUuids("uuids_ny.json")

    override suspend fun run() {
        val result = runJob(uuids)
        log.info(
            "Completed aktivitetskrav ny processing job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    fun runJob(aktivitetskravUuids: List<UUID>): CronjobResult {
        val result = CronjobResult()

        val aktivitetskravList =
            aktivitetskravUuids.mapNotNull { aktivitetskravService.getAktivitetskrav(it) }
                .filter { it.isAutomatiskOppfylt() }
        try {
            database.connection.use { connection ->
                aktivitetskravList.forEach {
                    val updatedAktivitetskrav = it.copy(
                        status = AktivitetskravStatus.NY
                    )
                    aktivitetskravService.updateAktivitetskrav(
                        connection = connection,
                        updatedAktivitetskrav = updatedAktivitetskrav,
                    )
                    result.updated++
                }
                connection.commit()
            }
        } catch (e: Exception) {
            log.error("Caught exception in aktivitetskrav ny job")
            result.failed++
        }

        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(AktivitetskravNyCronjob::class.java)
    }
}
