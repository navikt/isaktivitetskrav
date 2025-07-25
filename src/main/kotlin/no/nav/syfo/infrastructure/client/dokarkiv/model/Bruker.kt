package no.nav.syfo.infrastructure.client.dokarkiv.model

enum class BrukerIdType(
    val value: String,
) {
    PERSON_IDENT("FNR"),
}

data class Bruker private constructor(
    val id: String,
    val idType: String,
) {
    companion object {
        fun create(
            id: String,
            idType: BrukerIdType,
        ) = Bruker(
            id = id,
            idType = idType.value
        )
    }
}
