package no.nav.syfo.testhelper

import io.ktor.server.application.*
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    aktivitetskravVurderingProducer: AktivitetskravVurderingProducer,
) {
    val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    val aktivitetskravService = AktivitetskravService(
        aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
        database = externalMockEnvironment.database,
        arenaCutoff = externalMockEnvironment.environment.arenaCutoff,
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdClient = azureAdClient,
        clientEnvironment = externalMockEnvironment.environment.clients.syfotilgangskontroll,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        aktivitetskravService = aktivitetskravService,
        veilederTilgangskontrollClient = veilederTilgangskontrollClient,
    )
}
