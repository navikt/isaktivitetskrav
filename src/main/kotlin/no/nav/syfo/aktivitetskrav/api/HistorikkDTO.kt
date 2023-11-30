package no.nav.syfo.aktivitetskrav.api

import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import java.time.LocalDateTime

data class HistorikkDTO(
    val tidspunkt: LocalDateTime,
    val status: AktivitetskravStatus,
    val vurdertAv: String?,
)

internal fun createHistorikkDTOs(aktivitetskrav: Aktivitetskrav): List<HistorikkDTO> {
    val historikk = mutableListOf<HistorikkDTO>()
    historikk.add(
        HistorikkDTO(
            tidspunkt = aktivitetskrav.createdAt.toLocalDateTime(),
            status = if (aktivitetskrav.createdAt.toLocalDate() == aktivitetskrav.stoppunktAt) AktivitetskravStatus.NY_VURDERING else AktivitetskravStatus.NY,
            vurdertAv = null,
        )
    )
    aktivitetskrav.vurderinger.forEach {
        historikk.add(
            HistorikkDTO(
                tidspunkt = it.createdAt.toLocalDateTime(),
                status = it.status,
                vurdertAv = it.createdBy,
            )
        )
    }
    if (aktivitetskrav.status == AktivitetskravStatus.LUKKET) {
        historikk.add(
            HistorikkDTO(
                tidspunkt = aktivitetskrav.updatedAt.toLocalDateTime(),
                status = AktivitetskravStatus.LUKKET,
                vurdertAv = null,
            )
        )
    }
    return historikk
}
