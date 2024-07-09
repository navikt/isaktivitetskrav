package no.nav.syfo.infrastructure.cronjob

data class CronjobResult(
    var updated: Int = 0,
    var failed: Int = 0
)
