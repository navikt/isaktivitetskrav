package no.nav.syfo.client.pdfgen

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.micrometer.core.instrument.Counter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import org.slf4j.LoggerFactory

class PdfGenClient(
    private val httpClient: HttpClient = httpClientDefault(),
    val pdfGenBaseUrl: String,
) {

    suspend fun createForhandsvarselPdf(
        callId: String,
        documentComponents: List<DocumentComponentDTO>,
    ): ByteArray =
        getPdf(
            callId = callId,
            payload = documentComponents,
            pdfUrl = "$pdfGenBaseUrl$API_BASE_PATH$FORHANDSVARSEL_PATH",
        ) ?: throw RuntimeException("Failed to request pdf for forhandsvarsel, callId: $callId")

    private suspend fun getPdf(
        callId: String,
        payload: List<DocumentComponentDTO>,
        pdfUrl: String,
    ): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = httpClient.post(pdfUrl) {
                    header(NAV_CALL_ID_HEADER, callId)
                    accept(ContentType.Application.Json)
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
                Metrics.COUNT_CALL_PDFGEN_SUCCESS.increment()
                response.body()
            } catch (e: ClientRequestException) {
                handleUnexpectedResponseException(pdfUrl, e.response, callId)
            } catch (e: ServerResponseException) {
                handleUnexpectedResponseException(pdfUrl, e.response, callId)
            }
        }

    private fun handleUnexpectedResponseException(
        url: String,
        response: HttpResponse,
        callId: String,
    ): ByteArray? {
        log.error(
            "Error while requesting PDF from isaktivitetskravpdfgen with {}, {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            StructuredArguments.keyValue("url", url),
            StructuredArguments.keyValue("callId", callId)
        )
        Metrics.COUNT_CALL_PDFGEN_FAIL.increment()
        return null
    }

    companion object {
        private const val API_BASE_PATH = "/api/v1/genpdf/isaktivitetskrav"
        const val FORHANDSVARSEL_PATH = "/forhandsvarsel"

        private val log = LoggerFactory.getLogger(PdfGenClient::class.java)
    }
}

private object Metrics {
    private const val CALL_PDFGEN_BASE = "${METRICS_NS}_call_isaktivitetskravpdfgen"

    private const val CALL_PDFGEN_SUCCESS = "${CALL_PDFGEN_BASE}_success_count"
    private const val CALL_PDFGEN_FAIL = "${CALL_PDFGEN_BASE}_fail_count"

    val COUNT_CALL_PDFGEN_SUCCESS: Counter = Counter
        .builder(CALL_PDFGEN_SUCCESS)
        .description("Counts the number of successful calls to dialogmeldingpdfgen")
        .register(METRICS_REGISTRY)
    val COUNT_CALL_PDFGEN_FAIL: Counter = Counter
        .builder(CALL_PDFGEN_FAIL)
        .description("Counts the number of failed calls to dialogmeldingpdfgen")
        .register(METRICS_REGISTRY)
}
