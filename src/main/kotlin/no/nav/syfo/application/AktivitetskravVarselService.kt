package no.nav.syfo.application

import no.nav.syfo.api.dto.ForhandsvarselDTO
import no.nav.syfo.api.exception.ConflictException
import no.nav.syfo.domain.Aktivitetskrav
import no.nav.syfo.domain.AktivitetskravStatus
import no.nav.syfo.domain.AktivitetskravVarsel
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.VarselType
import no.nav.syfo.domain.isInFinalState
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.infrastructure.kafka.AktivitetskravVarselProducer
import no.nav.syfo.infrastructure.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.infrastructure.kafka.model.AktivitetskravVarselRecord
import java.util.*

class AktivitetskravVarselService(
    private val aktivitetskravVarselRepository: AktivitetskravVarselRepository,
    private val aktivitetskravVurderingProducer: AktivitetskravVurderingProducer,
    private val aktivitetskravVarselProducer: AktivitetskravVarselProducer,
    private val varselPdfService: VarselPdfService,
) {
    fun getIkkeJournalforte(): List<Triple<PersonIdent, AktivitetskravVarsel, ByteArray>> {
        return aktivitetskravVarselRepository.getIkkeJournalforte()
            .map { Triple(it.first, it.second.toAktivitetkravVarsel(), it.third) }
    }

    fun getIkkePubliserte(): List<AktivitetskravVarselRecord> {
        return aktivitetskravVarselRepository.getIkkePubliserte()
            .map { it.first.toKafkaAktivitetskravVarsel(it.second) }
    }

    fun publiser(varsel: AktivitetskravVarselRecord) {
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
            document = forhandsvarselDTO.document,
            frist = forhandsvarselDTO.frist,
        )
        val pdf = varselPdfService.createVarselPdf(
            varsel = forhandsvarsel,
            personIdent = personIdent,
            callId = callId,
        )
        val vurdering = forhandsvarselDTO.toAktivitetskravVurdering(veilederIdent)
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
}
