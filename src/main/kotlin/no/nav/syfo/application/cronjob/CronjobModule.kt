package no.nav.syfo.application.cronjob

import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.cronjob.AktivitetskravAutomatiskOppfyltCronjob
import no.nav.syfo.aktivitetskrav.cronjob.AktivitetskravNyCronjob
import no.nav.syfo.aktivitetskrav.cronjob.JournalforAktivitetskravVarselCronjob
import no.nav.syfo.aktivitetskrav.database.AktivitetskravVarselRepository
import no.nav.syfo.application.*
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.leaderelection.LeaderPodClient
import no.nav.syfo.client.pdl.PdlClient

fun launchCronjobModule(
    applicationState: ApplicationState,
    environment: Environment,
    database: DatabaseInterface,
    aktivitetskravService: AktivitetskravService,
    aktivitetskravVarselRepository: AktivitetskravVarselRepository,
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
    if (environment.journalforAktivitetskravVarselEnabled) {
        val dokarkivClient = DokarkivClient(
            azureAdClient = azureAdClient,
            dokarkivEnvironment = environment.clients.dokarkiv,
        )
        val journalforAktivitetskravVarselCronjob = JournalforAktivitetskravVarselCronjob(
            aktivitetskravVarselRepository = aktivitetskravVarselRepository,
            dokarkivClient = dokarkivClient,
            pdlClient = pdlClient
        )
        cronjobs.add(journalforAktivitetskravVarselCronjob)
    }

    cronjobs.forEach {
        launchBackgroundTask(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(cronjob = it)
        }
    }
}
