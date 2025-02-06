package no.nav.syfo.application.cache

import java.net.URI

class ValkeyConfig(
    val valkeyUri: URI,
    val valkeyDB: Int,
    val valkeyUsername: String,
    val valkeyPassword: String,
    val ssl: Boolean = true
) {
    val host: String = valkeyUri.host
    val port: Int = valkeyUri.port
}
