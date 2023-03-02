package no.nav.syfo.application.cronjob

import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.cronjob.AktivitetskravAutomatiskOppfyltCronjob
import no.nav.syfo.aktivitetskrav.cronjob.AktivitetskravNyCronjob
import no.nav.syfo.application.*
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.leaderelection.LeaderPodClient

fun launchCronjobModule(
    applicationState: ApplicationState,
    environment: Environment,
    database: DatabaseInterface,
    aktivitetskravService: AktivitetskravService,
) {
    val leaderPodClient = LeaderPodClient(
        electorPath = environment.electorPath
    )
    val cronjobRunner = CronjobRunner(
        applicationState = applicationState,
        leaderPodClient = leaderPodClient
    )

    if (environment.automatiskOppfyltCronJobEnabled) {
        val aktivitetskravAutomatiskOppfyltCronjob = AktivitetskravAutomatiskOppfyltCronjob(
            database = database,
            aktivitetskravService = aktivitetskravService,
        )
        launchBackgroundTask(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(cronjob = aktivitetskravAutomatiskOppfyltCronjob)
        }
    }
    if (environment.nyCronjobEnabled) {
        val aktivitetskravNyCronjob = AktivitetskravNyCronjob(
            database = database,
            aktivitetskravService = aktivitetskravService,
        )
        launchBackgroundTask(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(cronjob = aktivitetskravNyCronjob)
        }
    }
}
