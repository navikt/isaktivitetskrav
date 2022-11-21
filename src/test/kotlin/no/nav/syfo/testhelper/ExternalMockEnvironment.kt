package no.nav.syfo.testhelper

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.client.wellknown.WellKnown
import no.nav.syfo.testhelper.mock.AzureADMock
import no.nav.syfo.testhelper.mock.SyfoTilgangskontrollMock
import java.nio.file.Paths

fun wellKnownInternalAzureAD(): WellKnown {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    return WellKnown(
        issuer = "https://sts.issuer.net/veileder/v2",
        jwksUri = uri.toString()
    )
}

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()

    private val azureAdMock = AzureADMock()
    private val syfoTilgangskontrollMock = SyfoTilgangskontrollMock()

    val externalMocks = hashMapOf(
        azureAdMock.name to azureAdMock.server,
        syfoTilgangskontrollMock.name to syfoTilgangskontrollMock.server,
    )

    val environment: Environment by lazy {
        testEnvironment(
            azureOpenIdTokenEndpoint = azureAdMock.url(),
            syfoTilgangskontrollUrl = syfoTilgangskontrollMock.url(),
        )
    }

    val wellKnownInternalAzureAD = wellKnownInternalAzureAD()

    companion object {
        val instance: ExternalMockEnvironment by lazy {
            ExternalMockEnvironment().also {
                it.startExternalMocks()
            }
        }
    }
}

fun ExternalMockEnvironment.startExternalMocks() {
    this.externalMocks.forEach { (_, externalMock) -> externalMock.start() }
}
