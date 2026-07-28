package no.nav.syfo.api.cache

interface IValkeyStore {
    fun get(key: String): String?
    fun set(key: String, value: String, expireSeconds: Long)
}
