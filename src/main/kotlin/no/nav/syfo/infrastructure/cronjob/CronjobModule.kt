package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.application.*
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.client.leaderelection.LeaderPodClient
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.database.DatabaseInterface

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

    val dokarkivClient = DokarkivClient(
        azureAdClient = azureAdClient,
        dokarkivEnvironment = environment.clients.dokarkiv,
    )
    val journalforAktivitetskravVarselCronjob = JournalforAktivitetskravVarselCronjob(
        aktivitetskravVarselService = aktivitetskravVarselService,
        dokarkivClient = dokarkivClient,
        pdlClient = pdlClient,
        isJournalforingRetryEnabled = environment.isJournalforingRetryEnabled,
    )
    cronjobs.add(journalforAktivitetskravVarselCronjob)

    val publiserAktivitetskravVarselCronjob = PubliserAktivitetskravVarselCronjob(
        aktivitetskravVarselService = aktivitetskravVarselService,
    )
    cronjobs.add(publiserAktivitetskravVarselCronjob)

    if (environment.outdatedCronJobEnabled) {
        val outdatedAktivitetskravCronjob = OutdatedAktivitetskravCronjob(
            outdatedCutoffMonths = environment.outdatedCutoffMonths,
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
