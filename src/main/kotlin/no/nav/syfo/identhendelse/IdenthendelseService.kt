package no.nav.syfo.identhendelse

import no.nav.syfo.aktivitetskrav.database.getAktivitetskrav
import no.nav.syfo.aktivitetskrav.database.updateAktivitetskravPersonIdent
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.identhendelse.kafka.COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES
import no.nav.syfo.identhendelse.kafka.KafkaIdenthendelseDTO
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IdenthendelseService(
    private val database: DatabaseInterface,
    private val pdlClient: PdlClient,
) {
    private val log: Logger = LoggerFactory.getLogger(IdenthendelseService::class.java)

    suspend fun handle(identhendelse: KafkaIdenthendelseDTO) {
        if (identhendelse.folkeregisterIdenter.size > 1) {
            val activeIdent = identhendelse.getActivePersonident()
            if (activeIdent != null) {
                val inactiveIdenter = identhendelse.getInactivePersonidenter()
                val aktivitetskravWithInactiveIdent = inactiveIdenter.flatMap { database.getAktivitetskrav(it) }

                if (aktivitetskravWithInactiveIdent.isNotEmpty()) {
                    checkThatPdlIsUpdated(activeIdent)
                    val numberOfUpdatedIdenter = database.updateAktivitetskravPersonIdent(activeIdent, inactiveIdenter)
                    log.info("Identhendelse: Updated $numberOfUpdatedIdenter aktivitetskrav based on Identhendelse from PDL")
                    COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES.increment(numberOfUpdatedIdenter.toDouble())
                }
            } else {
                log.warn("Identhendelse: Ignored - Mangler aktiv ident i PDL")
            }
        }
    }

    // Erfaringer fra andre team tilsier at vi burde dobbeltsjekke at ting har blitt oppdatert i PDL før vi gjør endringer
    private suspend fun checkThatPdlIsUpdated(ident: PersonIdent) {
        val pdlIdenter = pdlClient.getPdlIdenter(ident)?.hentIdenter
        if (ident.value != pdlIdenter?.aktivIdent || pdlIdenter.identer.any { it.ident == ident.value && it.historisk }) {
            throw IllegalStateException("Ny ident er ikke aktiv ident i PDL")
        }
    }
}
