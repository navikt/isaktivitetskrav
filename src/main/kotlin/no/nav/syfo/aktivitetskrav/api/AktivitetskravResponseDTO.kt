package no.nav.syfo.aktivitetskrav.api

import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.domain.VurderingArsak
import java.time.LocalDate
import java.time.LocalDateTime

data class AktivitetskravResponseDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val status: AktivitetskravStatus,
    val inFinalState: Boolean,
    val stoppunktAt: LocalDate,
    val vurderinger: List<AktivitetskravVurderingResponseDTO>,
) {
    companion object {
        fun from(aktivitetskrav: Aktivitetskrav, vurderinger: List<AktivitetskravVurderingResponseDTO>) =
            AktivitetskravResponseDTO(
                uuid = aktivitetskrav.uuid.toString(),
                createdAt = aktivitetskrav.createdAt.toLocalDateTime(),
                status = aktivitetskrav.status,
                inFinalState = aktivitetskrav.status.isFinal,
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
    val arsaker: List<VurderingArsak>,
    val frist: LocalDate?,
    val varsel: VarselResponseDTO?
)

data class VarselResponseDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val svarfrist: LocalDate,
    val document: List<DocumentComponentDTO>,
)
