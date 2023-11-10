package no.nav.syfo.aktivitetskrav.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.AktivitetskravVarselService
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.domain.toVurderingResponseDto
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
    aktivitetskravVarselService: AktivitetskravVarselService,
) {
    route(aktivitetskravApiBasePath) {
        install(VeilederTilgangskontrollPlugin) {
            this.action = API_ACTION
            this.veilederTilgangskontrollClient = veilederTilgangskontrollClient
        }
        get(aktivitetskravApiPersonidentPath) {
            val personIdent = call.personIdent()
            val aktivitetskravAfterCutoff = aktivitetskravService.getAktivitetskravAfterCutoff(
                personIdent = personIdent,
            )
            val responseDTOList = aktivitetskravAfterCutoff.map { aktivitetskrav ->
                val vurderingResponseDTOs = aktivitetskrav.vurderinger.map { vurdering ->
                    val varsel = aktivitetskravVarselService.getVarsel(vurdering.uuid)
                    vurdering.toVurderingResponseDto(varsel)
                }
                AktivitetskravResponseDTO.from(aktivitetskrav, vurderingResponseDTOs)
            }

            call.respond(responseDTOList)
        }
        post {
            val personIdent = call.personIdent()
            val requestDTO: NewAktivitetskravDTO? =
                runCatching { call.receiveNullable<NewAktivitetskravDTO>() }.getOrNull()
            val createdAktivitetskrav =
                aktivitetskravService.createAktivitetskrav(personIdent, requestDTO?.previousAktivitetskravUuid)

            call.respond(HttpStatusCode.Created, createdAktivitetskrav)
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
            val aktivitetskravUUID = UUID.fromString(call.parameters[aktivitetskravParam])
            val requestDTO = call.receive<ForhandsvarselDTO>()
            if (requestDTO.document.isEmpty()) {
                throw IllegalArgumentException("Forhandsvarsel can't have an empty document")
            }

            val aktivitetskrav =
                aktivitetskravService.getAktivitetskrav(uuid = aktivitetskravUUID)
                    ?: throw IllegalArgumentException("Failed to create forhandsvarsel: aktivitetskrav not found")

            if (
                aktivitetskrav.status != AktivitetskravStatus.NY &&
                aktivitetskrav.status != AktivitetskravStatus.NY_VURDERING &&
                aktivitetskrav.status != AktivitetskravStatus.AVVENT
            ) {
                throw IllegalArgumentException("Failed to create forhandsvarsel: aktivitetskrav is not in a valid state")
            }

            val forhandsvarsel = aktivitetskravVarselService.sendForhandsvarsel(
                aktivitetskrav = aktivitetskrav,
                veilederIdent = call.getNAVIdent(),
                personIdent = call.personIdent(),
                forhandsvarselDTO = requestDTO,
                callId = call.getCallId(),
            )
            call.respond(HttpStatusCode.Created, forhandsvarsel)
        }
    }
}

private fun ApplicationCall.personIdent(): PersonIdent = this.getPersonIdent()
    ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")
