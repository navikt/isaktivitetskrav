package no.nav.syfo.aktivitetskrav.kafka

import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.kafka.domain.KafkaAktivitetskravVurdering
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.application.kafka.kafkaAivenProducerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class AktivitetskravVurderingProducer(
    private val producer: KafkaProducer<String, KafkaAktivitetskravVurdering>,
) {
    fun sendAktivitetskravVurdering(
        aktivitetskrav: Aktivitetskrav,
        previousAktivitetskravUuid: UUID? = null,
    ) {
        val kafkaAktivitetskravVurdering =
            KafkaAktivitetskravVurdering.from(
                aktivitetskrav,
                previousAktivitetskravUuid = previousAktivitetskravUuid
            )
        val key = UUID.nameUUIDFromBytes(kafkaAktivitetskravVurdering.personIdent.toByteArray()).toString()
        try {
            producer.send(
                ProducerRecord(
                    AKTIVITETSKRAV_VURDERING_TOPIC,
                    key,
                    kafkaAktivitetskravVurdering,
                )
            ).get()
            log.info(
                "Aktivitetskravvurdering sent with status: {} for uuid: {}",
                kafkaAktivitetskravVurdering.status,
                kafkaAktivitetskravVurdering.uuid
            )
            when (kafkaAktivitetskravVurdering.status) {
                AktivitetskravStatus.NY_VURDERING.name -> COUNT_NY_VURDERING.increment()
                AktivitetskravStatus.AVVENT.name -> COUNT_AVVENT.increment()
                AktivitetskravStatus.UNNTAK.name -> COUNT_UNNTAK.increment()
                AktivitetskravStatus.OPPFYLT.name -> COUNT_OPPFYLT.increment()
                AktivitetskravStatus.FORHANDSVARSEL.name -> COUNT_FORHANDSVARSEL.increment()
                AktivitetskravStatus.STANS.name -> COUNT_STANS.increment()
                AktivitetskravStatus.IKKE_OPPFYLT.name -> COUNT_IKKE_OPPFYLT.increment()
                AktivitetskravStatus.IKKE_AKTUELL.name -> COUNT_IKKE_AKTUELL.increment()
            }
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

fun aktivitetskravVurderingProducerConfig(kafkaEnvironment: KafkaEnvironment) =
    kafkaAivenProducerConfig<KafkaAktivitetskravVurderingSerializer>(kafkaEnvironment).apply {
        this[ProducerConfig.LINGER_MS_CONFIG] = "1000"
    }
