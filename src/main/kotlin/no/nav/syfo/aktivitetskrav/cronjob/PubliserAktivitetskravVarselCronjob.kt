package no.nav.syfo.aktivitetskrav.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.aktivitetskrav.database.AktivitetskravVarselRepository
import no.nav.syfo.aktivitetskrav.kafka.*
import no.nav.syfo.application.cronjob.Cronjob
import no.nav.syfo.application.cronjob.CronjobResult
import org.slf4j.LoggerFactory

class PubliserAktivitetskravVarselCronjob(
    private val aktivitetskravVarselRepository: AktivitetskravVarselRepository,
    private val arbeidstakervarselProducer: ArbeidstakervarselProducer,
) : Cronjob {
    override val initialDelayMinutes: Long = 7
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        val result = runJob()
        log.info(
            "Completed publisering of aktivitetskrav-varsel with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
    }

    fun runJob(): CronjobResult {
        val result = CronjobResult()
        aktivitetskravVarselRepository.getIkkePubliserte().forEach { (personIdent, pAktivitetskravVarsel) ->
            try {
                arbeidstakervarselProducer.sendArbeidstakervarsel(
                    varselHendelse = ArbeidstakerHendelse(
                        type = HendelseType.SM_FORHANDSVARSEL_STANS,
                        arbeidstakerFnr = personIdent.value,
                        data = VarselData(
                            journalpost = VarselDataJournalpost(
                                uuid = pAktivitetskravVarsel.uuid.toString(),
                                id = pAktivitetskravVarsel.journalpostId.toString(),
                            ),
                        ),
                        orgnummer = null,
                    )
                )
                aktivitetskravVarselRepository.setPublished(pAktivitetskravVarsel.toAktivitetkravVarsel())
                result.updated++
            } catch (e: Exception) {
                log.error("Exception caught while attempting publisering of aktivitetskrav-varsel", e)
                result.failed++
            }
        }
        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(PubliserAktivitetskravVarselCronjob::class.java)
    }
}
