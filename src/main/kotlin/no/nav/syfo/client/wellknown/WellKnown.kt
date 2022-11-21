package no.nav.syfo.client.wellknown

data class WellKnown(
    val issuer: String,
    val jwksUri: String,
)
