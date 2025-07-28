package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.infrastructure.kafka.model.AktivitetskravVarselRecord
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

class KafkaAktivitetskravVarselSerializer : Serializer<AktivitetskravVarselRecord> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: AktivitetskravVarselRecord?): ByteArray =
        mapper.writeValueAsBytes(data)
}
