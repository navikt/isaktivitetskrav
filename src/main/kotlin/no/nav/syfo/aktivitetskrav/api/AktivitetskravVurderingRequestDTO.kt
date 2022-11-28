package no.nav.syfo.aktivitetskrav.api

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus

data class AktivitetskravVurderingRequestDTO(
    val status: AktivitetskravStatus,
    val beskrivelse: String?,
)
