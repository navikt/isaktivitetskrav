package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.pdl.domain.*
import no.nav.syfo.testhelper.UserConstants

suspend fun MockRequestHandleScope.pdlMockResponse(request: HttpRequestData): HttpResponseData {
    val isHentIdenterRequest = request.receiveBody<Any>().toString().contains("hentIdenter")
    return if (isHentIdenterRequest) {
        val pdlRequest = request.receiveBody<PdlHentIdenterRequest>()
        when (val personIdent = PersonIdent(pdlRequest.variables.ident)) {
            UserConstants.THIRD_ARBEIDSTAKER_PERSONIDENT -> {
                val otherPersonIdent = PersonIdent("11111111111")
                respond(generatePdlIdenterResponse(otherPersonIdent))
            }

            else -> {
                respond(generatePdlIdenterResponse(personIdent))
            }
        }
    } else {
        val pdlRequest = request.receiveBody<PdlHentPersonRequest>()
        when (PersonIdent(pdlRequest.variables.ident)) {
            UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME -> respond(generatePdlPersonResponse())
            UserConstants.ARBEIDSTAKER_PERSONIDENT_NAME_WITH_DASH -> respond(
                generatePdlPersonResponse(
                    PdlPersonNavn(
                        fornavn = UserConstants.PERSON_FORNAVN_DASH,
                        mellomnavn = UserConstants.PERSON_MELLOMNAVN,
                        etternavn = UserConstants.PERSON_ETTERNAVN,
                    )
                )
            )

            else -> respond(generatePdlPersonResponse(generatePdlPersonNavn()))
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

fun generatePdlPersonResponse(pdlPersonNavn: PdlPersonNavn? = null) = PdlPersonResponse(
    errors = null,
    data = generatePdlHentPerson(pdlPersonNavn)
)

fun generatePdlPersonNavn(): PdlPersonNavn = PdlPersonNavn(
    fornavn = UserConstants.PERSON_FORNAVN,
    mellomnavn = UserConstants.PERSON_MELLOMNAVN,
    etternavn = UserConstants.PERSON_ETTERNAVN,
)

fun generatePdlHentPerson(
    pdlPersonNavn: PdlPersonNavn?,
): PdlHentPerson = PdlHentPerson(
    hentPerson = PdlPerson(
        navn = if (pdlPersonNavn != null) listOf(pdlPersonNavn) else emptyList(),
    )
)
