package no.nav.syfo.aktivitetskrav.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer


class ExpiredVarselSerializer : Serializer<ExpiredVarsel> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: ExpiredVarsel?): ByteArray =
        mapper.writeValueAsBytes(data)
}