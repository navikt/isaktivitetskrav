package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.infrastructure.kafka.domain.KafkaAktivitetskravVarsel
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

class KafkaAktivitetskravVarselSerializer : Serializer<KafkaAktivitetskravVarsel> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: KafkaAktivitetskravVarsel?): ByteArray =
        mapper.writeValueAsBytes(data)
}
