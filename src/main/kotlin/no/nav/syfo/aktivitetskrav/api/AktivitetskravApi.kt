package no.nav.syfo.aktivitetskrav.api

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.aktivitetskrav.AktivitetskravVurderingService
import no.nav.syfo.aktivitetskrav.domain.toResponseDTOList
import no.nav.syfo.application.api.VeilederTilgangskontrollPlugin
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.getPersonIdent

const val aktivitetskravApiBasePath = "/api/internad/v1/aktivitetskrav"
const val aktivitetskravApiPersonidentPath = "/personident"

private const val apiAction = "access aktivitetskrav for person"

fun Route.registerAktivitetskravApi(
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    aktivitetskravVurderingService: AktivitetskravVurderingService,
) {
    route(aktivitetskravApiBasePath) {
        install(VeilederTilgangskontrollPlugin) {
            this.action = apiAction
            this.veilederTilgangskontrollClient = veilederTilgangskontrollClient
        }
        get(aktivitetskravApiPersonidentPath) {
            val personIdent = call.getPersonIdent()
                ?: throw IllegalArgumentException("Failed to $apiAction: No $NAV_PERSONIDENT_HEADER supplied in request header")
            val responseDTOList = aktivitetskravVurderingService.getAktivitetskravVurderinger(
                personIdent = personIdent,
            ).toResponseDTOList()

            call.respond(responseDTOList)
        }
    }
}
