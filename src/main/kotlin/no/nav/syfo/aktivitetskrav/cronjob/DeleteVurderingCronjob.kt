package no.nav.syfo.aktivitetskrav.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.database.getAktivitetskravVurdering
import no.nav.syfo.application.cronjob.Cronjob
import no.nav.syfo.application.cronjob.CronjobResult
import no.nav.syfo.application.database.DatabaseInterface
import org.slf4j.LoggerFactory
import java.util.UUID

class DeleteVurderingCronjob(
    private val database: DatabaseInterface,
    private val aktivitetskravService: AktivitetskravService,
) : Cronjob {

    override val initialDelayMinutes: Long = 3
    override val intervalDelayMinutes: Long = 240

    override suspend fun run() {
        val result = runJob(uuids.map { UUID.fromString(it) })
        log.info(
            "Completed delete vurdering job with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    fun runJob(vurderingUuids: List<UUID>): CronjobResult {
        val result = CronjobResult()
        val aktivitetskravVurderinger = vurderingUuids.mapNotNull { database.getAktivitetskravVurdering(it) }
        log.info("Cronjob for delete vurdering found ${aktivitetskravVurderinger.size} vurderinger")
        aktivitetskravVurderinger.forEach {
            try {
                database.connection.use { connection ->
                    aktivitetskravService.deleteVurdering(
                        connection = connection,
                        vurdering = it
                    )
                    connection.commit()
                    log.info("Vurdering with uuid ${it.uuid} deleted")
                }
                result.updated++
            } catch (e: Exception) {
                log.error("Caught exception in delete vurdering job")
                result.failed++
            }
        }

        return result
    }

    companion object {
        private val uuids = emptyList<String>()
        private val log = LoggerFactory.getLogger(DeleteVurderingCronjob::class.java)
    }
}
