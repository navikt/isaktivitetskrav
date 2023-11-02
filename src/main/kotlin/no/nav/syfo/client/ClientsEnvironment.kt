package no.nav.syfo.client

data class ClientsEnvironment(
    val istilgangskontroll: ClientEnvironment,
    val pdl: ClientEnvironment,
    val isaktivitetskravpdfgen: OpenClientEnvironment,
    val dokarkiv: ClientEnvironment,
    val krr: ClientEnvironment,
)

data class ClientEnvironment(
    val baseUrl: String,
    val clientId: String,
)

data class OpenClientEnvironment(
    val baseUrl: String,
)
