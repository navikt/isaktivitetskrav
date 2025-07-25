package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.domain.Aktivitetskrav
import no.nav.syfo.domain.AktivitetskravStatus
import no.nav.syfo.infrastructure.kafka.model.AktivitetskravVurderingRecord
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serializer
import org.slf4j.LoggerFactory
import java.util.*

class AktivitetskravVurderingProducer(
    private val producer: KafkaProducer<String, AktivitetskravVurderingRecord>,
) {
    fun sendAktivitetskravVurdering(
        aktivitetskrav: Aktivitetskrav,
        previousAktivitetskravUuid: UUID? = null,
    ) {
        val aktivitetskravVurderingRecord =
            AktivitetskravVurderingRecord.from(
                aktivitetskrav,
                previousAktivitetskravUuid = previousAktivitetskravUuid
            )
        val key = UUID.nameUUIDFromBytes(aktivitetskravVurderingRecord.personIdent.toByteArray()).toString()
        try {
            producer.send(
                ProducerRecord(
                    AKTIVITETSKRAV_VURDERING_TOPIC,
                    key,
                    aktivitetskravVurderingRecord,
                )
            ).get()
            log.info(
                "Aktivitetskravvurdering sent with status: {} for uuid: {}",
                aktivitetskravVurderingRecord.status,
                aktivitetskravVurderingRecord.uuid
            )
            when (aktivitetskravVurderingRecord.status) {
                AktivitetskravStatus.NY_VURDERING.name -> COUNT_NY_VURDERING.increment()
                AktivitetskravStatus.AVVENT.name -> COUNT_AVVENT.increment()
                AktivitetskravStatus.UNNTAK.name -> COUNT_UNNTAK.increment()
                AktivitetskravStatus.OPPFYLT.name -> COUNT_OPPFYLT.increment()
                AktivitetskravStatus.FORHANDSVARSEL.name -> COUNT_FORHANDSVARSEL.increment()
                AktivitetskravStatus.INNSTILLING_OM_STANS.name -> COUNT_STANS.increment()
                AktivitetskravStatus.IKKE_OPPFYLT.name -> COUNT_IKKE_OPPFYLT.increment()
                AktivitetskravStatus.IKKE_AKTUELL.name -> COUNT_IKKE_AKTUELL.increment()
            }
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send KafkaAktivitetskravVurdering with key: {}, aktivitetskravUUID: {}, vurderingUUID: {}, ${e.message}",
                key,
                aktivitetskravVurderingRecord.uuid,
                aktivitetskravVurderingRecord.sisteVurderingUuid,
            )
            throw e
        }
    }

    companion object {
        const val AKTIVITETSKRAV_VURDERING_TOPIC = "teamsykefravr.aktivitetskrav-vurdering"
        private val log = LoggerFactory.getLogger(AktivitetskravVurderingProducer::class.java)
    }
}

class aktivitetskravVurderingRecordSerializer : Serializer<AktivitetskravVurderingRecord> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: AktivitetskravVurderingRecord?): ByteArray =
        mapper.writeValueAsBytes(data)
}

fun aktivitetskravVurderingProducerConfig(kafkaEnvironment: KafkaEnvironment) =
    kafkaAivenProducerConfig<aktivitetskravVurderingRecordSerializer>(kafkaEnvironment).apply {
        this[ProducerConfig.LINGER_MS_CONFIG] = "1000"
    }
