package no.nav.syfo.application.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import no.nav.syfo.application.exception.ForbiddenAccessVeilederException
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.util.*

class VeilederTilgangskontrollPluginConfig {
    lateinit var action: String
    lateinit var veilederTilgangskontrollClient: VeilederTilgangskontrollClient
}

val VeilederTilgangskontrollPlugin = createRouteScopedPlugin(
    name = "VeilederTilgangskontrollPlugin",
    createConfiguration = ::VeilederTilgangskontrollPluginConfig
) {
    val action = this.pluginConfig.action
    val veilederTilgangskontrollClient = this.pluginConfig.veilederTilgangskontrollClient

    on(AuthenticationChecked) { call ->
        when {
            call.isHandled -> {
                /** Autentisering kan ha feilet og gitt respons på kallet, ikke gå videre */
            }

            else -> {
                val callId = call.getCallId()
                val personIdent = call.getPersonIdent()
                    ?: throw IllegalArgumentException("Failed to $action: No $NAV_PERSONIDENT_HEADER supplied in request header")
                val token = call.getBearerHeader()
                    ?: throw IllegalArgumentException("Failed to complete the following action: $action. No Authorization header supplied")

                val hasAccess = veilederTilgangskontrollClient.hasAccess(
                    callId = callId,
                    personIdent = personIdent,
                    token = token,
                )
                if (!hasAccess) {
                    throw ForbiddenAccessVeilederException(
                        action = action,
                    )
                }
            }
        }
    }
}
