package no.nav.syfo.api.dto

import no.nav.syfo.domain.Aktivitetskrav
import no.nav.syfo.domain.AktivitetskravStatus
import no.nav.syfo.domain.AktivitetskravVarsel
import no.nav.syfo.domain.AktivitetskravVurdering
import no.nav.syfo.domain.isInFinalState
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class AktivitetskravResponseDTO(
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val status: AktivitetskravStatus,
    val inFinalState: Boolean,
    val stoppunktAt: LocalDate,
    val vurderinger: List<AktivitetskravVurderingResponseDTO>,
) {
    companion object {

        fun from(aktivitetskrav: Aktivitetskrav) =
            AktivitetskravResponseDTO(
                uuid = aktivitetskrav.uuid,
                createdAt = aktivitetskrav.createdAt.toLocalDateTime(),
                status = aktivitetskrav.status,
                inFinalState = aktivitetskrav.isInFinalState(),
                stoppunktAt = aktivitetskrav.stoppunktAt,
                vurderinger = aktivitetskrav.vurderinger.map { AktivitetskravVurderingResponseDTO.from(it, it.varsel()) }
            )

        fun from(aktivitetskrav: Aktivitetskrav, vurderinger: List<AktivitetskravVurderingResponseDTO>) =
            AktivitetskravResponseDTO(
                uuid = aktivitetskrav.uuid,
                createdAt = aktivitetskrav.createdAt.toLocalDateTime(),
                status = aktivitetskrav.status,
                inFinalState = aktivitetskrav.isInFinalState(),
                stoppunktAt = aktivitetskrav.stoppunktAt,
                vurderinger = vurderinger
            )
    }
}

data class AktivitetskravVurderingResponseDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val createdBy: String,
    val status: AktivitetskravStatus,
    val beskrivelse: String?,
    val arsaker: List<Arsak>,
    val frist: LocalDate?,
    val varsel: VarselResponseDTO?,
) {
    companion object {
        fun from(
            aktivitetskravVurdering: AktivitetskravVurdering,
            varsel: AktivitetskravVarsel?,
        ): AktivitetskravVurderingResponseDTO =
            AktivitetskravVurderingResponseDTO(
                uuid = aktivitetskravVurdering.uuid.toString(),
                createdAt = aktivitetskravVurdering.createdAt.toLocalDateTime(),
                createdBy = aktivitetskravVurdering.createdBy,
                status = aktivitetskravVurdering.status,
                beskrivelse = aktivitetskravVurdering.beskrivelse,
                arsaker = aktivitetskravVurdering.arsaker().map { Arsak.valueOf(it) },
                frist = aktivitetskravVurdering.frist(),
                varsel = varsel?.toVarselResponseDTO()
            )
    }
}

data class VarselResponseDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val svarfrist: LocalDate?,
    val document: List<DocumentComponentDTO>,
)
