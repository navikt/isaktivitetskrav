package no.nav.syfo.testhelper.mock

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.azuread.AzureAdTokenResponse

class AzureADMock : MockServer() {
    private val azureAdTokenResponse = AzureAdTokenResponse(
        accessToken = "token",
        expiresIn = 3600,
        tokenType = "type",
    )

    override val name = "azuread"
    override val routingConfiguration: Routing.() -> Unit = {
        post {
            call.respond(azureAdTokenResponse)
        }
    }
}
