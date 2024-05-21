package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.api.ForhandsvarselDTO
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.application.exception.ConflictException
import no.nav.syfo.infrastructure.kafka.domain.ExpiredVarsel
import no.nav.syfo.infrastructure.kafka.domain.KafkaAktivitetskravVarsel
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.kafka.AktivitetskravVarselProducer
import no.nav.syfo.infrastructure.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.infrastructure.kafka.ExpiredVarselProducer
import java.util.*

class AktivitetskravVarselService(
    private val aktivitetskravVarselRepository: AktivitetskravVarselRepository,
    private val aktivitetskravVurderingProducer: AktivitetskravVurderingProducer,
    private val aktivitetskravVarselProducer: AktivitetskravVarselProducer,
    private val expiredVarselProducer: ExpiredVarselProducer,
    private val varselPdfService: VarselPdfService,
) {
    fun getIkkeJournalforte(): List<Triple<PersonIdent, AktivitetskravVarsel, ByteArray>> {
        return aktivitetskravVarselRepository.getIkkeJournalforte()
            .map { Triple(it.first, it.second.toAktivitetkravVarsel(), it.third) }
    }

    fun getIkkePubliserte(): List<KafkaAktivitetskravVarsel> {
        return aktivitetskravVarselRepository.getIkkePubliserte()
            .map { it.first.toKafkaAktivitetskravVarsel(it.second) }
    }

    fun publiser(varsel: KafkaAktivitetskravVarsel) {
        aktivitetskravVarselProducer.sendAktivitetskravVarsel(
            varsel = varsel,
        )
        aktivitetskravVarselRepository.setPublished(varsel)
    }

    fun setJournalfort(varsel: AktivitetskravVarsel, journalpostId: String) {
        aktivitetskravVarselRepository.updateJournalpostId(
            varsel = varsel,
            journalpostId = journalpostId,
        )
    }

    fun getVarsel(vurderingUuid: UUID): AktivitetskravVarsel? =
        aktivitetskravVarselRepository.getVarselForVurdering(vurderingUuid = vurderingUuid)
            ?.toAktivitetkravVarsel()

    suspend fun sendForhandsvarsel(
        aktivitetskrav: Aktivitetskrav,
        veilederIdent: String,
        personIdent: PersonIdent,
        forhandsvarselDTO: ForhandsvarselDTO,
        callId: String,
    ): AktivitetskravVarsel {
        if (aktivitetskrav.vurderinger.any { it.status == AktivitetskravStatus.FORHANDSVARSEL }) {
            throw ConflictException("Forhåndsvarsel allerede sendt")
        }
        if (aktivitetskrav.isInFinalState()) {
            throw ConflictException("Kan ikke sende forhåndsvarsel, aktivitetskravet har en avsluttende vurdering ${aktivitetskrav.status}")
        }

        val forhandsvarsel = AktivitetskravVarsel.create(
            type = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
            document = forhandsvarselDTO.document
        )
        val pdf = varselPdfService.createVarselPdf(
            varsel = forhandsvarsel,
            personIdent = personIdent,
            callId = callId,
        )
        val vurdering: AktivitetskravVurdering = forhandsvarselDTO.toAktivitetskravVurdering(veilederIdent)
        val updatedAktivitetskrav = aktivitetskrav.vurder(aktivitetskravVurdering = vurdering)

        val nyttForhandsvarsel = aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
            aktivitetskrav = updatedAktivitetskrav,
            newVurdering = vurdering,
            varsel = forhandsvarsel,
            pdf = pdf,
        )

        aktivitetskravVurderingProducer.sendAktivitetskravVurdering(aktivitetskrav = updatedAktivitetskrav)

        return nyttForhandsvarsel.toAktivitetkravVarsel()
    }

    suspend fun getExpiredVarsler(): List<ExpiredVarsel> =
        aktivitetskravVarselRepository.getExpiredVarsler().map { (personIdent, aktivitetskravUuid, varsel) ->
            varsel.toExpiredVarsel(personIdent, aktivitetskravUuid)
        }

    suspend fun publishExpiredVarsel(expiredVarselToBePublished: ExpiredVarsel) {
        expiredVarselProducer.publishExpiredVarsel(expiredVarselToBePublished)
        aktivitetskravVarselRepository.updateExpiredVarselPublishedAt(expiredVarselToBePublished)
    }
}
