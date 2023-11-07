package no.nav.syfo.aktivitetskrav.api

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.domain.VurderingArsak
import java.time.LocalDate
import java.time.LocalDateTime

data class AktivitetskravResponseDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val status: AktivitetskravStatus,
    val stoppunktAt: LocalDate,
    val vurderinger: List<AktivitetskravVurderingResponseDTO>,
)

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
