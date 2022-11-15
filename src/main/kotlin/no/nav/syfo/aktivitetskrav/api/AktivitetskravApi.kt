package no.nav.syfo.aktivitetskrav.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

const val aktivitetskravApiBasePath = "/api/internad/v1/aktivitetskrav"
const val aktivitetskravApiPersonidentPath = "/personident"

fun Route.registerAktivitetskravApi() {
    route(aktivitetskravApiBasePath) {
        get(aktivitetskravApiPersonidentPath) {
            // TODO: Implement
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
