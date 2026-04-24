package no.nav.syfo.client.veiledertilgang

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * Unit test for VeilederTilgangControllClient. This is not full API integration testing
 * like the API tests. azureAdClient and httpClient dependencies are mocked here.
 */
class VeilederTilgangskontrollClientTest {
    private val token = "token"
    private val oboToken = "obo-token"
    private val callId = "call-id"
    private val personident = PersonIdent("12345678910")
    private val clientEnvironment = testEnvironment().clients.istilgangskontroll
    private val azureAdClient = mockk<AzureAdClient>()

    @BeforeEach
    fun setup() {
        coEvery {
            azureAdClient.getOnBehalfOfToken(any(), any())
        } returns AzureAdToken(
            accessToken = oboToken,
            expires = LocalDateTime.now().plusHours(1),
        )
    }

    @AfterEach
    fun teardown() {
        clearAllMocks()
    }

    @Test
    fun `hasAccess returns true when tilgang is approved and sends expected headers to istilgangskontroll`() {
        lateinit var authorizationHeader: String
        lateinit var personidentHeader: String
        lateinit var callIdHeader: String

        // In order to intercept the headers that istilgangskontroll would be called with, this test
        // is not using the createClient() helper used by the other tests, and is instead setting up
        // httpClient and VeilederTilgangskontrollClient manually.
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
            assertTrue(client.hasAccess(callId, personident, token))
        }

        assertEquals(bearerHeader(oboToken), authorizationHeader)
        assertEquals(personident.value, personidentHeader)
        assertEquals(callId, callIdHeader)
    }

    @Test
    fun `hasAccess and hasWriteAccess returns true when tilgang to person is approved and user has fullTilgang`() {
        val client = createMockClientForResponse(Tilgang(erGodkjent = true, fullTilgang = true))

        runBlocking {
            assertTrue(client.hasAccess(callId, personident, token))
            assertTrue(client.hasWriteAccess(callId, personident, token))
        }
    }

    @Test
    fun `hasAccess returns true and hasWriteAccess returns false when tilgang to person is approved but user does not have fullTilgang`() {
        val client = createMockClientForResponse(Tilgang(erGodkjent = true, fullTilgang = false))

        runBlocking {
            assertTrue(client.hasAccess(callId, personident, token))
            assertFalse(client.hasWriteAccess(callId, personident, token))
        }
    }

    @Test
    fun `hasAccess and hasWriteAccess returns false when tilgang is not approved`() {
        val client = createMockClientForResponse(Tilgang(erGodkjent = false, fullTilgang = true))

        runBlocking {
            assertFalse(client.hasAccess(callId, personident, token))
            assertFalse(client.hasWriteAccess(callId, personident, token))
        }
    }

    @Test
    fun `hasAccess and hasWriteAccess returns false on unexpected response`() {
        val client = createMockClientForResponse(status = HttpStatusCode.InternalServerError)

        runBlocking {
            assertFalse(client.hasAccess(callId, personident, token))
            assertFalse(client.hasWriteAccess(callId, personident, token))
        }
    }

    @Test
    fun `hasAccess and hasWriteAccess throws when obo token request fails`() {
        coEvery {
            azureAdClient.getOnBehalfOfToken(any(), any())
        } returns null

        val client = createMockClientForResponse()

        assertThrows(RuntimeException::class.java) {
            runBlocking {
                client.hasAccess(callId, personident, token)
            }
        }
        assertThrows(RuntimeException::class.java) {
            runBlocking {
                client.hasWriteAccess(callId, personident, token)
            }
        }
    }

    /**
     * Create mock veilederTilgangkontrollClient for a specific response DTO from istilgangskontroll.
     */
    private fun createMockClientForResponse(
        tilgang: Tilgang = Tilgang(erGodkjent = true),
        status: HttpStatusCode = HttpStatusCode.OK,
    ): VeilederTilgangskontrollClient {
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
}
