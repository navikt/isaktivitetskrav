package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.testhelper.UserConstants

fun MockRequestHandleScope.pdfGenClientMockResponse(request: HttpRequestData): HttpResponseData {
    val requestUrl = request.url.encodedPath

    return when {
        requestUrl.endsWith("/forhandsvarsel-til-innbygger-om-stans-av-sykepenger") -> {
            respond(content = UserConstants.PDF_FORHANDSVARSEL)
        }
        requestUrl.endsWith("/innstilling-om-stans-av-sykepenger") -> {
            respond(content = UserConstants.PDF_STANS)
        }
        requestUrl.endsWith("/aktivitetskrav-vurdering") -> {
            respond(content = UserConstants.PDF_VURDERING)
        }

        else -> error("Unhandled pdf ${request.url.encodedPath}")
    }
}
