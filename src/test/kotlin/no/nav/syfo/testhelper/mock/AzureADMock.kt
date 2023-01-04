package no.nav.syfo.testhelper.mock

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.azuread.AzureAdTokenResponse

class AzureADMock : MockServer() {
    private val azureAdTokenResponse = AzureAdTokenResponse(
        access_token = "token",
        expires_in = 3600,
        token_type = "type",
    )

    override val name = "azuread"
    override val routingConfiguration: Routing.() -> Unit = {
        post {
            call.respond(azureAdTokenResponse)
        }
    }
}
