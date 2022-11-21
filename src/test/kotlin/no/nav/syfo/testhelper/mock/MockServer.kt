package no.nav.syfo.testhelper.mock

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.api.installContentNegotiation

abstract class MockServer {
    val server = embeddedServer(
        factory = Netty,
        port = 0,
    ) {
        installContentNegotiation()
        install(Routing, routingConfiguration)
    }

    abstract val name: String
    abstract val routingConfiguration: Routing.() -> Unit
    fun url(): String {
        val port = runBlocking { server.resolvedConnectors().first().port }
        return "http://localhost:$port"
    }
}
