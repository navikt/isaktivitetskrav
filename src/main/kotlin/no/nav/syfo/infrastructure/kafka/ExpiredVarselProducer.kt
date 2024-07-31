package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.infrastructure.kafka.domain.ExpiredVarsel
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.UUID

class ExpiredVarselProducer(private val producer: KafkaProducer<String, ExpiredVarsel>) {

    suspend fun publishExpiredVarsel(expiredVarsel: ExpiredVarsel) =
        try {
            val record = ProducerRecord(TOPIC, UUID.randomUUID().toString(), expiredVarsel)
            producer.send(record).also { it.get() }
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to publish expired varsel: ${e.message}")
            throw e
        }

    companion object {
        private const val TOPIC = "teamsykefravr.aktivitetskrav-expired-varsel"
        private val log = LoggerFactory.getLogger(ExpiredVarselProducer::class.java)
    }
}
