package no.nav.syfo.client.wellknown

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.httpClientProxy

fun getWellKnown(
    wellKnownUrl: String,
): WellKnown = runBlocking {
    httpClientProxy().use { client ->
        client.get(wellKnownUrl).body<WellKnownDTO>().toWellKnown()
    }
}
