package no.nav.syfo.aktivitetskrav.api

import no.nav.syfo.aktivitetskrav.domain.*
import java.time.LocalDate

data class AktivitetskravVurderingRequestDTO(
    val status: AktivitetskravStatus,
    val beskrivelse: String?,
    val arsaker: List<VurderingArsak>,
    val frist: LocalDate? = null,
)

fun AktivitetskravVurderingRequestDTO.toAktivitetskravVurdering(
    createdByIdent: String,
) = AktivitetskravVurdering.create(
    status = this.status,
    createdBy = createdByIdent,
    beskrivelse = this.beskrivelse,
    arsaker = this.arsaker,
    frist = this.frist,
)
