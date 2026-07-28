package no.nav.syfo.api.cache

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.exceptions.JedisConnectionException

class ValkeyStore(private val jedisPool: JedisPool) : IValkeyStore {

    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.application.cache")

    override fun get(key: String): String? {
        try {
            jedisPool.resource.use { jedis -> return jedis.get(key) }
        } catch (e: JedisConnectionException) {
            log.warn("Got connection error when fetching from redis! Continuing without cached value", e)
            return null
        }
    }

    override fun set(key: String, value: String, expireSeconds: Long) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.setex(
                    key,
                    expireSeconds,
                    value,
                )
            }
        } catch (e: JedisConnectionException) {
            log.warn("Got connection error when storing in redis! Continue without caching", e)
        }
    }
}
