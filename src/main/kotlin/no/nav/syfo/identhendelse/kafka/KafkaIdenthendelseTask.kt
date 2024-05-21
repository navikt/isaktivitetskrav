package no.nav.syfo.identhendelse.kafka

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.infrastructure.kafka.launchKafkaTask

const val PDL_AKTOR_TOPIC = "pdl.aktor-v2"

fun launchKafkaTaskIdenthendelse(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    kafkaIdenthendelseService: KafkaIdenthendelseService,
) {
    val consumerProperties = kafkaIdenthendelseConsumerConfig(kafkaEnvironment = kafkaEnvironment)
    launchKafkaTask(
        applicationState = applicationState,
        topic = PDL_AKTOR_TOPIC,
        consumerProperties = consumerProperties,
        kafkaConsumerService = kafkaIdenthendelseService,
    )
}
