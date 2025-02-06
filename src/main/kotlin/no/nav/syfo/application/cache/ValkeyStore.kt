package no.nav.syfo.application.cache

import no.nav.syfo.util.configuredJacksonMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.exceptions.JedisConnectionException

class ValkeyStore(private val jedisPool: JedisPool) {

    private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.application.cache")
    val mapper = configuredJacksonMapper()

    inline fun <reified T> getObject(key: String): T? {
        val value = get(key)
        return if (value != null) mapper.readValue(value, T::class.java) else null
    }

    inline fun <reified T> getListObject(key: String): List<T>? {
        val value = get(key)
        return if (value != null) {
            mapper.readValue(value, mapper.typeFactory.constructCollectionType(ArrayList::class.java, T::class.java))
        } else null
    }

    inline fun <reified T> getSetObject(key: String): Set<T>? {
        val value = get(key)
        return if (value != null) {
            mapper.readValue(value, mapper.typeFactory.constructCollectionType(HashSet::class.java, T::class.java))
        } else null
    }

    fun get(key: String): String? {
        try {
            jedisPool.resource.use { jedis -> return jedis.get(key) }
        } catch (e: JedisConnectionException) {
            log.warn("Got connection error when fetching from redis! Continuing without cached value", e)
            return null
        }
    }

    fun <T> setObject(key: String, value: T, expireSeconds: Long) {
        set(key, mapper.writeValueAsString(value), expireSeconds)
    }

    fun set(key: String, value: String, expireSeconds: Long) {
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
