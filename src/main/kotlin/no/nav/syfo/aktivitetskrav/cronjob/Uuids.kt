package no.nav.syfo.aktivitetskrav.cronjob

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.syfo.util.configuredJacksonMapper
import java.util.*

data class UuidListe(val uuids: List<UUID>)

private val objectMapper: ObjectMapper = configuredJacksonMapper()

fun getUuids(fileName: String): List<UUID> = objectMapper.readValue(
    object {}.javaClass.getResource("/cronjob/$fileName")!!.readText(),
    UuidListe::class.java
).uuids
