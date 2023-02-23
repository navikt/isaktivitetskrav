package no.nav.syfo.application.kafka

import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.identhendelse.IdenthendelseService
import no.nav.syfo.identhendelse.kafka.KafkaIdenthendelseService
import no.nav.syfo.identhendelse.kafka.launchKafkaTaskIdenthendelse
import no.nav.syfo.oppfolgingstilfelle.kafka.KafkaOppfolgingstilfellePersonService
import no.nav.syfo.oppfolgingstilfelle.kafka.launchKafkaTaskOppfolgingstilfellePerson

fun launchKafkaModule(
    applicationState: ApplicationState,
    environment: Environment,
    database: DatabaseInterface,
    azureAdClient: AzureAdClient,
    aktivitetskravService: AktivitetskravService,
) {
    val kafkaOppfolgingstilfellePersonService = KafkaOppfolgingstilfellePersonService(
        database = database,
        aktivitetskravService = aktivitetskravService,
        arenaCutoff = environment.arenaCutoff,
    )
    launchKafkaTaskOppfolgingstilfellePerson(
        applicationState = applicationState,
        kafkaEnvironment = environment.kafka,
        kafkaOppfolgingstilfellePersonService = kafkaOppfolgingstilfellePersonService,
    )

    val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        pdlEnvironment = environment.clients.pdl,
    )
    val identhendelseService = IdenthendelseService(database = database, pdlClient = pdlClient)
    val kafkaIdenthendelseService = KafkaIdenthendelseService(identhendelseService = identhendelseService)
    launchKafkaTaskIdenthendelse(
        applicationState = applicationState,
        kafkaEnvironment = environment.kafka,
        kafkaIdenthendelseService = kafkaIdenthendelseService,
    )
}
