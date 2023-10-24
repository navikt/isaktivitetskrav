package no.nav.syfo.aktivitetskrav.kafka

import no.nav.syfo.aktivitetskrav.kafka.domain.KafkaAktivitetskravVarsel
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class AktivitetskravVarselProducer(private val kafkaProducer: KafkaProducer<String, KafkaAktivitetskravVarsel>) {

    fun sendAktivitetskravVarsel(
        varsel: KafkaAktivitetskravVarsel,
    ) {
        val key = UUID.nameUUIDFromBytes(varsel.personIdent.toByteArray()).toString()
        try {
            kafkaProducer.send(
                ProducerRecord(
                    AKTIVITETSKRAV_VARSEL_TOPIC,
                    key,
                    varsel,
                )
            ).get()
            log.info(
                "Varsel with uuid: {} published",
                varsel.varselUuid
            )
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send KafkaAktivitetskravVarsel with id {}: ${e.message}",
                key
            )
            throw e
        }
    }

    companion object {
        const val AKTIVITETSKRAV_VARSEL_TOPIC = "teamsykefravr.aktivitetskrav-varsel"
        private val log = LoggerFactory.getLogger(AktivitetskravVarselProducer::class.java)
    }
}
