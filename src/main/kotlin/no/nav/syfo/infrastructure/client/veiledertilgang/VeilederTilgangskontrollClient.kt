package no.nav.syfo.infrastructure.client.veiledertilgang

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.client.ClientEnvironment
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.httpClientDefault
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory

class VeilederTilgangskontrollClient(
    private val azureAdClient: AzureAdClient,
    private val clientEnvironment: ClientEnvironment,
    private val httpClient: HttpClient = httpClientDefault(),
) {
    private val tilgangskontrollPersonUrl = "${clientEnvironment.baseUrl}$TILGANGSKONTROLL_PERSON_PATH"
    private val tilgangskontrollBrukereUrl = "${clientEnvironment.baseUrl}$TILGANGSKONTROLL_BRUKERE_PATH"

    suspend fun hasAccess(callId: String, personIdent: PersonIdent, token: String): Boolean {
        val onBehalfOfToken = azureAdClient.getOnBehalfOfToken(
            scopeClientId = clientEnvironment.clientId,
            token = token,
        )?.accessToken ?: throw RuntimeException("Failed to request access to Person: Failed to get OBO token")

        return try {
            val tilgang = httpClient.get(tilgangskontrollPersonUrl) {
                header(HttpHeaders.Authorization, bearerHeader(onBehalfOfToken))
                header(NAV_PERSONIDENT_HEADER, personIdent.value)
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
            }
            COUNT_CALL_TILGANGSKONTROLL_PERSON_SUCCESS.increment()
            tilgang.body<Tilgang>().erGodkjent
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.Forbidden) {
                COUNT_CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN.increment()
            } else {
                handleUnexpectedResponseException(e.response, callId)
            }
            false
        }
    }

    suspend fun veilederPersonerAccess(
        personidenter: List<PersonIdent>,
        token: String,
        callId: String,
    ): List<PersonIdent>? {
        val oboToken = azureAdClient.getOnBehalfOfToken(
            scopeClientId = clientEnvironment.clientId,
            token = token
        )?.accessToken
            ?: throw RuntimeException("Failed to request access to list of persons: Failed to get OBO token")

        val identer = personidenter.map { it.value }
        return try {
            val response: HttpResponse = httpClient.post(tilgangskontrollBrukereUrl) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken))
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(identer)
            }
            response.body<List<String>>().map { PersonIdent(it) }
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Forbidden) {
                log.warn("Forbidden to request access to list of person from istilgangskontroll")
                null
            } else {
                log.error("Error while requesting access to list of person from istilgangskontroll: ${e.message}", e)
                null
            }
        } catch (e: ServerResponseException) {
            log.error("Error while requesting access to list of person from istilgangskontroll: ${e.message}", e)
            null
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        callId: String,
    ) {
        log.error(
            "Error while requesting access to person from istilgangskontroll with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            StructuredArguments.keyValue("callId", callId)
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(VeilederTilgangskontrollClient::class.java)

        const val TILGANGSKONTROLL_PERSON_PATH = "/api/tilgang/navident/person"
        const val TILGANGSKONTROLL_BRUKERE_PATH = "/api/tilgang/navident/brukere"
    }
}
