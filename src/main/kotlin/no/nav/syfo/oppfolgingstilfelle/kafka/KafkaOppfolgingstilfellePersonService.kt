package no.nav.syfo.oppfolgingstilfelle.kafka

import no.nav.syfo.application.kafka.KafkaConsumerService
import org.apache.kafka.clients.consumer.*
import java.time.Duration

class KafkaOppfolgingstilfellePersonService : KafkaConsumerService<KafkaOppfolgingstilfellePerson> {

    override val pollDurationInMillis: Long = 1000

    override fun pollAndProcessRecords(kafkaConsumer: KafkaConsumer<String, KafkaOppfolgingstilfellePerson>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(pollDurationInMillis))
        if (records.count() > 0) {
            // TODO: Process records
            kafkaConsumer.commitSync()
        }
    }
}
