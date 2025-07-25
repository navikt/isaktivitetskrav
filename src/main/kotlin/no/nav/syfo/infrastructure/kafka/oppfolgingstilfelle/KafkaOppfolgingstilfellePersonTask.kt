package no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.infrastructure.kafka.kafkaAivenConsumerConfig
import no.nav.syfo.infrastructure.kafka.launchKafkaTask
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Deserializer

const val OPPFOLGINGSTILFELLE_PERSON_TOPIC =
    "teamsykefravr.isoppfolgingstilfelle-oppfolgingstilfelle-person"

fun launchKafkaTaskOppfolgingstilfellePerson(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    kafkaOppfolgingstilfellePersonService: KafkaOppfolgingstilfellePersonService,
) {
    val consumerProperties = kafkaAivenConsumerConfig<KafkaOppfolgingstilfellePersonDeserializer>(
        kafkaEnvironment = kafkaEnvironment,
    )
    consumerProperties.apply {
        this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "100"
    }

    launchKafkaTask(
        applicationState = applicationState,
        topic = OPPFOLGINGSTILFELLE_PERSON_TOPIC,
        consumerProperties = consumerProperties,
        kafkaConsumerService = kafkaOppfolgingstilfellePersonService,
    )
}

class KafkaOppfolgingstilfellePersonDeserializer : Deserializer<KafkaOppfolgingstilfellePerson> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): KafkaOppfolgingstilfellePerson =
        mapper.readValue(data, KafkaOppfolgingstilfellePerson::class.java)
}
