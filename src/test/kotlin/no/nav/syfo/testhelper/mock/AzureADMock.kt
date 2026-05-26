package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.*

fun MockRequestHandleScope.azureAdMockResponse(request: HttpRequestData): HttpResponseData {
    // Echo the incoming "assertion" token (see getOnBehalfOfToken() in AzureAdClient) as access
    // token. This makes it so that the navIdent claim in the token that an endpoint was called
    // with in the API tests survives OBO flow and can be read out again by TilgangskontrollMock.
    val assertionToken = (request.body as? FormDataContent)?.formData?.get("assertion")
    val token = assertionToken ?: "token"
    return respond(
        content = """{"access_token":"$token","expires_in":3600,"token_type":"Bearer"}""",
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}
