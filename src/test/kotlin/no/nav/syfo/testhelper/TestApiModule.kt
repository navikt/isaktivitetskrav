package no.nav.syfo.testhelper

import io.ktor.server.application.*
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.database.AktivitetskravVarselRepository
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    aktivitetskravVurderingProducer: AktivitetskravVurderingProducer,
) {
    val aktivitetskravService = AktivitetskravService(
        aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
        database = externalMockEnvironment.database,
        arenaCutoff = externalMockEnvironment.environment.arenaCutoff,
        pdfGenClient = externalMockEnvironment.pdfgenClient,
        aktivitetskravVarselRepository = AktivitetskravVarselRepository(
            database = externalMockEnvironment.database
        ),
        pdlClient = externalMockEnvironment.pdlClient,
        krrClient = externalMockEnvironment.krrClient,
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdClient = externalMockEnvironment.azureAdClient,
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
