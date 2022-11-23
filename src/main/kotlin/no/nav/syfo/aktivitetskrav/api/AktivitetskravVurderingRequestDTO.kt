package no.nav.syfo.aktivitetskrav.api

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurderingStatus

data class AktivitetskravVurderingRequestDTO(
    val status: AktivitetskravVurderingStatus,
    val beskrivelse: String?,
)
