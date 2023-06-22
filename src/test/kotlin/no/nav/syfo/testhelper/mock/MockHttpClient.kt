package no.nav.syfo.testhelper.mock

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import no.nav.syfo.application.Environment
import no.nav.syfo.client.commonConfig

fun mockHttpClient(environment: Environment) = HttpClient(MockEngine) {
    commonConfig()
    engine {
        addHandler { request ->
            val requestUrl = request.url.encodedPath
            when {
                requestUrl == "/${environment.azure.openidConfigTokenEndpoint}" -> azureAdMockResponse()
                requestUrl.startsWith("/${environment.clients.syfotilgangskontroll.baseUrl}") -> syfoTilgangskontrollResponse(
                    request
                )

                requestUrl.startsWith("/${environment.clients.pdl.baseUrl}") -> pdlMockResponse(request)
                else -> error("Unhandled ${request.url.encodedPath}")
            }
        }
    }
}
