package no.nav.syfo.aktivitetskrav.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.domain.toResponseDTOList
import no.nav.syfo.application.api.VeilederTilgangskontrollPlugin
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.*
import java.util.UUID

const val aktivitetskravApiBasePath = "/api/internad/v1/aktivitetskrav"
const val aktivitetskravApiPersonidentPath = "/personident"
const val aktivitetskravParam = "aktivitetskravUuid"
const val vurderAktivitetskravPath = "/vurder"

private const val apiAction = "access aktivitetskrav for person"

fun Route.registerAktivitetskravApi(
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    aktivitetskravService: AktivitetskravService,
) {
    route(aktivitetskravApiBasePath) {
        install(VeilederTilgangskontrollPlugin) {
            this.action = apiAction
            this.veilederTilgangskontrollClient = veilederTilgangskontrollClient
        }
        get(aktivitetskravApiPersonidentPath) {
            val personIdent = call.personIdent()
            val responseDTOList = aktivitetskravService.getAktivitetskrav(
                personIdent = personIdent,
            ).toResponseDTOList()

            call.respond(responseDTOList)
        }
        post("/{$aktivitetskravParam}$vurderAktivitetskravPath") {
            val personIdent = call.personIdent()
            val aktivitetskravUUID = UUID.fromString(call.parameters[aktivitetskravParam])
            val requestDTO = call.receive<AktivitetskravVurderingRequestDTO>()

            val aktivitetskravToUpdate =
                aktivitetskravService.getAktivitetskrav(uuid = aktivitetskravUUID)
                    ?: throw IllegalArgumentException("Failed to vurdere aktivitetskrav: aktivitetskrav not found")

            if (aktivitetskravToUpdate.personIdent != personIdent) {
                throw IllegalArgumentException("Failed to vurdere aktivitetskrav: personIdent on aktivitetskrav differs from request")
            }

            aktivitetskravService.vurderAktivitetskrav(
                aktivitetskrav = aktivitetskravToUpdate,
                aktivitetskravVurderingRequestDTO = requestDTO,
                veilederIdent = call.getNAVIdent(),
            )

            call.respond(HttpStatusCode.OK)
        }
    }
}

private fun ApplicationCall.personIdent(): PersonIdent = this.getPersonIdent()
    ?: throw IllegalArgumentException("Failed to $apiAction: No $NAV_PERSONIDENT_HEADER supplied in request header")
