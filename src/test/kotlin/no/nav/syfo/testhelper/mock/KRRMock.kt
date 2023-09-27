package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.client.krr.KRRResponseDTO
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER

fun MockRequestHandleScope.krrMockResponse(request: HttpRequestData): HttpResponseData {
    return when (val personident = request.headers[NAV_PERSONIDENT_HEADER]) {
        UserConstants.ARBEIDSTAKER_PERSONIDENT_RESERVERT.value -> respond(generateReservertKRRResponseDTO(personident))
        else -> respond(generateKRRResponseDTO(personident))
    }
}

private fun generateKRRResponseDTO(personIdent: String?) = KRRResponseDTO(
    personident = personIdent ?: "",
    aktiv = true,
    kanVarsles = true,
    reservasjonOppdatert = "2023-09-25T11:27:43.017Z",
    reservert = false,
    spraak = null,
    spraakOppdatert = null,
    epostadresse = "test@nav.no",
    epostadresseOppdatert = null,
    epostadresseVerifisert = null,
    mobiltelefonnummer = "90909090",
    mobiltelefonnummerOppdatert = null,
    mobiltelefonnummerVerifisert = null,
    sikkerDigitalPostkasse = null,
)

private fun generateReservertKRRResponseDTO(personIdent: String) =
    generateKRRResponseDTO(personIdent).copy(
        reservert = true,
        kanVarsles = false,
    )
