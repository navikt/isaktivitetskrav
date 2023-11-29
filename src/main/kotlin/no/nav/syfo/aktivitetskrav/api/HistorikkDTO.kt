package no.nav.syfo.aktivitetskrav.api

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import java.time.LocalDateTime

data class HistorikkDTO(
    val tidspunkt: LocalDateTime,
    val status: AktivitetskravStatus,
    val vurdertAv: String?,
)
