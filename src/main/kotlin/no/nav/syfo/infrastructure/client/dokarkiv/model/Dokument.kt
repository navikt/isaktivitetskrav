package no.nav.syfo.infrastructure.client.dokarkiv.model

enum class BrevkodeType(
    val value: String,
) {
    AKTIVITETSKRAV_FORHANDSVARSEL("OPPF_AKTIVITETSKRAV_FORHANDSVARSEL"),
    AKTIVITETSKRAV_VURDERING("OPPF_AKTIVITETSKRAV_VURDERING"),
    AKTIVITETSKRAV_STANS("OPPF_AKTIVITETSKRAV_STANS"),
}

data class Dokument private constructor(
    val brevkode: String,
    val dokumentKategori: String? = null,
    val dokumentvarianter: List<Dokumentvariant>,
    val tittel: String? = null,
) {
    companion object {
        fun create(
            brevkode: BrevkodeType,
            dokumentvarianter: List<Dokumentvariant>,
            tittel: String? = null,
        ) = Dokument(
            brevkode = brevkode.value,
            dokumentvarianter = dokumentvarianter,
            tittel = tittel,
        )
    }
}
