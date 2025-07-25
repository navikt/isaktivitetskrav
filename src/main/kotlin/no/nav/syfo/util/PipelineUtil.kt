package no.nav.syfo.util

import com.auth0.jwt.JWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.nav.syfo.api.exception.ForbiddenAccessVeilederException
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.client.veiledertilgang.VeilederTilgangskontrollClient

const val JWT_CLAIM_AZP = "azp"
const val JWT_CLAIM_NAVIDENT = "NAVident"

fun ApplicationCall.getCallId(): String = this.request.headers[NAV_CALL_ID_HEADER].toString()

fun ApplicationCall.getPersonIdent(): PersonIdent? =
    this.request.headers[NAV_PERSONIDENT_HEADER]?.let { PersonIdent(it) }

fun ApplicationCall.getConsumerClientId(): String? =
    getBearerHeader()?.let {
        JWT.decode(it).claims[JWT_CLAIM_AZP]?.asString()
    }

fun ApplicationCall.getNAVIdent(): String {
    val token = getBearerHeader() ?: throw Error("No Authorization header supplied")
    return JWT.decode(token).claims[JWT_CLAIM_NAVIDENT]?.asString()
        ?: throw Error("Missing NAVident in private claims")
}

fun ApplicationCall.getBearerHeader(): String? =
    this.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")

suspend fun RoutingContext.checkVeilederTilgang(
    action: String,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    block: suspend () -> Unit,
) {
    val callId = call.getCallId()
    val token = call.getBearerHeader()
        ?: throw IllegalArgumentException("Failed to complete the following action: $action. No Authorization header supplied")
    val personident = call.getPersonIdent()
        ?: throw IllegalArgumentException("Failed to $action: No $NAV_PERSONIDENT_HEADER supplied in request header")

    val hasAccess = veilederTilgangskontrollClient.hasAccess(
        callId = callId,
        personIdent = personident,
        token = token,
    )
    if (!hasAccess) {
        throw ForbiddenAccessVeilederException(
            action = action,
        )
    } else {
        block()
    }
}
