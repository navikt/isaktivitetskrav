package no.nav.syfo.client

data class ClientsEnvironment(
    val syfotilgangskontroll: ClientEnvironment,
)

data class ClientEnvironment(
    val baseUrl: String,
    val clientId: String,
)
