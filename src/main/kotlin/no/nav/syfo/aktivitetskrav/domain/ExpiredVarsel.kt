package no.nav.syfo.aktivitetskrav.kafka

import java.time.LocalDate
import java.util.*

data class ExpiredVarsel(
    val uuid: UUID,
    val svarfrist: LocalDate,
)
