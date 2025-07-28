package no.nav.syfo.api.dto

data class GetAktivitetskravForPersonsResponseDTO(
    val aktivitetskravvurderinger: Map<String, AktivitetskravResponseDTO>,
)
