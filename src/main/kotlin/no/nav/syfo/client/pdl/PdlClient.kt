package no.nav.syfo.client.pdl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.syfo.client.ClientEnvironment
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.azuread.AzureAdToken
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.client.pdl.domain.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.ALLE_TEMA_HEADERVERDI
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import no.nav.syfo.util.TEMA_HEADER
import no.nav.syfo.util.bearerHeader
import org.slf4j.LoggerFactory

class PdlClient(
    private val azureAdClient: AzureAdClient,
    private val pdlEnvironment: ClientEnvironment,
    private val httpClient: HttpClient = httpClientDefault(),
) {

    suspend fun getPdlIdenter(
        personIdent: PersonIdent,
        callId: String? = null,
    ): PdlHentIdenter? {
        val token = azureAdClient.getSystemToken(pdlEnvironment.clientId)
            ?: throw RuntimeException("Failed to send PdlHentIdenterRequest to PDL: No token was found")

        val query = getPdlQuery(
            queryFilePath = "/pdl/hentIdenter.graphql",
        )

        val request = PdlHentIdenterRequest(
            query = query,
            variables = PdlHentIdenterRequestVariables(
                ident = personIdent.value,
                historikk = true,
                grupper = listOf(
                    IdentType.FOLKEREGISTERIDENT,
                ),
            ),
        )

        val response: HttpResponse = httpClient.post(pdlEnvironment.baseUrl) {
            header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header(TEMA_HEADER, ALLE_TEMA_HEADERVERDI)
            header(NAV_CALL_ID_HEADER, callId)
            header(IDENTER_HEADER, IDENTER_HEADER)
            setBody(request)
        }

        when (response.status) {
            HttpStatusCode.OK -> {
                val pdlIdenterResponse = response.body<PdlIdenterResponse>()
                return if (!pdlIdenterResponse.errors.isNullOrEmpty()) {
                    COUNT_CALL_PDL_IDENTER_FAIL.increment()
                    pdlIdenterResponse.errors.forEach {
                        logger.error("Error while requesting IdentList from PersonDataLosningen: ${it.errorMessage()}")
                    }
                    null
                } else {
                    COUNT_CALL_PDL_IDENTER_SUCCESS.increment()
                    pdlIdenterResponse.data
                }
            }

            else -> {
                COUNT_CALL_PDL_IDENTER_FAIL.increment()
                logger.error("Request to get IdentList with url: ${pdlEnvironment.baseUrl} failed with reponse code ${response.status.value}")
                return null
            }
        }
    }

    suspend fun navn(
        personIdent: PersonIdent,
    ): String {
        val token = azureAdClient.getSystemToken(pdlEnvironment.clientId)
            ?: throw RuntimeException("Failed to send request to PDL: No token was found")
        return person(personIdent, token)?.fullName()
            ?: throw RuntimeException("PDL returned empty navn for given fnr")
    }

    private suspend fun person(
        personIdent: PersonIdent,
        token: AzureAdToken,
    ): PdlPerson? {
        val query = getPdlQuery("/pdl/hentPerson.graphql")
        val request = PdlHentPersonRequest(query, PdlHentPersonRequestVariables(personIdent.value))

        val response: HttpResponse = httpClient.post(pdlEnvironment.baseUrl) {
            setBody(request)
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
            header(TEMA_HEADER, ALLE_TEMA_HEADERVERDI)
        }

        when (response.status) {
            HttpStatusCode.OK -> {
                val pdlPersonReponse = response.body<PdlPersonResponse>()
                return if (!pdlPersonReponse.errors.isNullOrEmpty()) {
                    COUNT_CALL_PDL_PERSON_FAIL.increment()
                    pdlPersonReponse.errors.forEach {
                        logger.error("Error while requesting person from PersonDataLosningen: ${it.errorMessage()}")
                    }
                    null
                } else {
                    COUNT_CALL_PDL_PERSON_SUCCESS.increment()
                    pdlPersonReponse.data?.hentPerson
                }
            }

            else -> {
                COUNT_CALL_PDL_PERSON_FAIL.increment()
                logger.error("Request with url: ${pdlEnvironment.baseUrl} failed with reponse code ${response.status.value}")
                return null
            }
        }
    }

    private fun getPdlQuery(queryFilePath: String): String {
        return this::class.java.getResource(queryFilePath)!!
            .readText()
            .replace("[\n\r]", "")
    }

    companion object {
        const val IDENTER_HEADER = "identer"
        private val logger = LoggerFactory.getLogger(PdlClient::class.java)
    }
}
