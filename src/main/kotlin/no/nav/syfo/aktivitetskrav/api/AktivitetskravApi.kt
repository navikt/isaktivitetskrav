package no.nav.syfo.aktivitetskrav.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.AktivitetskravVarselService
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.checkVeilederTilgang
import no.nav.syfo.util.getBearerHeader
import no.nav.syfo.util.getCallId
import no.nav.syfo.util.getNAVIdent
import no.nav.syfo.util.getPersonIdent
import java.util.*

const val aktivitetskravApiBasePath = "/api/internad/v1/aktivitetskrav"
const val aktivitetskravApiPersonidentPath = "/personident"
const val aktivitetskravApiHistorikkPath = "/historikk"
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
        get(aktivitetskravApiPersonidentPath) {
            call.checkVeilederTilgang(
                action = API_ACTION,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val personIdent = call.personIdent()
                val aktivitetskravAfterCutoff = aktivitetskravService.getAktivitetskravAfterCutoff(
                    personIdent = personIdent,
                )
                val responseDTOList = aktivitetskravAfterCutoff.map { aktivitetskrav ->
                    val vurderingResponseDTOs = aktivitetskrav.vurderinger.map { vurdering ->
                        val varsel = aktivitetskravVarselService.getVarsel(vurdering.uuid)
                        AktivitetskravVurderingResponseDTO.from(vurdering, varsel)
                    }
                    AktivitetskravResponseDTO.from(aktivitetskrav, vurderingResponseDTOs)
                }

                call.respond(responseDTOList)
            }
        }
        get(aktivitetskravApiHistorikkPath) {
            call.checkVeilederTilgang(
                action = API_ACTION,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val personIdent = call.personIdent()
                call.respond(aktivitetskravService.getAktivitetskravHistorikk(personIdent))
            }
        }
        post {
            call.checkVeilederTilgang(
                action = API_ACTION,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val personIdent = call.personIdent()
                val requestDTO: NewAktivitetskravDTO? =
                    runCatching { call.receiveNullable<NewAktivitetskravDTO>() }.getOrNull()
                val previousAktivitetskrav = requestDTO?.previousAktivitetskravUuid?.let {
                    aktivitetskravService.getAktivitetskrav(uuid = it)
                        ?: throw IllegalArgumentException("Failed to create aktivitetskrav: previous aktivitetskrav not found")
                }
                val createdAktivitetskrav =
                    aktivitetskravService.createAktivitetskrav(personIdent, previousAktivitetskrav)

                call.respond(
                    HttpStatusCode.Created,
                    AktivitetskravResponseDTO.from(createdAktivitetskrav)
                )
            }
        }
        post("/{$aktivitetskravParam}$vurderAktivitetskravPath") {
            call.checkVeilederTilgang(
                action = API_ACTION,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
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
                    document = requestDTO.document ?: emptyList(),
                    callId = call.getCallId(),
                )

                call.respond(HttpStatusCode.OK)
            }
        }
        post("/{$aktivitetskravParam}$forhandsvarselPath") {
            call.checkVeilederTilgang(
                action = API_ACTION,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val aktivitetskravUUID = UUID.fromString(call.parameters[aktivitetskravParam])
                val requestDTO = call.receive<ForhandsvarselDTO>()
                if (requestDTO.document.isEmpty()) {
                    throw IllegalArgumentException("Forhandsvarsel can't have an empty document")
                }

                val aktivitetskrav =
                    aktivitetskravService.getAktivitetskrav(uuid = aktivitetskravUUID)
                        ?: throw IllegalArgumentException("Failed to create forhandsvarsel: aktivitetskrav not found")

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
        post("/get-vurderinger") {
            val token = call.getBearerHeader()
                ?: throw IllegalArgumentException("Failed to get vurderinger for personer. No Authorization header supplied.")
            val requestBody = call.receive<GetVurderingerRequestBody>()
            val personidenter = requestBody.personidenter.map { PersonIdent(it) }

            val personerVeilederHasAccessTo = veilederTilgangskontrollClient.veilederPersonerAccess(
                personidenter = personidenter,
                token = token,
                callId = call.getCallId(),
            )

            val aktivitetskravList = if (personerVeilederHasAccessTo.isNullOrEmpty()) {
                emptyList()
            } else {
                aktivitetskravService.getAktivitetskravForPersons(
                    personidenter = personerVeilederHasAccessTo,
                )
            }

            if (aktivitetskravList.isEmpty()) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                val responseDTO = aktivitetskravList.map { AktivitetskravResponseDTO.from(it) }
                call.respond(responseDTO)
            }
        }
    }
}

private fun ApplicationCall.personIdent(): PersonIdent = this.getPersonIdent()
    ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")
