package no.nav.syfo.api.cache

import no.nav.syfo.util.configuredJacksonMapper

interface IValkeyStore {
    fun get(key: String): String?
    fun set(key: String, value: String, expireSeconds: Long)
}

@PublishedApi
internal val cacheMapper = configuredJacksonMapper()

inline fun <reified T> IValkeyStore.getObject(key: String): T? {
    val value = get(key)
    return if (value != null) cacheMapper.readValue(value, T::class.java) else null
}

inline fun <reified T> IValkeyStore.getListObject(key: String): List<T>? {
    val value = get(key)
    return if (value != null) {
        cacheMapper.readValue(value, cacheMapper.typeFactory.constructCollectionType(ArrayList::class.java, T::class.java))
    } else null
}

inline fun <reified T> IValkeyStore.getSetObject(key: String): Set<T>? {
    val value = get(key)
    return if (value != null) {
        cacheMapper.readValue(value, cacheMapper.typeFactory.constructCollectionType(HashSet::class.java, T::class.java))
    } else null
}

fun <T> IValkeyStore.setObject(key: String, value: T, expireSeconds: Long) {
    set(key, cacheMapper.writeValueAsString(value), expireSeconds)
}
