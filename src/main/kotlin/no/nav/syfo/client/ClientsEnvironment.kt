package no.nav.syfo.client

data class ClientsEnvironment(
    val syfotilgangskontroll: ClientEnvironment,
    val pdl: ClientEnvironment,
    val dokarkiv: ClientEnvironment,
)

data class ClientEnvironment(
    val baseUrl: String,
    val clientId: String,
)
