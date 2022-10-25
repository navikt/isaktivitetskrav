package no.nav.syfo.application.kafka

import org.apache.kafka.clients.consumer.KafkaConsumer

interface KafkaConsumerService<ConsumerRecordValue> {
    val pollDurationInMillis: Long
    fun pollAndProcessRecords(
        kafkaConsumer: KafkaConsumer<String, ConsumerRecordValue>,
    )
}
