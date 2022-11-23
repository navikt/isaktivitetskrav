package no.nav.syfo.aktivitetskrav.api

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurderingStatus
import java.time.LocalDateTime

data class AktivitetskravVurderingResponseDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val sistEndret: LocalDateTime,
    val status: AktivitetskravVurderingStatus,
    val updatedBy: String?,
    val beskrivelse: String?,
)
