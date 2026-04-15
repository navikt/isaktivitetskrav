package no.nav.syfo.util

import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.RoutingRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.api.exception.ForbiddenAccessVeilederException
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.client.veiledertilgang.VeilederTilgangskontrollClient
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PipelineUtilTest {
    private val action = "read aktivitetskrav"
    private val callId = "123"
    private val personIdent = PersonIdent("12345678910")
    private val token = "token"

    private val veilederTilgangskontrollClient = mockk<VeilederTilgangskontrollClient>()

    @Test
    fun `calls hasAccess and executes block when read access is granted`() {
        val routingContext = routingContextWithHeaders(
            headers = Headers.build {
                append(NAV_CALL_ID_HEADER, callId)
                append(NAV_PERSONIDENT_HEADER, personIdent.value)
                append(HttpHeaders.Authorization, bearerHeader(token))
            }
        )
        var blockCalled = false

        coEvery {
            veilederTilgangskontrollClient.hasAccess(callId, personIdent, token)
        } returns true

        runBlocking {
            routingContext.checkVeilederTilgang(
                action = action,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                blockCalled = true
            }
        }

        assertTrue(blockCalled)
        coVerify(exactly = 1) {
            veilederTilgangskontrollClient.hasAccess(callId, personIdent, token)
        }
        coVerify(exactly = 0) {
            veilederTilgangskontrollClient.hasWriteAccess(any(), any(), any())
        }
    }

    @Test
    fun `calls hasWriteAccess and executes block when write access is granted`() {
        val routingContext = routingContextWithHeaders(
            headers = Headers.build {
                append(NAV_CALL_ID_HEADER, callId)
                append(NAV_PERSONIDENT_HEADER, personIdent.value)
                append(HttpHeaders.Authorization, bearerHeader(token))
            }
        )
        var blockCalled = false

        coEvery {
            veilederTilgangskontrollClient.hasWriteAccess(callId, personIdent, token)
        } returns true

        runBlocking {
            routingContext.checkVeilederTilgang(
                action = action,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                requiresWriteAccess = true,
            ) {
                blockCalled = true
            }
        }

        assertTrue(blockCalled)
        coVerify(exactly = 1) {
            veilederTilgangskontrollClient.hasWriteAccess(callId, personIdent, token)
        }
        coVerify(exactly = 0) {
            veilederTilgangskontrollClient.hasAccess(any(), any(), any())
        }
    }

    @Test
    fun `throws forbidden and does not execute block when read access is denied`() {
        val routingContext = routingContextWithHeaders(
            headers = Headers.build {
                append(NAV_CALL_ID_HEADER, callId)
                append(NAV_PERSONIDENT_HEADER, personIdent.value)
                append(HttpHeaders.Authorization, bearerHeader(token))
            }
        )
        var blockCalled = false

        coEvery {
            veilederTilgangskontrollClient.hasAccess(callId, personIdent, token)
        } returns false

        assertThrows(ForbiddenAccessVeilederException::class.java) {
            runBlocking {
                routingContext.checkVeilederTilgang(
                    action = action,
                    veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                ) {
                    blockCalled = true
                }
            }
        }

        assertFalse(blockCalled)
        coVerify(exactly = 1) {
            veilederTilgangskontrollClient.hasAccess(callId, personIdent, token)
        }
    }

    @Test
    fun `throws illegal argument when authorization header is missing`() {
        val routingContext = routingContextWithHeaders(
            headers = Headers.build {
                append(NAV_CALL_ID_HEADER, callId)
                append(NAV_PERSONIDENT_HEADER, personIdent.value)
            }
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                routingContext.checkVeilederTilgang(
                    action = action,
                    veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                ) {}
            }
        }

        coVerify(exactly = 0) {
            veilederTilgangskontrollClient.hasAccess(any(), any(), any())
        }
        coVerify(exactly = 0) {
            veilederTilgangskontrollClient.hasWriteAccess(any(), any(), any())
        }
    }

    @Test
    fun `throws illegal argument when personident header is missing`() {
        val routingContext = routingContextWithHeaders(
            headers = Headers.build {
                append(NAV_CALL_ID_HEADER, callId)
                append(HttpHeaders.Authorization, bearerHeader(token))
            }
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                routingContext.checkVeilederTilgang(
                    action = action,
                    veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                ) {}
            }
        }

        coVerify(exactly = 0) {
            veilederTilgangskontrollClient.hasAccess(any(), any(), any())
        }
        coVerify(exactly = 0) {
            veilederTilgangskontrollClient.hasWriteAccess(any(), any(), any())
        }
    }

    private fun routingContextWithHeaders(headers: Headers): RoutingContext {
        val routingRequest = mockk<RoutingRequest>()
        every { routingRequest.headers } returns headers

        val routingCall = mockk<RoutingCall>()
        every { routingCall.request } returns routingRequest

        val routingContext = mockk<RoutingContext>()
        every { routingContext.call } returns routingCall
        return routingContext
    }
}
