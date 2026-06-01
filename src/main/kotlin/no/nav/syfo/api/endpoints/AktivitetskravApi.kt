package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.dto.AktivitetskravResponseDTO
import no.nav.syfo.api.dto.AktivitetskravVurderingRequestDTO
import no.nav.syfo.api.dto.AktivitetskravVurderingResponseDTO
import no.nav.syfo.api.dto.ForhandsvarselDTO
import no.nav.syfo.api.dto.GetAktivitetskravForPersonsResponseDTO
import no.nav.syfo.api.dto.GetVurderingerRequestBody
import no.nav.syfo.api.dto.NewAktivitetskravDTO
import no.nav.syfo.application.AktivitetskravService
import no.nav.syfo.application.AktivitetskravVarselService
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.tilgangskontroll.ktor.checkPersonAndSyfoTilgang
import no.nav.syfo.common.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.common.util.ktor.bearerTokenOrNull
import no.nav.syfo.common.util.ktor.callId
import no.nav.syfo.common.util.ktor.navIdent
import no.nav.syfo.common.util.ktor.personIdentOrNull
import no.nav.syfo.domain.Aktivitetskrav
import no.nav.syfo.domain.PersonIdent
import java.util.*

const val aktivitetskravApiBasePath = "/api/internad/v1/aktivitetskrav"
const val aktivitetskravApiPersonidentPath = "/personident"
const val aktivitetskravApiHistorikkPath = "/historikk"
const val aktivitetskravParam = "aktivitetskravUuid"
const val vurderAktivitetskravPath = "/vurder"
const val forhandsvarselPath = "/forhandsvarsel"

private const val API_ACTION = "access aktivitetskrav for person"

fun Route.registerAktivitetskravApi(
    tilgangskontrollClient: TilgangskontrollClient,
    aktivitetskravService: AktivitetskravService,
    aktivitetskravVarselService: AktivitetskravVarselService,
) {
    route(aktivitetskravApiBasePath) {
        get(aktivitetskravApiPersonidentPath) {
            checkPersonAndSyfoTilgang(
                action = API_ACTION,
                tilgangskontrollClient = tilgangskontrollClient,
            ) {
                val personIdent = call.getPersonIdent()
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
            checkPersonAndSyfoTilgang(
                action = API_ACTION,
                tilgangskontrollClient = tilgangskontrollClient,
            ) {
                val personIdent = call.getPersonIdent()
                call.respond(aktivitetskravService.getAktivitetskravHistorikk(personIdent))
            }
        }
        post {
            checkPersonAndSyfoTilgang(
                action = API_ACTION,
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = true,
            ) {
                val personIdent = call.getPersonIdent()
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
            checkPersonAndSyfoTilgang(
                action = API_ACTION,
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = true,
            ) {
                val personIdent = call.getPersonIdent()
                val aktivitetskravUUID = UUID.fromString(call.parameters[aktivitetskravParam])
                val requestDTO = call.receive<AktivitetskravVurderingRequestDTO>()

                val aktivitetskrav =
                    aktivitetskravService.getAktivitetskrav(uuid = aktivitetskravUUID)
                        ?: throw IllegalArgumentException("Failed to vurdere aktivitetskrav: aktivitetskrav not found")

                if (aktivitetskrav.personIdent != personIdent) {
                    throw IllegalArgumentException("Failed to vurdere aktivitetskrav: personIdent on aktivitetskrav differs from request")
                }

                val aktivitetskravVurdering = requestDTO.toAktivitetskravVurdering(createdByIdent = call.navIdent)
                aktivitetskravService.vurderAktivitetskrav(
                    aktivitetskrav = aktivitetskrav,
                    aktivitetskravVurdering = aktivitetskravVurdering,
                    document = requestDTO.document ?: emptyList(),
                    callId = call.callId,
                )

                call.respond(HttpStatusCode.OK)
            }
        }

        post("/{$aktivitetskravParam}$forhandsvarselPath") {
            checkPersonAndSyfoTilgang(
                action = API_ACTION,
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = true,
            ) {
                val aktivitetskravUUID = UUID.fromString(call.parameters[aktivitetskravParam])
                val requestDTO = call.receive<ForhandsvarselDTO>()

                val aktivitetskrav =
                    aktivitetskravService.getAktivitetskrav(uuid = aktivitetskravUUID)
                        ?: throw IllegalArgumentException("Failed to create forhandsvarsel: aktivitetskrav not found")

                val forhandsvarsel = aktivitetskravVarselService.sendForhandsvarsel(
                    aktivitetskrav = aktivitetskrav,
                    veilederIdent = call.navIdent,
                    personIdent = call.getPersonIdent(),
                    forhandsvarselDTO = requestDTO,
                    callId = call.callId,
                )
                call.respond(HttpStatusCode.Created, forhandsvarsel)
            }
        }

        post("/get-vurderinger") {
            val token = call.bearerTokenOrNull
                ?: throw IllegalArgumentException("Failed to get vurderinger for personer. No Authorization header supplied.")
            val requestBody = call.receive<GetVurderingerRequestBody>()
            val personIdenter = requestBody.personidenter.map { PersonIdent(it) }

            val personerUserHasAccessTo = tilgangskontrollClient.personsUserHasAccessTo(
                personIdenter = personIdenter.map { it.value },
                token = token,
                callId = call.callId,
            )?.map { PersonIdent(it) }

            val aktivitetskravvurderinger: Map<PersonIdent, Aktivitetskrav> =
                if (personerUserHasAccessTo.isNullOrEmpty()) {
                    emptyMap()
                } else {
                    aktivitetskravService.getAktivitetskravForPersons(
                        personidenter = personerUserHasAccessTo,
                    )
                }

            if (aktivitetskravvurderinger.isEmpty()) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                val responseDTO =
                    GetAktivitetskravForPersonsResponseDTO(
                        aktivitetskravvurderinger = aktivitetskravvurderinger.map {
                            it.key.value to AktivitetskravResponseDTO.from(it.value)
                        }.toMap()
                    )
                call.respond(responseDTO)
            }
        }
    }
}

private fun ApplicationCall.getPersonIdent(): PersonIdent = this.personIdentOrNull
    ?.let { PersonIdent(it) }
    ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")
