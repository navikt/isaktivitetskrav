package no.nav.syfo.infrastructure.client.pdl.model

data class PdlHentPersonRequest(
    val query: String,
    val variables: PdlHentPersonRequestVariables,
)

data class PdlHentPersonRequestVariables(
    val ident: String,
    val navnHistorikk: Boolean = false,
)
