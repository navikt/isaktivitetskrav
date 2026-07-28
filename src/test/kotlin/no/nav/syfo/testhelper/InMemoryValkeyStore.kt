package no.nav.syfo.testhelper

import no.nav.syfo.api.cache.IValkeyStore
import java.util.concurrent.ConcurrentHashMap

class InMemoryValkeyStore : IValkeyStore {
    private val store = ConcurrentHashMap<String, String>()

    override fun get(key: String): String? = store[key]

    override fun set(key: String, value: String, expireSeconds: Long) {
        store[key] = value
    }

    fun clear() {
        store.clear()
    }
}
