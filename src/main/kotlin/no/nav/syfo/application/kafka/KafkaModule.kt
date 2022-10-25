package no.nav.syfo.application.kafka

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.oppfolgingstilfelle.kafka.launchKafkaTaskOppfolgingstilfellePerson

fun launchKafkaModule(
    applicationState: ApplicationState,
    environment: Environment,
) {
    if (environment.kafkaOppfolgingstilfellePersonProcessingEnabled) {
        launchKafkaTaskOppfolgingstilfellePerson(
            applicationState = applicationState,
            kafkaEnvironment = environment.kafka
        )
    }
}
