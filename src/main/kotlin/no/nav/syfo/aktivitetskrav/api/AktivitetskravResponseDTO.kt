package no.nav.syfo.aktivitetskrav.api

import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.domain.VurderingArsak
import no.nav.syfo.aktivitetskrav.domain.isInFinalState
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
        fun from(aktivitetskrav: Aktivitetskrav, vurderinger: List<AktivitetskravVurderingResponseDTO> = emptyList()) =
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

data class HistorikkDTO(
    val tidspunkt: LocalDateTime,
    val status: AktivitetskravStatus,
    val vurdertAv: String?,
)
