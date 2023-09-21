package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.client.pdfgen.ForhandsvarselPdfDTO
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.testhelper.UserConstants

suspend fun MockRequestHandleScope.pdfGenClientMockResponse(request: HttpRequestData): HttpResponseData {
    val requestUrl = request.url.encodedPath
    request.receiveBody<ForhandsvarselPdfDTO>()

    return when {
        requestUrl.endsWith(PdfGenClient.Companion.FORHANDSVARSEL_PATH) -> {
            respond(content = UserConstants.PDF_FORHANDSVARSEL)
        }

        else -> error("Unhandled pdf ${request.url.encodedPath}")
    }
}
