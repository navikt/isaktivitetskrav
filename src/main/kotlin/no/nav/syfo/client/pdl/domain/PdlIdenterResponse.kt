package no.nav.syfo.client.pdl.domain

data class PdlIdenterResponse(
    val data: PdlHentIdenter?,
    val errors: List<PdlError>?
)

data class PdlHentIdenter(
    val hentIdenter: PdlIdenter?,
)

data class PdlIdenter(
    val identer: List<PdlIdent>,
) {
    val aktivIdent: String? = identer.firstOrNull {
        it.gruppe == IdentType.FOLKEREGISTERIDENT && !it.historisk
    }?.ident
}

data class PdlIdent(
    val ident: String,
    val historisk: Boolean,
    val gruppe: IdentType,
)

enum class IdentType {
    FOLKEREGISTERIDENT,
    AKTORID,
    NPID,
}
