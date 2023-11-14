package no.nav.syfo.identhendelse

import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.database.getAktivitetskrav
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.identhendelse.kafka.COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES
import no.nav.syfo.identhendelse.kafka.KafkaIdenthendelseDTO
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IdenthendelseService(
    private val aktivitetskravService: AktivitetskravService,
    private val pdlClient: PdlClient,
) {
    private val log: Logger = LoggerFactory.getLogger(IdenthendelseService::class.java)

    fun handle(identhendelse: KafkaIdenthendelseDTO) {
        if (identhendelse.folkeregisterIdenter.size > 1) {
            val activeIdent = identhendelse.getActivePersonident()
            if (activeIdent != null) {
                val inactiveIdenter = identhendelse.getInactivePersonidenter()
                val oldPersonIdentList =
                    inactiveIdenter.filter { aktivitetskravService.getAktivitetskrav(it).isNotEmpty() }

                if (oldPersonIdentList.isNotEmpty()) {
                    checkThatPdlIsUpdated(activeIdent)
                    val numberOfUpdatedIdenter =
                        aktivitetskravService.updateAktivitetskravPersonIdent(activeIdent, oldPersonIdentList)

                    log.info("Identhendelse: Updated $numberOfUpdatedIdenter aktivitetskrav based on Identhendelse from PDL")
                    COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES.increment(numberOfUpdatedIdenter.toDouble())
                }
            } else {
                log.warn("Identhendelse: Ignored - Mangler aktiv ident i PDL")
            }
        }
    }

    // Erfaringer fra andre team tilsier at vi burde dobbeltsjekke at ting har blitt oppdatert i PDL før vi gjør endringer
    private fun checkThatPdlIsUpdated(ident: PersonIdent) {
        runBlocking {
            val pdlIdenter =
                pdlClient.getPdlIdenter(ident)?.hentIdenter ?: throw RuntimeException("Fant ingen identer fra PDL")
            if (ident.value != pdlIdenter.aktivIdent && pdlIdenter.identhendelseIsNotHistorisk(ident.value)) {
                throw IllegalStateException("Ny ident er ikke aktiv ident i PDL")
            }
        }
    }
}
