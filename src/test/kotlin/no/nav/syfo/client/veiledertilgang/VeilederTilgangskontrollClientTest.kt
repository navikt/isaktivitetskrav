package no.nav.syfo.client.veiledertilgang

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.azuread.AzureAdToken
import no.nav.syfo.infrastructure.client.commonConfig
import no.nav.syfo.infrastructure.client.veiledertilgang.Tilgang
import no.nav.syfo.infrastructure.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.testhelper.mock.respond
import no.nav.syfo.testhelper.testEnvironment
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class VeilederTilgangskontrollClientTest {
    private val token = "token"
    private val oboToken = "obo-token"
    private val callId = "call-id"
    private val personIdent = PersonIdent("12345678910")
    private val clientEnvironment = testEnvironment().clients.istilgangskontroll

    @Test
    fun `hasAccess returns true when tilgang is approved and sends expected headers`() {
        val azureAdClient = mockAzureAdClientWithToken()
        lateinit var authorizationHeader: String
        lateinit var personidentHeader: String
        lateinit var callIdHeader: String

        // In order to inspect the headers the httpClient is called with, this test is not using the
        // createClient() helper used by the other tests, and is instead setting up httpClient and
        // VeilederTilgangskontrollClient manually.
        val httpClient = HttpClient(MockEngine) {
            commonConfig()
            engine {
                addHandler { request ->
                    authorizationHeader = request.headers[HttpHeaders.Authorization].orEmpty()
                    personidentHeader = request.headers[NAV_PERSONIDENT_HEADER].orEmpty()
                    callIdHeader = request.headers[NAV_CALL_ID_HEADER].orEmpty()
                    respond(Tilgang(erGodkjent = true))
                }
            }
        }

        val client = VeilederTilgangskontrollClient(
            azureAdClient = azureAdClient,
            clientEnvironment = clientEnvironment,
            httpClient = httpClient,
        )

        runBlocking {
            assertTrue(client.hasAccess(callId, personIdent, token))
        }

        assertEquals(bearerHeader(oboToken), authorizationHeader)
        assertEquals(personIdent.value, personidentHeader)
        assertEquals(callId, callIdHeader)
    }

    @Test
    fun `hasAccess returns false when tilgang is not approved`() {
        val client = createClient(Tilgang(erGodkjent = false, fullTilgang = true))

        runBlocking {
            assertFalse(client.hasAccess(callId, personIdent, token))
        }
    }

    @Test
    fun `hasAccess returns false on forbidden response`() {
        val client = createClient(status = HttpStatusCode.Forbidden)

        runBlocking {
            assertFalse(client.hasAccess(callId, personIdent, token))
        }
    }

    @Test
    fun `hasWriteAccess returns true when tilgang to person is approved and user has fullTilgang`() {
        val client = createClient(Tilgang(erGodkjent = true, fullTilgang = true))

        runBlocking {
            assertTrue(client.hasWriteAccess(callId, personIdent, token))
        }
    }

    @Test
    fun `hasWriteAccess returns false when tilgang to person is approved but user does not have fullTilgang`() {
        val client = createClient(Tilgang(erGodkjent = true, fullTilgang = false))

        runBlocking {
            assertFalse(client.hasWriteAccess(callId, personIdent, token))
        }
    }

    @Test
    fun `hasWriteAccess returns false when tilgang to person is not approved`() {
        val client = createClient(Tilgang(erGodkjent = false, fullTilgang = true))

        runBlocking {
            assertFalse(client.hasWriteAccess(callId, personIdent, token))
        }
    }

    @Test
    fun `hasWriteAccess returns false on unexpected response`() {
        val client = createClient(status = HttpStatusCode.InternalServerError)

        runBlocking {
            assertFalse(client.hasWriteAccess(callId, personIdent, token))
        }
    }

    @Test
    fun `hasAccess throws when obo token request fails`() {
        val azureAdClient = mockk<AzureAdClient>()
        coEvery {
            azureAdClient.getOnBehalfOfToken(scopeClientId = clientEnvironment.clientId, token = token)
        } returns null

        val httpClient = HttpClient(MockEngine) {
            commonConfig()
            engine {
                addHandler { respond(Tilgang(erGodkjent = true)) }
            }
        }

        val client = VeilederTilgangskontrollClient(
            azureAdClient = azureAdClient,
            clientEnvironment = clientEnvironment,
            httpClient = httpClient,
        )

        assertThrows(RuntimeException::class.java) {
            runBlocking {
                client.hasAccess(callId, personIdent, token)
            }
        }
    }

    private fun createClient(
        tilgang: Tilgang = Tilgang(erGodkjent = true),
        status: HttpStatusCode = HttpStatusCode.OK,
    ): VeilederTilgangskontrollClient {
        val azureAdClient = mockAzureAdClientWithToken()
        val httpClient = HttpClient(MockEngine) {
            commonConfig()
            engine {
                addHandler {
                    if (status == HttpStatusCode.OK) {
                        respond(tilgang, status)
                    } else {
                        respondError(status)
                    }
                }
            }
        }

        return VeilederTilgangskontrollClient(
            azureAdClient = azureAdClient,
            clientEnvironment = clientEnvironment,
            httpClient = httpClient,
        )
    }

    private fun mockAzureAdClientWithToken(): AzureAdClient {
        val azureAdClient = mockk<AzureAdClient>()
        coEvery {
            azureAdClient.getOnBehalfOfToken(scopeClientId = clientEnvironment.clientId, token = token)
        } returns AzureAdToken(
            accessToken = oboToken,
            expires = LocalDateTime.now().plusHours(1),
        )
        return azureAdClient
    }
}
