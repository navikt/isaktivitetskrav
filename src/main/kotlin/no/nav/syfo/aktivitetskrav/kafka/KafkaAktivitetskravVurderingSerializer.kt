package no.nav.syfo.aktivitetskrav.kafka

import no.nav.syfo.aktivitetskrav.kafka.domain.KafkaAktivitetskravVurdering
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer

class KafkaAktivitetskravVurderingSerializer : Serializer<KafkaAktivitetskravVurdering> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: KafkaAktivitetskravVurdering?): ByteArray =
        mapper.writeValueAsBytes(data)
}
