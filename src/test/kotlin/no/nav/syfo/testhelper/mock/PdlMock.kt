package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.client.pdl.domain.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.testhelper.UserConstants

suspend fun MockRequestHandleScope.pdlMockResponse(request: HttpRequestData): HttpResponseData {
    val pdlRequest = request.receiveBody<PdlHentIdenterRequest>()
    return when (val personIdent = PersonIdent(pdlRequest.variables.ident)) {
        UserConstants.THIRD_ARBEIDSTAKER_PERSONIDENT -> {
            val otherPersonIdent = PersonIdent("11111111111")
            respond(generatePdlIdenterResponse(otherPersonIdent))
        }

        else -> {
            respond(generatePdlIdenterResponse(personIdent))
        }
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
