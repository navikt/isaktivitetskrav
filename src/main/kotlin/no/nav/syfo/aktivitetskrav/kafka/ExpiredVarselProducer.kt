package no.nav.syfo.aktivitetskrav.kafka

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.slf4j.LoggerFactory
import java.util.*

class ExpiredVarselProducer(private val producer: KafkaProducer<String, ExpiredVarsel>) {

    suspend fun publishExpiredVarsel(expiredVarselOppgave: ExpiredVarsel): RecordMetadata =
        withContext(Dispatchers.IO) {
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