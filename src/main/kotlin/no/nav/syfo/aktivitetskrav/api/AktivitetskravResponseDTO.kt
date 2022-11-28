package no.nav.syfo.aktivitetskrav.api

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import java.time.LocalDateTime

data class AktivitetskravResponseDTO(
    val uuid: String,
    val createdAt: LocalDateTime,
    val sistEndret: LocalDateTime,
    val status: AktivitetskravStatus,
    val updatedBy: String?,
    val beskrivelse: String?,
)
