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
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.getCallId
import no.nav.syfo.util.getNAVIdent
import no.nav.syfo.util.getPersonIdent
import java.util.*

const val aktivitetskravApiBasePath = "/api/internad/v1/aktivitetskrav"
const val aktivitetskravApiPersonidentPath = "/personident"
const val aktivitetskravParam = "aktivitetskravUuid"
const val vurderAktivitetskravPath = "/vurder"
const val forhandsvarselPath = "/forhandsvarsel"

private const val API_ACTION = "access aktivitetskrav for person"

fun Route.registerAktivitetskravApi(
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    aktivitetskravService: AktivitetskravService,
) {
    route(aktivitetskravApiBasePath) {
        install(VeilederTilgangskontrollPlugin) {
            this.action = API_ACTION
            this.veilederTilgangskontrollClient = veilederTilgangskontrollClient
        }
        get(aktivitetskravApiPersonidentPath) {
            val personIdent = call.personIdent()
            val responseDTOList = aktivitetskravService.getAktivitetskravAfterCutoff(
                personIdent = personIdent,
            ).toResponseDTOList()

            call.respond(responseDTOList)
        }
        post("/{$aktivitetskravParam}$vurderAktivitetskravPath") {
            val personIdent = call.personIdent()
            val aktivitetskravUUID = UUID.fromString(call.parameters[aktivitetskravParam])
            val requestDTO = call.receive<AktivitetskravVurderingRequestDTO>()

            val aktivitetskrav =
                aktivitetskravService.getAktivitetskrav(uuid = aktivitetskravUUID)
                    ?: throw IllegalArgumentException("Failed to vurdere aktivitetskrav: aktivitetskrav not found")

            if (aktivitetskrav.personIdent != personIdent) {
                throw IllegalArgumentException("Failed to vurdere aktivitetskrav: personIdent on aktivitetskrav differs from request")
            }

            val aktivitetskravVurdering = requestDTO.toAktivitetskravVurdering(
                createdByIdent = call.getNAVIdent(),
            )
            aktivitetskravService.vurderAktivitetskrav(
                aktivitetskrav = aktivitetskrav,
                aktivitetskravVurdering = aktivitetskravVurdering,
            )

            call.respond(HttpStatusCode.OK)
        }
        post(vurderAktivitetskravPath) {
            val personIdent = call.personIdent()
            val requestDTO = call.receive<AktivitetskravVurderingRequestDTO>()

            val aktivitetskravVurdering = requestDTO.toAktivitetskravVurdering(
                createdByIdent = call.getNAVIdent(),
            )
            aktivitetskravService.createAndVurderAktivitetskrav(
                personIdent = personIdent,
                aktivitetskravVurdering = aktivitetskravVurdering
            )

            call.respond(HttpStatusCode.OK)
        }
        post("/{$aktivitetskravParam}$forhandsvarselPath") {
            val personIdent = call.personIdent()
            val callId = call.getCallId()
            val aktivitetskravUUID = UUID.fromString(call.parameters[aktivitetskravParam])

            // Trenger kanskje ikke veilederIdent her?
            // Vi må sette opp en reminder på at veileder skal få en oppgave når fristen på å svare til innbygger har gått ut.
            // val veilederIdent = call.getNAVIdent()
            val requestDTO = call.receive<ForhandsvarselDTO>()
            if (requestDTO.document.isEmpty()) {
                throw IllegalArgumentException("Forhandsvarsel can't have an empty document")
            }

            aktivitetskravService.sendForhandsvarsel(
                personIdent,
                call.getNAVIdent(),
                aktivitetskravUUID,
                requestDTO,
                callId
            )

            call.respond(HttpStatusCode.Created)
        }
    }
}

private fun ApplicationCall.personIdent(): PersonIdent = this.getPersonIdent()
    ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")
