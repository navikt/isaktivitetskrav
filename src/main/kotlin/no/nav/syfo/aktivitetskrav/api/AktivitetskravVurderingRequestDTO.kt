package no.nav.syfo.aktivitetskrav.api

import no.nav.syfo.aktivitetskrav.domain.*

data class AktivitetskravVurderingRequestDTO(
    val status: AktivitetskravStatus,
    val beskrivelse: String?,
    val arsaker: List<VurderingArsak>,
)

fun AktivitetskravVurderingRequestDTO.toAktivitetskravVurdering(
    createdByIdent: String,
) = AktivitetskravVurdering.create(
    status = this.status,
    createdBy = createdByIdent,
    beskrivelse = this.beskrivelse,
    arsaker = this.arsaker,
)
