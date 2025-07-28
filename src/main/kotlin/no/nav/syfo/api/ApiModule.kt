package no.nav.syfo.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import no.nav.syfo.api.auth.JwtIssuer
import no.nav.syfo.api.auth.JwtIssuerType
import no.nav.syfo.api.auth.installJwtAuthentication
import no.nav.syfo.api.endpoints.registerAktivitetskravApi
import no.nav.syfo.api.endpoints.registerPodApi
import no.nav.syfo.api.metric.registerMetricApi
import no.nav.syfo.application.AktivitetskravService
import no.nav.syfo.application.AktivitetskravVarselService
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.infrastructure.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.client.wellknown.WellKnown
import no.nav.syfo.infrastructure.database.DatabaseInterface

fun Application.apiModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
    wellKnownInternalAzureAD: WellKnown,
    aktivitetskravService: AktivitetskravService,
    aktivitetskravVarselService: AktivitetskravVarselService,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
) {
    installMetrics()
    installCallId()
    installContentNegotiation()
    installStatusPages()
    installJwtAuthentication(
        jwtIssuerList = listOf(
            JwtIssuer(
                acceptedAudienceList = listOf(environment.azure.appClientId),
                jwtIssuerType = JwtIssuerType.INTERNAL_AZUREAD,
                wellKnown = wellKnownInternalAzureAD,
            ),
        )
    )

    routing {
        registerPodApi(
            applicationState = applicationState,
            database = database
        )
        registerMetricApi()
        authenticate(JwtIssuerType.INTERNAL_AZUREAD.name) {
            registerAktivitetskravApi(
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                aktivitetskravService = aktivitetskravService,
                aktivitetskravVarselService = aktivitetskravVarselService,
            )
        }
    }
}
