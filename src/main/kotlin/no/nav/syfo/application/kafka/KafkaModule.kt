package no.nav.syfo.application.kafka

import no.nav.syfo.aktivitetskrav.AktivitetskravVurderingService
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingstilfelle.kafka.KafkaOppfolgingstilfellePersonService
import no.nav.syfo.oppfolgingstilfelle.kafka.launchKafkaTaskOppfolgingstilfellePerson

fun launchKafkaModule(
    applicationState: ApplicationState,
    environment: Environment,
    database: DatabaseInterface,
    aktivitetskravVurderingService: AktivitetskravVurderingService,
) {
    if (environment.kafkaOppfolgingstilfellePersonProcessingEnabled) {
        val kafkaOppfolgingstilfellePersonService = KafkaOppfolgingstilfellePersonService(
            database = database,
            aktivitetskravVurderingService = aktivitetskravVurderingService,
        )
        launchKafkaTaskOppfolgingstilfellePerson(
            applicationState = applicationState,
            kafkaEnvironment = environment.kafka,
            kafkaOppfolgingstilfellePersonService = kafkaOppfolgingstilfellePersonService,
        )
    }
}
