package no.nav.syfo.testhelper.mock

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.veiledertilgang.Tilgang
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.testhelper.UserConstants.PERSONIDENT_VEILEDER_NO_ACCESS
import no.nav.syfo.util.getPersonIdent

class SyfoTilgangskontrollMock : MockServer() {
    override val name = "syfotilgangskontroll"
    override val routingConfiguration: Routing.() -> Unit = {
        get(VeilederTilgangskontrollClient.TILGANGSKONTROLL_PERSON_PATH) {
            when (call.getPersonIdent()?.value) {
                PERSONIDENT_VEILEDER_NO_ACCESS.value -> call.respond(
                    Tilgang(harTilgang = false)
                )

                else -> call.respond(
                    Tilgang(harTilgang = true)
                )
            }
        }
    }
}
