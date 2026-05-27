package no.nav.syfo.infrastructure.client

import no.nav.syfo.common.util.ClientConfig
import no.nav.syfo.common.util.OpenClientConfig

data class ClientsConfig(
    val istilgangskontroll: ClientConfig,
    val pdl: ClientConfig,
    val ispdfgen: OpenClientConfig,
    val dokarkiv: ClientConfig,
)
