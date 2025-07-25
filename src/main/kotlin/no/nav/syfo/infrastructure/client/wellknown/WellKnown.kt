package no.nav.syfo.infrastructure.client.wellknown

data class WellKnown(
    val issuer: String,
    val jwksUri: String,
)
