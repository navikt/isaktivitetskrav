package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.AktivitetskravVarselService
import no.nav.syfo.aktivitetskrav.cronjob.*
import no.nav.syfo.application.*
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.leaderelection.LeaderPodClient
import no.nav.syfo.infrastructure.pdl.PdlClient

fun launchCronjobModule(
    applicationState: ApplicationState,
    environment: Environment,
    database: DatabaseInterface,
    aktivitetskravService: AktivitetskravService,
    aktivitetskravVarselService: AktivitetskravVarselService,
    pdlClient: PdlClient,
    azureAdClient: AzureAdClient,
) {
    val leaderPodClient = LeaderPodClient(
        electorPath = environment.electorPath
    )
    val cronjobRunner = CronjobRunner(
        applicationState = applicationState,
        leaderPodClient = leaderPodClient
    )
    val cronjobs = mutableListOf<Cronjob>()

    if (environment.automatiskOppfyltCronJobEnabled) {
        val aktivitetskravAutomatiskOppfyltCronjob = AktivitetskravAutomatiskOppfyltCronjob(
            database = database,
            aktivitetskravService = aktivitetskravService,
        )
        cronjobs.add(aktivitetskravAutomatiskOppfyltCronjob)
    }
    if (environment.nyCronjobEnabled) {
        val aktivitetskravNyCronjob = AktivitetskravNyCronjob(
            database = database,
            aktivitetskravService = aktivitetskravService,
        )
        cronjobs.add(aktivitetskravNyCronjob)
    }

    val dokarkivClient = DokarkivClient(
        azureAdClient = azureAdClient,
        dokarkivEnvironment = environment.clients.dokarkiv,
    )
    val journalforAktivitetskravVarselCronjob = JournalforAktivitetskravVarselCronjob(
        aktivitetskravVarselService = aktivitetskravVarselService,
        dokarkivClient = dokarkivClient,
        pdlClient = pdlClient
    )
    // cronjobs.add(journalforAktivitetskravVarselCronjob)

    val publiserAktivitetskravVarselCronjob = PubliserAktivitetskravVarselCronjob(
        aktivitetskravVarselService = aktivitetskravVarselService,
    )
    cronjobs.add(publiserAktivitetskravVarselCronjob)

    if (environment.outdatedCronJobEnabled) {
        val outdatedAktivitetskravCronjob = OutdatedAktivitetskravCronjob(
            outdatedCutoff = environment.outdatedCutoff,
            aktivitetskravService = aktivitetskravService,
        )
        cronjobs.add(outdatedAktivitetskravCronjob)
    }
    cronjobs.forEach {
        launchBackgroundTask(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(cronjob = it)
        }
    }
}
