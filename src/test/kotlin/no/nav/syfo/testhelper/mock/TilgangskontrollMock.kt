package no.nav.syfo.testhelper.mock

import com.auth0.jwt.JWT
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import no.nav.syfo.common.mock.MockTilgangResponse
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient.Companion.TILGANGSKONTROLL_BRUKERE_PATH
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient.Companion.TILGANGSKONTROLL_PERSON_PATH
import no.nav.syfo.common.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.common.util.ktor.JWT_CLAIM_NAVIDENT
import no.nav.syfo.testhelper.UserConstants.PERSONIDENT_VEILEDER_NO_ACCESS
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT_WITH_LESETILGANG

private val navIdentsWithLeseTilgangOnly = setOf(VEILEDER_IDENT_WITH_LESETILGANG)

// Decode caller navIdent claim from token so this mock can enforce write access for specific
// test veileder idents (see src/test/kotlin/no/nav/syfo/testhelper/UserConstants.kt).
private fun HttpRequestData.navIdent(): String? =
    headers[HttpHeaders.Authorization]
        ?.removePrefix("Bearer ")
        ?.let { token ->
            runCatching { JWT.decode(token).claims[JWT_CLAIM_NAVIDENT]?.asString() }.getOrNull()
        }

fun MockRequestHandleScope.tilgangskontrollResponse(request: HttpRequestData): HttpResponseData {
    val requestUrl = request.url.encodedPath

    return when {
        requestUrl.endsWith(TILGANGSKONTROLL_PERSON_PATH) -> {
            val erGodkjent = request.headers[NAV_PERSONIDENT_HEADER] != PERSONIDENT_VEILEDER_NO_ACCESS.value
            val fullTilgang = request.navIdent() !in navIdentsWithLeseTilgangOnly
            respond(MockTilgangResponse(erGodkjent = erGodkjent, fullTilgang = fullTilgang))
        }
        requestUrl.endsWith(TILGANGSKONTROLL_BRUKERE_PATH) -> {
            val body = runBlocking<List<String>> { request.receiveBody() }.toMutableList()
            body.removeAll { it == PERSONIDENT_VEILEDER_NO_ACCESS.value }
            respond(body)
        }
        else -> error("Unhandled path $requestUrl")
    }
}
