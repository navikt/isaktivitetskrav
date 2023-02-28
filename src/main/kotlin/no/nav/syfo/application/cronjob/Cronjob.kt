package no.nav.syfo.application.cronjob

interface Cronjob {
    suspend fun run()
    val initialDelayMinutes: Long
    val intervalDelayMinutes: Long
}
