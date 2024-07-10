package no.nav.syfo.aktivitetskrav.api

data class GetAktivitetskravForPersonsResponseDTO(
    val aktivitetskravvurderinger: Map<String, AktivitetskravResponseDTO>,
)
