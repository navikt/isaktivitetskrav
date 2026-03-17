package no.nav.syfo.infrastructure.clients.behandlendeenhet

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.application.IBehandlendeEnhetClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.client.ClientEnvironment
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.httpClientDefault
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import org.slf4j.LoggerFactory
import java.util.UUID

class BehandlendeEnhetClient(
    private val azureAdClient: AzureAdClient,
    private val clientEnvironment: ClientEnvironment,
    private val httpClient: HttpClient = httpClientDefault(),
) : IBehandlendeEnhetClient {
    private val behandlendeEnhetUrl = "${clientEnvironment.baseUrl}$BEHANDLENDEENHET_SYSTEM_PATH"

    override suspend fun getEnhet(
        personident: PersonIdent,
    ): BehandlendeEnhetResponseDTO? {
        val systemToken = azureAdClient.getSystemToken(
            scopeClientId = clientEnvironment.clientId,
        )
            ?.accessToken
            ?: throw RuntimeException("Failed to request access to Syfobehandlendeenhet: Failed to get token")

        return try {
            val response: HttpResponse = httpClient.get(behandlendeEnhetUrl) {
                header(HttpHeaders.Authorization, bearerHeader(systemToken))
                header(NAV_CALL_ID_HEADER, UUID.randomUUID().toString())
                header(NAV_PERSONIDENT_HEADER, personident.value)
                accept(ContentType.Application.Json)
            }
            if (response.status == HttpStatusCode.OK) {
                response.body<BehandlendeEnhetResponseDTO>()
            } else {
                null
            }
        } catch (e: Exception) {
            log.error(
                """
                    Error while requesting BehandlendeEnhet of person from Syfobehandlendeenhet
                    Error: ${e.message}
                """.trimIndent(),
            )
            throw e
        }
    }

    companion object {
        const val BEHANDLENDEENHET_SYSTEM_PATH = "/api/system/v2/personident"
        private val log = LoggerFactory.getLogger(BehandlendeEnhetClient::class.java)
    }
}
