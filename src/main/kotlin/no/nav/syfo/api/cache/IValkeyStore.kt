package no.nav.syfo.api.cache

import no.nav.syfo.util.configuredJacksonMapper

interface IValkeyStore {
    fun get(key: String): String?
    fun set(key: String, value: String, expireSeconds: Long)
}
