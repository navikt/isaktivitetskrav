package no.nav.syfo.testhelper

import io.ktor.server.application.*
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.application.api.apiModule

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    aktivitetskravVurderingProducer: AktivitetskravVurderingProducer,
) {
    val aktivitetskravService = AktivitetskravService(
        aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
        database = externalMockEnvironment.database,
    )
    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        aktivitetskravService = aktivitetskravService,
    )
}
