package no.nav.syfo.identhendelse.kafka

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.kafka.KafkaConsumerService
import no.nav.syfo.identhendelse.IdenthendelseService
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

class KafkaIdenthendelseService(
    private val identhendelseService: IdenthendelseService,
) : KafkaConsumerService<GenericRecord> {
    override val pollDurationInMillis: Long = 1000

    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, GenericRecord>) {
        runBlocking {
            try {
                val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
                if (records.count() > 0) {
                    records.forEach { record ->
                        if (record.value() != null) {
                            identhendelseService.handle(record.value().toKafkaIdenthendelseDTO())
                        } else {
                            log.warn("Identhendelse: Value of ConsumerRecord from topic $PDL_AKTOR_TOPIC is null, probably due to a tombstone. Contact the owner of the topic if an error is suspected")
                            COUNT_KAFKA_CONSUMER_PDL_AKTOR_TOMBSTONE.increment()
                        }
                    }
                    kafkaConsumer.commitSync()
                }
            } catch (ex: Exception) {
                log.error("Error running kafka consumer for pdl-aktor, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry")
                kafkaConsumer.unsubscribe()
                delay(DELAY_ON_ERROR_SECONDS.seconds)
            }
        }
    }

    companion object {
        private const val DELAY_ON_ERROR_SECONDS = 60L
        private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.identhendelse")
    }
}

fun GenericRecord.toKafkaIdenthendelseDTO(): KafkaIdenthendelseDTO {
    val identifikatorer = (get("identifikatorer") as GenericData.Array<GenericRecord>).map {
        Identifikator(
            idnummer = it.get("idnummer").toString(),
            gjeldende = it.get("gjeldende").toString().toBoolean(),
            type = when (it.get("type").toString()) {
                "FOLKEREGISTERIDENT" -> IdentType.FOLKEREGISTERIDENT
                "AKTORID" -> IdentType.AKTORID
                "NPID" -> IdentType.NPID
                else -> throw IllegalStateException("Har mottatt ident med ukjent type")
            }
        )
    }
    return KafkaIdenthendelseDTO(identifikatorer)
}
