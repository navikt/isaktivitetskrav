package no.nav.syfo.infrastructure.pdl.domain

data class PdlHentPersonRequest(
    val query: String,
    val variables: PdlHentPersonRequestVariables
)

data class PdlHentPersonRequestVariables(
    val ident: String,
    val navnHistorikk: Boolean = false
)
