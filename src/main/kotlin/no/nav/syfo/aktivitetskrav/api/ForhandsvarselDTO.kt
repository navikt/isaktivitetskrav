package no.nav.syfo.aktivitetskrav.api

data class ForhandsvarselDTO(
    val fritekst: String,
    val document: List<DocumentComponentDTO> = emptyList(),
)
