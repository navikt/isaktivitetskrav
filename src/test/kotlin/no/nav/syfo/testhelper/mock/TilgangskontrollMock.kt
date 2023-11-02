package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.client.veiledertilgang.Tilgang
import no.nav.syfo.testhelper.UserConstants.PERSONIDENT_VEILEDER_NO_ACCESS
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER

fun MockRequestHandleScope.tilgangskontrollResponse(request: HttpRequestData): HttpResponseData {
    return when (request.headers[NAV_PERSONIDENT_HEADER]) {
        PERSONIDENT_VEILEDER_NO_ACCESS.value -> respond(Tilgang(erGodkjent = false))
        else -> respond(Tilgang(erGodkjent = true))
    }
}
