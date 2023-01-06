package no.nav.syfo.testhelper

import io.ktor.server.application.*
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.client.azuread.AzureAdClient

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    aktivitetskravVurderingProducer: AktivitetskravVurderingProducer,
) {
    val azureAdClient = AzureAdClient(
        azureEnvironment = externalMockEnvironment.environment.azure,
    )
    val aktivitetskravService = AktivitetskravService(
        aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
        database = externalMockEnvironment.database,
    )
    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        azureAdClient = azureAdClient,
        aktivitetskravService = aktivitetskravService,
    )
}
