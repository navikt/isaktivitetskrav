package no.nav.syfo.aktivitetskrav.api

import java.time.LocalDateTime

data class AktivitetskravVurderingResponseDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val status: String,
    val updatedBy: String?,
    val beskrivelse: String?,
)
