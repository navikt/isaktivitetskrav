package no.nav.syfo.testhelper

import no.nav.syfo.application.cache.RedisConfig
import no.nav.syfo.application.cache.RedisStore
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.Protocol
import redis.embedded.RedisServer

fun testRedisServer(
    redisConfig: RedisConfig,
): RedisServer = RedisServer.builder()
    .port(redisConfig.port)
    .setting("requirepass ${redisConfig.redisPassword}")
    .setting("maxmemory 1024M")
    .build()

fun testRedisCache(redisConfig: RedisConfig): RedisStore = RedisStore(
    JedisPool(
        JedisPoolConfig(),
        redisConfig.host,
        redisConfig.port,
        Protocol.DEFAULT_TIMEOUT,
        redisConfig.redisPassword
    )
)
