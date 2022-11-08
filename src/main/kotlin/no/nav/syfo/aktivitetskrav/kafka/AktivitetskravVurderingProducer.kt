package no.nav.syfo.aktivitetskrav.kafka

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
import no.nav.syfo.aktivitetskrav.domain.toKafkaAktivitetskravVurdering
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class AktivitetskravVurderingProducer(
    private val kafkaProducerAktivitetskravVurdering: KafkaProducer<String, KafkaAktivitetskravVurdering>,
) {
    fun sendAktivitetskravVurdering(
        aktivitetskravVurdering: AktivitetskravVurdering,
    ) {
        val kafkaAktivitetskravVurdering = aktivitetskravVurdering.toKafkaAktivitetskravVurdering()
        val key = UUID.nameUUIDFromBytes(kafkaAktivitetskravVurdering.personIdent.toByteArray()).toString()
        try {
            kafkaProducerAktivitetskravVurdering.send(
                ProducerRecord(
                    AKTIVITETSKRAV_VURDERING_TOPIC,
                    key,
                    kafkaAktivitetskravVurdering,
                )
            ).get()
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send KafkaAktivitetskravVurdering with id {}: ${e.message}",
                key
            )
            throw e
        }
    }

    companion object {
        const val AKTIVITETSKRAV_VURDERING_TOPIC = "teamsykefravr.aktivitetskrav-vurdering"
        private val log = LoggerFactory.getLogger(AktivitetskravVurderingProducer::class.java)
    }
}
