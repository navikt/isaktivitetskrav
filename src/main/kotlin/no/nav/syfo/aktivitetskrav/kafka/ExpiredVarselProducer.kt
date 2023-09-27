package no.nav.syfo.aktivitetskrav.kafka

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class ExpiredVarselProducer(private val producer: KafkaProducer<String, ExpiredVarsel>) {

    fun publishExpiredVarsel(expiredVarselOppgave: ExpiredVarsel) {
        try {
            producer.send(
                ProducerRecord(
                    TOPIC,
                    UUID.randomUUID().toString(),
                    expiredVarselOppgave,
                )
            ).get()
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to publish expired varsel: ${e.message}")
            throw e
        }
    }

    companion object {
        private const val TOPIC = "teamsykefravr.aktivitetskrav-expired-varsel"
        private val log = LoggerFactory.getLogger(ExpiredVarselProducer::class.java)
    }
}
