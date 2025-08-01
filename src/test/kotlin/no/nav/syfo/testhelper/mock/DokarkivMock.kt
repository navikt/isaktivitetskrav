package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.infrastructure.client.dokarkiv.model.JournalpostRequest
import no.nav.syfo.infrastructure.client.dokarkiv.model.JournalpostResponse
import no.nav.syfo.testhelper.UserConstants

val response = JournalpostResponse(
    journalpostId = 1,
    journalpostferdigstilt = true,
    journalstatus = "status",
)
val conflictResponse = JournalpostResponse(
    journalpostId = 2,
    journalpostferdigstilt = true,
    journalstatus = "conflict",
)

suspend fun MockRequestHandleScope.dokarkivMockResponse(request: HttpRequestData): HttpResponseData {
    val journalpostRequest = request.receiveBody<JournalpostRequest>()
    val eksternReferanseId = journalpostRequest.eksternReferanseId

    return when (eksternReferanseId) {
        UserConstants.EXISTING_EKSTERN_REFERANSE_UUID.toString() -> respond(conflictResponse, HttpStatusCode.Conflict)
        else -> respond(response)
    }
}
