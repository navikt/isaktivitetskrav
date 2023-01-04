package no.nav.syfo.client.azuread

import java.time.LocalDateTime

data class AzureAdTokenResponse(
    val accessToken: String,
    val expiresIn: Long,
    val tokenType: String,
)

fun AzureAdTokenResponse.toAzureAdToken(): AzureAdToken {
    val expiresOn = LocalDateTime.now().plusSeconds(this.expiresIn)
    return AzureAdToken(
        accessToken = this.accessToken,
        expires = expiresOn,
    )
}
