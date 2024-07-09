package no.nav.syfo.infrastructure.cronjob

interface Cronjob {
    suspend fun run()
    val initialDelayMinutes: Long
    val intervalDelayMinutes: Long
}
