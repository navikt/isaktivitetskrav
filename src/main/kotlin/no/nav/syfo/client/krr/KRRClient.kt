package no.nav.syfo.client.krr

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.bearerHeader
import org.slf4j.LoggerFactory

class KRRClient(
    private val azureAdClient: AzureAdClient,
    baseUrl: String,
    private val clientId: String,
) {
    private val httpClient = httpClientDefault()

    private val krrKontaktinfoUrl: String = "$baseUrl$KRR_KONTAKTINFORMASJON_PATH"

    suspend fun hentDigitalKontaktinformasjon(
        callId: String,
        personIdent: PersonIdent,
        token: String,
    ): KRRResponseDTO {
        val oboToken = azureAdClient.getOnBehalfOfToken(
            scopeClientId = clientId,
            token = token,
        )?.accessToken ?: throw RuntimeException("Failed to request get response from KRR: Failed to get OBO token")

        try {
            val response: KRRResponseDTO? = httpClient.get(this.krrKontaktinfoUrl) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken))
                header(NAV_PERSONIDENT_HEADER, personIdent)
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
            }.body()

            if (response != null) {
                COUNT_CALL_KRR_KONTAKTINFORMASJON_SUCCESS.increment()
                return response
            } else {
                val errorMessage = "Failed to get Kontaktinfo from KRR: response body is null"
                log.error(errorMessage)
                throw KRRRequestFailedException(errorMessage)
            }
        } catch (e: ClosedReceiveChannelException) {
            COUNT_CALL_KRR_KONTAKTINFORMASJON_FAIL.increment()
            throw RuntimeException("Caught ClosedReceiveChannelException in KRRClient.hentDigitalKontaktinformasjon", e)
        } catch (e: ResponseException) {
            log.error(
                "Error while requesting Response from KRR {}, {}, {}",
                StructuredArguments.keyValue("statusCode", e.response.status.value.toString()),
                StructuredArguments.keyValue("message", e.message),
                StructuredArguments.keyValue("callId", callId),
            )
            COUNT_CALL_KRR_KONTAKTINFORMASJON_FAIL.increment()
            throw e
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(KRRClient::class.java)
        const val KRR_KONTAKTINFORMASJON_PATH = "/rest/v1/person"
    }
}
