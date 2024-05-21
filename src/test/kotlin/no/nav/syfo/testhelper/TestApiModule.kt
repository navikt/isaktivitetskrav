package no.nav.syfo.testhelper

import io.ktor.server.application.*
import io.mockk.mockk
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.AktivitetskravVarselService
import no.nav.syfo.aktivitetskrav.VarselPdfService
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.infrastructure.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.application.api.apiModule
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    aktivitetskravVurderingProducer: AktivitetskravVurderingProducer,
) {
    val database = externalMockEnvironment.database
    val varselPdfService = VarselPdfService(
        pdfGenClient = externalMockEnvironment.pdfgenClient,
        pdlClient = externalMockEnvironment.pdlClient,
    )
    val aktivitetskravService = AktivitetskravService(
        aktivitetskravRepository = AktivitetskravRepository(database),
        aktivitetskravVarselRepository = AktivitetskravVarselRepository(database),
        aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
        arenaCutoff = externalMockEnvironment.environment.arenaCutoff,
        varselPdfService = varselPdfService
    )
    val aktivitetskravVarselService = AktivitetskravVarselService(
        varselPdfService = varselPdfService,
        aktivitetskravVarselRepository = AktivitetskravVarselRepository(
            database = database
        ),

        aktivitetskravVarselProducer = mockk(),
        aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
        expiredVarselProducer = mockk(),
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdClient = externalMockEnvironment.azureAdClient,
        clientEnvironment = externalMockEnvironment.environment.clients.istilgangskontroll,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        database = database,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        aktivitetskravService = aktivitetskravService,
        aktivitetskravVarselService = aktivitetskravVarselService,
        veilederTilgangskontrollClient = veilederTilgangskontrollClient,
    )
}
