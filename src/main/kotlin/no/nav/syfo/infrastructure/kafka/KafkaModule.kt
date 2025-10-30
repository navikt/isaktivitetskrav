package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.application.AktivitetskravService
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.IdenthendelseService
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.kafka.identhendelse.KafkaIdenthendelseService
import no.nav.syfo.infrastructure.kafka.identhendelse.launchKafkaTaskIdenthendelse
import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.OppfolgingstilfellePersonConsumer
import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.launchKafkaTaskOppfolgingstilfellePerson

fun launchKafkaModule(
    applicationState: ApplicationState,
    environment: Environment,
    pdlClient: PdlClient,
    aktivitetskravService: AktivitetskravService,
    aktivitetskravRepository: AktivitetskravRepository,
) {
    val oppfolgingstilfellePersonConsumer = OppfolgingstilfellePersonConsumer(
        aktivitetskravService = aktivitetskravService,
        arenaCutoff = environment.arenaCutoff,
    )
    launchKafkaTaskOppfolgingstilfellePerson(
        applicationState = applicationState,
        kafkaEnvironment = environment.kafka,
        oppfolgingstilfellePersonConsumer = oppfolgingstilfellePersonConsumer,
    )

    val identhendelseService = IdenthendelseService(
        aktivitetskravRepository = aktivitetskravRepository,
        pdlClient = pdlClient
    )
    val kafkaIdenthendelseService = KafkaIdenthendelseService(identhendelseService = identhendelseService)
    launchKafkaTaskIdenthendelse(
        applicationState = applicationState,
        kafkaEnvironment = environment.kafka,
        kafkaIdenthendelseService = kafkaIdenthendelseService,
    )
}
