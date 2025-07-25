package no.nav.syfo.testhelper

import no.nav.syfo.api.cache.ValkeyConfig
import no.nav.syfo.api.cache.ValkeyStore
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.Protocol
import redis.embedded.RedisServer

fun testValkeyServer(
    valkeyConfig: ValkeyConfig,
): RedisServer = RedisServer.builder()
    .port(valkeyConfig.port)
    .setting("requirepass ${valkeyConfig.valkeyPassword}")
    .setting("maxmemory 1024M")
    .build()

fun testValkeyCache(valkeyConfig: ValkeyConfig): ValkeyStore = ValkeyStore(
    JedisPool(
        JedisPoolConfig(),
        valkeyConfig.host,
        valkeyConfig.port,
        Protocol.DEFAULT_TIMEOUT,
        valkeyConfig.valkeyPassword
    )
)
