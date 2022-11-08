package no.nav.syfo.application.kafka

import no.nav.syfo.aktivitetskrav.AktivitetskravVurderingService
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.aktivitetskrav.kafka.KafkaAktivitetskravVurderingSerializer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingstilfelle.kafka.KafkaOppfolgingstilfellePersonService
import no.nav.syfo.oppfolgingstilfelle.kafka.launchKafkaTaskOppfolgingstilfellePerson
import org.apache.kafka.clients.producer.KafkaProducer

fun launchKafkaModule(
    applicationState: ApplicationState,
    environment: Environment,
    database: DatabaseInterface,
) {
    if (environment.kafkaOppfolgingstilfellePersonProcessingEnabled) {
        val kafkaEnvironment = environment.kafka
        val aktivitetskravVurderingProducer = AktivitetskravVurderingProducer(
            kafkaProducerAktivitetskravVurdering = KafkaProducer(
                kafkaAivenProducerConfig<KafkaAktivitetskravVurderingSerializer>(
                    kafkaEnvironment = kafkaEnvironment,
                )
            )
        )
        val aktivitetskravVurderingService = AktivitetskravVurderingService(
            aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
        )
        val kafkaOppfolgingstilfellePersonService = KafkaOppfolgingstilfellePersonService(
            database = database,
            aktivitetskravVurderingService = aktivitetskravVurderingService,
        )
        launchKafkaTaskOppfolgingstilfellePerson(
            applicationState = applicationState,
            kafkaEnvironment = kafkaEnvironment,
            kafkaOppfolgingstilfellePersonService = kafkaOppfolgingstilfellePersonService,
        )
    }
}
