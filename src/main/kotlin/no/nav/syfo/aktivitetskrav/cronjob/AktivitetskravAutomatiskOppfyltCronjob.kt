package no.nav.syfo.aktivitetskrav.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.domain.isNy
import no.nav.syfo.application.cronjob.Cronjob
import no.nav.syfo.application.cronjob.CronjobResult
import no.nav.syfo.application.database.DatabaseInterface
import org.slf4j.LoggerFactory
import java.util.UUID

class AktivitetskravAutomatiskOppfyltCronjob(
    private val database: DatabaseInterface,
    private val aktivitetskravService: AktivitetskravService,
) : Cronjob {
    override val initialDelayMinutes: Long = 5
    override val intervalDelayMinutes: Long = 240

    override suspend fun run() {
        val result = runJob(uuids.map { UUID.fromString(it) })
        log.info(
            "Completed aktivitetskrav automatisk oppfylt processing job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    fun runJob(aktivitetskravUuids: List<UUID>): CronjobResult {
        val result = CronjobResult()

        val aktivitetskravList =
            aktivitetskravUuids.mapNotNull { aktivitetskravService.getAktivitetskrav(it) }.filter { it.isNy() }
        try {
            database.connection.use { connection ->
                aktivitetskravList.forEach {
                    aktivitetskravService.oppfyllAutomatisk(
                        connection = connection,
                        aktivitetskrav = it
                    )
                    result.updated++
                }
                connection.commit()
            }
        } catch (e: Exception) {
            log.error("Caught exception in aktivitetskrav automatisk oppfylt job")
            result.failed++
        }

        return result
    }

    companion object {
        private val uuids = listOf<String>()
        private val log = LoggerFactory.getLogger(AktivitetskravAutomatiskOppfyltCronjob::class.java)
    }
}
