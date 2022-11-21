package no.nav.syfo.client.azuread

data class AzureAdTokenResponse(
    val access_token: String,
    val expires_in: Long,
    val token_type: String,
)
