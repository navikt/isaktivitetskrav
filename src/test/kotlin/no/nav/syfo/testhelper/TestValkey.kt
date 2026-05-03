package no.nav.syfo.testhelper

import no.nav.syfo.api.cache.ValkeyConfig
import no.nav.syfo.api.cache.ValkeyStore
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.Protocol

fun testValkeyServer(
    valkeyConfig: ValkeyConfig,
): GenericContainer<*> = GenericContainer<Nothing>(DockerImageName.parse("redis:7-alpine")).apply {
    withExposedPorts(6379)
    withCommand("redis-server", "--requirepass", valkeyConfig.valkeyPassword, "--maxmemory", "1024M")
}

fun testValkeyCache(container: GenericContainer<*>, valkeyConfig: ValkeyConfig): ValkeyStore = ValkeyStore(
    JedisPool(
        JedisPoolConfig(),
        container.host,
        container.getMappedPort(6379),
        Protocol.DEFAULT_TIMEOUT,
        valkeyConfig.valkeyPassword
    )
)
