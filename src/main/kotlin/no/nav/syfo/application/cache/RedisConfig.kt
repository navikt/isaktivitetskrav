package no.nav.syfo.application.cache

import java.net.URI

class RedisConfig(
    val redisUri: URI,
    val redisDB: Int,
    val redisUsername: String,
    val redisPassword: String,
    val ssl: Boolean = true
) {
    val host: String = redisUri.host
    val port: Int = redisUri.port
}
