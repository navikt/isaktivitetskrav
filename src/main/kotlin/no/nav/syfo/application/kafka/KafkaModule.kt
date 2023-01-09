package no.nav.syfo.application.kafka

import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.identhendelse.kafka.KafkaIdenthendelseService
import no.nav.syfo.identhendelse.kafka.launchKafkaTaskIdenthendelse
import no.nav.syfo.oppfolgingstilfelle.kafka.KafkaOppfolgingstilfellePersonService
import no.nav.syfo.oppfolgingstilfelle.kafka.launchKafkaTaskOppfolgingstilfellePerson

fun launchKafkaModule(
    applicationState: ApplicationState,
    environment: Environment,
    database: DatabaseInterface,
    aktivitetskravService: AktivitetskravService,
) {
    if (environment.kafkaOppfolgingstilfellePersonProcessingEnabled) {
        val kafkaOppfolgingstilfellePersonService = KafkaOppfolgingstilfellePersonService(
            database = database,
            aktivitetskravService = aktivitetskravService,
        )
        launchKafkaTaskOppfolgingstilfellePerson(
            applicationState = applicationState,
            kafkaEnvironment = environment.kafka,
            kafkaOppfolgingstilfellePersonService = kafkaOppfolgingstilfellePersonService,
        )
    }
    if (environment.toggleKafkaConsumerIdenthendelseEnabled) {
        val kafkaIdenthendelseService = KafkaIdenthendelseService()
        launchKafkaTaskIdenthendelse(
            applicationState = applicationState,
            kafkaEnvironment = environment.kafka,
            kafkaIdenthendelseService = kafkaIdenthendelseService,
        )
    }
}
