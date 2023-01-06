package no.nav.syfo.testhelper.mock

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.pdl.domain.*
import no.nav.syfo.domain.PersonIdent

class PdlMock : MockServer() {
    override val name = "pdl"
    override val routingConfiguration: Routing.() -> Unit = {
        post {
            val pdlRequest = call.receive<PdlHentIdenterRequest>()
            val personIdent = PersonIdent(pdlRequest.variables.ident)
            call.respond(generatePdlIdenterResponse(personIdent))
        }
    }

    private fun generatePdlIdenterResponse(
        personIdent: PersonIdent,
    ) = PdlIdenterResponse(
        data = PdlHentIdenter(
            hentIdenter = PdlIdenter(
                identer = listOf(
                    PdlIdent(
                        ident = personIdent.value,
                        historisk = false,
                        gruppe = IdentType.FOLKEREGISTERIDENT,
                    ),
                    PdlIdent(
                        ident = personIdent.value.replace("2", "1"),
                        historisk = true,
                        gruppe = IdentType.FOLKEREGISTERIDENT,
                    ),
                ),
            ),
        ),
        errors = null,
    )
}
