package no.nav.syfo.aktivitetskrav.kafka

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.aktivitetskrav.kafka.domain.ExpiredVarsel
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class ExpiredVarselProducer(private val producer: KafkaProducer<String, ExpiredVarsel>) {

    suspend fun publishExpiredVarsel(expiredVarsel: ExpiredVarsel) {
        try {
            val record = ProducerRecord(TOPIC, UUID.randomUUID().toString(), expiredVarsel)
            withContext(Dispatchers.IO) {
                producer.send(record).get()
            }
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
