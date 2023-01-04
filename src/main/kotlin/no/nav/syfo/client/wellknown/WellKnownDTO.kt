package no.nav.syfo.client.wellknown

data class WellKnownDTO(
    val authorizationEndpoint: String,
    val issuer: String,
    val jwksUri: String,
    val tokenEndpoint: String,
)

fun WellKnownDTO.toWellKnown() = WellKnown(
    issuer = this.issuer,
    jwksUri = this.jwksUri,
)
