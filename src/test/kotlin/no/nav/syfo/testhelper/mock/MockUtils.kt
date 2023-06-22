package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.util.configuredJacksonMapper

val mapper = configuredJacksonMapper()

fun <T> MockRequestHandleScope.respond(body: T): HttpResponseData =
    respond(
        mapper.writeValueAsString(body),
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json")
    )

suspend inline fun <reified T> HttpRequestData.receiveBody(): T {
    return mapper.readValue(body.toByteArray(), T::class.java)
}
