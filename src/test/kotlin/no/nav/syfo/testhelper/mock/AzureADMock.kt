package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.FormDataContent
import no.nav.syfo.infrastructure.client.azuread.AzureAdTokenResponse

fun MockRequestHandleScope.azureAdMockResponse(request: HttpRequestData): HttpResponseData {
    // Echo the incoming "assertion" token (see getOnBehalfOfToken() in  AzureAdClient) as access
    // token. This makes it so that the navIdent claim in the token that an endpoint was called
    // with in the API tests survives OBO flow and can be read out again by TilgangskontrollMock.
    val assertionToken = (request.body as? FormDataContent)?.formData?.get("assertion")
    return respond(
        AzureAdTokenResponse(
            access_token = assertionToken ?: "token",
            expires_in = 3600,
            token_type = "type",
        )
    )
}
