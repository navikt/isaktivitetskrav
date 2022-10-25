package no.nav.syfo.application.kafka

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.launchBackgroundTask
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

inline fun <reified ConsumerRecordValue> launchKafkaTask(
    applicationState: ApplicationState,
    topic: String,
    consumerProperties: Properties,
    kafkaConsumerService: KafkaConsumerService<ConsumerRecordValue>,
) {
    launchBackgroundTask(
        applicationState = applicationState
    ) {
        log.info("Setting up kafka consumer for ${ConsumerRecordValue::class.java.simpleName}")

        val kafkaConsumer = KafkaConsumer<String, ConsumerRecordValue>(consumerProperties)
        kafkaConsumer.subscribe(
            listOf(topic)
        )

        while (applicationState.ready) {
            kafkaConsumerService.pollAndProcessRecords(kafkaConsumer)
        }
    }
}
